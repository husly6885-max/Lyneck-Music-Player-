package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.LyneckContainer
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.SettingsManager
import com.example.data.repository.MusicRepository
import com.example.playback.MusicPlayerManager
import com.example.widget.LyneckMusicWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
            .build()
        setMediaNotificationProvider(notificationProvider)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, playerManager.exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_PLAY_PAUSE -> playerManager.togglePlayPause()
            ACTION_WIDGET_NEXT -> playerManager.playNext()
            ACTION_WIDGET_PREV -> playerManager.playPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == SERVICE_INTERFACE) {
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
        serviceScope.cancel()
        super.onDestroy()
    }
}
