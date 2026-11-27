package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.LruCache
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.LyneckContainer
import com.example.MainActivity
import com.example.R
import com.example.data.model.Song
import com.example.data.preferences.SettingsManager
import com.example.data.repository.MusicRepository
import com.example.playback.MusicPlayerManager
import com.example.widget.LyneckMusicWidgetProvider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "lyneck_playback_channel"
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.action.WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.action.WIDGET_PREV"

        @Volatile
        var instance: MusicService? = null
            private set
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bitmapExecutor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    lateinit var settingsManager: SettingsManager
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var playerManager: MusicPlayerManager
        private set

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        val container = LyneckContainer.get(applicationContext)
        settingsManager = container.settingsManager
        repository = container.repository

        playerManager = MusicPlayerManager(
            context = applicationContext,
            settingsManager = settingsManager,
            onTrackPlayed = { songId, delta ->
                serviceScope.launch {
                    repository.recordSongPlayed(songId, delta)
                }
            },
            onListenTick = { songId, delta ->
                serviceScope.launch {
                    repository.addListeningDuration(songId, delta)
                }
            },
            onQueuePersistRequested = { queueId, songIds, idx, pos ->
                serviceScope.launch {
                    repository.saveQueueState(queueId, songIds, idx, pos)
                }
            },
            onPlaybackVisualStateChanged = { state ->
                LyneckMusicWidgetProvider.pushUpdate(
                    context = applicationContext,
                    title = state.currentSong?.title,
                    artist = state.currentSong?.artist,
                    artUri = if (settingsManager.settings.value.showArtworkInNotification) {
                        state.currentSong?.albumArtUri
                    } else null,
                    isPlaying = state.isPlaying
                )
            }
        )

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build().apply {
                setSmallIcon(R.drawable.ic_widget_music_note)
            }
        setMediaNotificationProvider(notificationProvider)

        val openPlayerIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openPlayerIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionPlayer = LyneckSessionPlayer(playerManager)
        val bitmapLoader = LyneckNotificationBitmapLoader(
            context = applicationContext,
            settingsManager = settingsManager,
            executor = bitmapExecutor,
            currentSongProvider = { playerManager.playbackState.value.currentSong }
        )

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setBitmapLoader(bitmapLoader)
            .setCallback(LyneckMediaSessionCallback())
            .build()

        serviceScope.launch {
            settingsManager.settings
                .map { it.showArtworkInNotification }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    withContext(Dispatchers.Main) {
                        playerManager.refreshSessionMediaItems()
                        mediaSession?.let { session ->
                            onUpdateNotification(session, playerManager.exoPlayer.playWhenReady)
                        }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_PLAY_PAUSE -> handleWidgetOrExternalTransport { playerManager.togglePlayPause() }
            ACTION_WIDGET_NEXT -> handleWidgetOrExternalTransport { playerManager.playNext() }
            ACTION_WIDGET_PREV -> handleWidgetOrExternalTransport { playerManager.playPrevious() }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleWidgetOrExternalTransport(action: () -> Unit) {
        if (playerManager.playbackState.value.queue.isNotEmpty()) {
            action()
            return
        }
        serviceScope.launch {
            val restored = restoreSavedQueueIfNeeded()
            if (restored) {
                withContext(Dispatchers.Main) {
                    action()
                }
            }
        }
    }

    private suspend fun restoreSavedQueueIfNeeded(): Boolean {
        if (playerManager.playbackState.value.queue.isNotEmpty()) return true
        val scanned = repository.allSongs.first().ifEmpty { repository.scanSongs() }
        if (scanned.isEmpty()) return false

        val activeQ = repository.ensureDefaultQueueExists()
        val savedIds = repository.getQueueSongIds(activeQ.id)
        val songMap = scanned.associateBy { it.id }
        val restoredList = savedIds.mapNotNull { songMap[it] }

        return if (restoredList.isNotEmpty()) {
            val idx = activeQ.currentIndex.coerceIn(0, restoredList.lastIndex)
            val pos = if (settingsManager.settings.value.rememberPlaybackPosition) activeQ.positionMs else 0L
            withContext(Dispatchers.Main) {
                playerManager.restoreQueueWithoutAutoPlay(activeQ.id, activeQ.name, restoredList, idx, pos)
            }
            true
        } else {
            val lastSong = songMap[settingsManager.settings.value.lastSongId] ?: scanned.firstOrNull()
            if (lastSong != null) {
                val pos = if (settingsManager.settings.value.rememberPlaybackPosition) {
                    settingsManager.settings.value.lastPositionMs
                } else 0L
                withContext(Dispatchers.Main) {
                    playerManager.restoreQueueWithoutAutoPlay(activeQ.id, activeQ.name, listOf(lastSong), 0, pos)
                }
                true
            } else {
                false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        val action = intent?.action
        if (action == SERVICE_INTERFACE || action == "android.media.browse.MediaBrowserService") {
            return super.onBind(intent)
        }
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        playerManager.persistCurrentPlaybackPosition()
        val keepPlaying = settingsManager.settings.value.keepPlayingOnTaskRemove
        val player = mediaSession?.player
        if (!keepPlaying || player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        instance = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        playerManager.release()
        bitmapExecutor.shutdownNow()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class LyneckSessionPlayer(
        private val manager: MusicPlayerManager
    ) : ForwardingPlayer(manager.exoPlayer) {

        override fun play() {
            manager.playFromSession()
        }

        override fun pause() {
            manager.pauseFromSession()
        }

        override fun seekToNext() {
            manager.playNext()
        }

        override fun seekToNextMediaItem() {
            manager.playNext()
        }

        override fun seekToPrevious() {
            manager.playPrevious()
        }

        override fun seekToPreviousMediaItem() {
            manager.playPrevious()
        }

        override fun seekTo(positionMs: Long) {
            manager.seekTo(positionMs)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            val queue = manager.playbackState.value.queue
            if (mediaItemIndex in queue.indices && mediaItemIndex != currentMediaItemIndex) {
                manager.playAtIndex(mediaItemIndex)
                if (positionMs > 0L) {
                    manager.seekTo(positionMs)
                }
            } else {
                manager.seekTo(positionMs)
            }
        }

        override fun getMediaMetadata(): MediaMetadata {
            val currentSong = manager.playbackState.value.currentSong
            return if (currentSong != null) {
                manager.buildMediaMetadata(currentSong)
            } else {
                super.getMediaMetadata()
            }
        }

        override fun getCurrentMediaItem(): MediaItem? {
            return super.getCurrentMediaItem()
                ?: manager.playbackState.value.currentSong?.let { manager.buildMediaItem(it) }
        }

        override fun getAvailableCommands(): Player.Commands {
            val base = super.getAvailableCommands()
            if (manager.playbackState.value.queue.isEmpty()) return base
            return base.buildUpon()
                .addAll(
                    COMMAND_PLAY_PAUSE,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    COMMAND_GET_CURRENT_MEDIA_ITEM,
                    COMMAND_GET_METADATA,
                    COMMAND_GET_TIMELINE,
                    COMMAND_STOP
                )
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            if (manager.playbackState.value.queue.isNotEmpty() && command in SESSION_TRANSPORT_COMMANDS) {
                return true
            }
            return super.isCommandAvailable(command)
        }
    }

    private inner class LyneckMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_METADATA
                )
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return bitmapExecutor.submit<MediaSession.MediaItemsWithStartPosition> {
                runBlocking {
                    val scanned = repository.allSongs.first().ifEmpty { repository.scanSongs() }
                    if (scanned.isEmpty()) {
                        throw IllegalStateException("No local audio tracks available for resumption")
                    }
                    val activeQ = repository.ensureDefaultQueueExists()
                    val savedIds = repository.getQueueSongIds(activeQ.id)
                    val songMap = scanned.associateBy { it.id }
                    val restoredSongs = savedIds.mapNotNull { songMap[it] }.ifEmpty {
                        listOfNotNull(songMap[settingsManager.settings.value.lastSongId] ?: scanned.firstOrNull())
                    }
                    if (restoredSongs.isEmpty()) {
                        throw IllegalStateException("No saved track available for resumption")
                    }
                    val startIndex = activeQ.currentIndex.coerceIn(0, restoredSongs.lastIndex)
                    val startPos = if (settingsManager.settings.value.rememberPlaybackPosition) {
                        activeQ.positionMs.coerceAtLeast(0L)
                    } else 0L
                    val mediaItems = restoredSongs.map { playerManager.buildMediaItem(it) }
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPos)
                }
            }
        }
    }
}

private val SESSION_TRANSPORT_COMMANDS = setOf(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_METADATA,
    Player.COMMAND_GET_TIMELINE,
    Player.COMMAND_STOP
)

@UnstableApi
private class LyneckNotificationBitmapLoader(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val executor: ListeningExecutorService,
    private val currentSongProvider: () -> Song?
) : BitmapLoader {

    private val cache = LruCache<String, Bitmap>(6)

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/", ignoreCase = true)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return executor.submit<Bitmap> {
            decodeScaledByteArray(data, 512)
                ?: throw IllegalArgumentException("Failed to decode notification artwork bytes")
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return executor.submit<Bitmap> {
            if (!settingsManager.settings.value.showArtworkInNotification) {
                throw IllegalStateException("Notification artwork disabled in settings")
            }
            val currentSong = currentSongProvider()
            val cacheKey = "${uri}_${currentSong?.id ?: -1L}"
            cache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@submit cached
            }

            val loaded = loadFromUriScaled(uri, 512)
                ?: currentSong?.let { loadFallbackFromSong(it, 512) }
                ?: throw IllegalStateException("No album artwork found for $uri")

            cache.put(cacheKey, loaded)
            loaded
        }
    }

    private fun loadFromUriScaled(uri: Uri, maxSizePx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSizePx)
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadFallbackFromSong(song: Song, maxSizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(
                    song.contentUri,
                    Size(maxSizePx, maxSizePx),
                    null
                )
            } catch (_: Exception) {
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, song.contentUri)
            val embedded = retriever.embeddedPicture
            if (embedded != null) decodeScaledByteArray(embedded, maxSizePx) else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeScaledByteArray(data: ByteArray, maxSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSizePx)
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSizePx: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxSizePx && h / 2 >= maxSizePx) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }
}
