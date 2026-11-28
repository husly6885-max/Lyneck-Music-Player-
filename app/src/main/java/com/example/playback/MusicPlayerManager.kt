package com.example.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.data.model.Song
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class SleepTimerState(
    val active: Boolean = false,
    val remainingSeconds: Int = 0,
    val stopAfterCurrentTrack: Boolean = false,
    val stopAfterCurrentPlaylist: Boolean = false,
    val fadeOutEnabled: Boolean = true
)

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val playbackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val stopAfterCurrentTrack: Boolean = false,
    val stopAfterCurrentPlaylist: Boolean = false,
    val activeQueueId: Long = 1L,
    val activeQueueName: String = "Main Queue"
)

@OptIn(UnstableApi::class)
class MusicPlayerManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val onTrackPlayed: (songId: Long, listenDeltaMs: Long) -> Unit = { _, _ -> },
    private val onListenTick: (songId: Long, deltaMs: Long) -> Unit = { _, _ -> },
    private val onQueuePersistRequested: (queueId: Long, songIds: List<Long>, index: Int, posMs: Long) -> Unit = { _, _, _, _ -> },
    private val onCursorPersistRequested: (queueId: Long, index: Int, posMs: Long) -> Unit = { _, _, _ -> },
    private val onPlaybackVisualStateChanged: (PlaybackState) -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var fadeJob: Job? = null
    private var crossfadeJob: Job? = null
    private var crossfadeTriggeredForSongId: Long = -1L

    val audioProcessor = LyneckAudioProcessor()
    val volumeObserver = SystemVolumeObserver(context)
    val effectsManager = AudioEffectsManager(context) { _ ->
        applyAudioSettings(settingsManager.settings.value)
    }

    val exoPlayer: ExoPlayer

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState = _sleepTimerState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorEvents = _errorEvents.asSharedFlow()

    private var originalQueue: List<Song> = emptyList()
    private var listenAccumulatedMs = 0L
    private var lastSavedPosMs = 0L
    private var lastRecordedPlaySongId: Long = -1L
    private var lastRecordedPlayTimeMs: Long = 0L

    private fun recordTrackPlayedOnce(songId: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (songId == lastRecordedPlaySongId && (now - lastRecordedPlayTimeMs) < 1500L) {
            return
        }
        lastRecordedPlaySongId = songId
        lastRecordedPlayTimeMs = now
        onTrackPlayed(songId, 0L)
    }

    init {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(audioProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        setupPlayerListener()
        applyPlaybackSettings(settingsManager.settings.value)
        applyAudioSettings(settingsManager.settings.value)
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effectsManager.attachToAudioSession(audioSessionId, settingsManager.settings.value)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                onPlaybackVisualStateChanged(_playbackState.value)
                if (isPlaying) {
                    startProgressTracker()
                    if (exoPlayer.audioSessionId != 0) {
                        effectsManager.attachToAudioSession(exoPlayer.audioSessionId, settingsManager.settings.value)
                    }
                } else {
                    stopProgressTracker()
                    persistCurrentPlaybackPosition()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        val dur = exoPlayer.duration.coerceAtLeast(0L)
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        _playbackState.value = _playbackState.value.copy(
                            durationMs = if (dur > 0) dur else (_playbackState.value.currentSong?.duration ?: 0L),
                            currentPositionMs = pos
                        )
                    }
                    Player.STATE_ENDED -> {
                        if (_sleepTimerState.value.stopAfterCurrentTrack || _playbackState.value.stopAfterCurrentTrack) {
                            _sleepTimerState.value = _sleepTimerState.value.copy(stopAfterCurrentTrack = false)
                            settingsManager.update { it.copy(stopAfterCurrentTrack = false) }
                            _playbackState.value = _playbackState.value.copy(stopAfterCurrentTrack = false, isPlaying = false)
                            exoPlayer.pause()
                        } else if (_sleepTimerState.value.stopAfterCurrentPlaylist || _playbackState.value.stopAfterCurrentPlaylist) {
                            _sleepTimerState.value = _sleepTimerState.value.copy(stopAfterCurrentPlaylist = false)
                            _playbackState.value = _playbackState.value.copy(stopAfterCurrentPlaylist = false, isPlaying = false)
                            exoPlayer.pause()
                        } else {
                            handleSongEnded()
                        }
                    }
                    else -> Unit
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previousIndex = _playbackState.value.currentIndex
                val currentMediaId = mediaItem?.mediaId?.toLongOrNull()
                val queue = _playbackState.value.queue
                val index = queue.indexOfFirst { it.id == currentMediaId }
                val currentSong = if (index >= 0) queue[index] else queue.find { it.id == currentMediaId }

                val wrappedPlaylist = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    queue.isNotEmpty() &&
                    previousIndex == queue.lastIndex &&
                    index == 0

                if (currentSong != null) {
                    audioProcessor.trackReplayGainDb = currentSong.replayGainDb
                    if (exoPlayer.playWhenReady) {
                        recordTrackPlayedOnce(currentSong.id)
                    }
                    crossfadeTriggeredForSongId = -1L

                    if (_playbackState.value.stopAfterCurrentTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        exoPlayer.pause()
                        settingsManager.update { it.copy(stopAfterCurrentTrack = false) }
                        _playbackState.value = _playbackState.value.copy(stopAfterCurrentTrack = false)
                        _sleepTimerState.value = _sleepTimerState.value.copy(stopAfterCurrentTrack = false)
                    } else if (_playbackState.value.stopAfterCurrentPlaylist && wrappedPlaylist) {
                        exoPlayer.pause()
                        _playbackState.value = _playbackState.value.copy(stopAfterCurrentPlaylist = false)
                        _sleepTimerState.value = _sleepTimerState.value.copy(stopAfterCurrentPlaylist = false)
                    }
                }

                val transitionPos = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    exoPlayer.currentPosition.coerceAtLeast(0L)
                } else 0L

                _playbackState.value = _playbackState.value.copy(
                    currentSong = currentSong ?: _playbackState.value.currentSong,
                    currentIndex = if (index >= 0) index else _playbackState.value.currentIndex,
                    currentPositionMs = transitionPos,
                    durationMs = currentSong?.duration ?: _playbackState.value.durationMs
                )
                onPlaybackVisualStateChanged(_playbackState.value)
                persistCurrentPlaybackPosition()
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelCrossfadeAndRestoreVolume()
                val title = _playbackState.value.currentSong?.title ?: "Audio track"
                _errorEvents.tryEmit("Unable to play \"$title\": ${error.errorCodeName}")
                exoPlayer.pause()
            }
        })
    }

    fun applyPlaybackSettings(settings: AppSettings) {
        exoPlayer.setPlaybackParameters(PlaybackParameters(settings.playbackSpeed.coerceIn(0.5f, 2.0f)))
        exoPlayer.skipSilenceEnabled = settings.skipSilence
        exoPlayer.pauseAtEndOfMediaItems = settings.stopAfterCurrentTrack
        if (settings.crossfadeSeconds <= 0) {
            cancelCrossfadeAndRestoreVolume()
        }
        _playbackState.value = _playbackState.value.copy(
            playbackSpeed = settings.playbackSpeed,
            skipSilence = settings.skipSilence,
            stopAfterCurrentTrack = settings.stopAfterCurrentTrack
        )
    }

    fun applyAudioSettings(settings: AppSettings) {
        audioProcessor.balance = settings.audioBalance
        audioProcessor.normalizeVolume = settings.volumeNormalization
        audioProcessor.replayGainEnabled = settings.replayGainEnabled
        effectsManager.applySettings(settings)
    }

    fun setVisualizerActive(active: Boolean) {
        audioProcessor.visualizerActive = active
        if (!active) audioProcessor.resetVisualizer()
    }

    fun playSongList(songs: List<Song>, startIndex: Int = 0, seekPositionMs: Long = 0L, autoPlay: Boolean = true) {
        if (songs.isEmpty()) return
        cancelCrossfadeAndRestoreVolume()

        originalQueue = songs.toList()
        val queueToPlay = if (_playbackState.value.isShuffle) {
            val startSong = songs.getOrNull(startIndex) ?: songs.first()
            val shuffled = songs.filter { it.id != startSong.id }.shuffled()
            listOf(startSong) + shuffled
        } else {
            songs.toList()
        }

        val actualStartIndex = if (_playbackState.value.isShuffle) 0 else startIndex.coerceIn(0, queueToPlay.size - 1)
        val targetSong = queueToPlay[actualStartIndex]
        audioProcessor.trackReplayGainDb = targetSong.replayGainDb
        crossfadeTriggeredForSongId = -1L

        _playbackState.value = _playbackState.value.copy(
            queue = queueToPlay,
            currentIndex = actualStartIndex,
            currentSong = targetSong,
            currentPositionMs = seekPositionMs,
            durationMs = targetSong.duration
        )

        val mediaItems = queueToPlay.map { songToMediaItem(it) }
        exoPlayer.playWhenReady = autoPlay
        exoPlayer.setMediaItems(mediaItems, actualStartIndex, seekPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        if (autoPlay) {
            if (settingsManager.settings.value.fadeInOut) {
                fadeInAndPlay()
            } else {
                exoPlayer.volume = 1f
                exoPlayer.play()
            }
            recordTrackPlayedOnce(targetSong.id)
        }
        onPlaybackVisualStateChanged(_playbackState.value)
        persistQueueStructure()
    }

    fun restoreQueueWithoutAutoPlay(
        queueId: Long,
        queueName: String,
        songs: List<Song>,
        startIndex: Int,
        positionMs: Long
    ) {
        if (songs.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, songs.lastIndex)
        _playbackState.value = _playbackState.value.copy(
            activeQueueId = queueId,
            activeQueueName = queueName
        )
        playSongList(songs, safeIdx, positionMs, autoPlay = false)
    }

    fun setActiveQueueInfo(queueId: Long, queueName: String) {
        _playbackState.value = _playbackState.value.copy(
            activeQueueId = queueId,
            activeQueueName = queueName
        )
    }

    fun playNextInQueue(song: Song) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            playSongList(listOf(song), 0)
            return
        }
        val curIndex = _playbackState.value.currentIndex.coerceIn(0, currentQueue.lastIndex)
        val existingIdx = currentQueue.indexOfFirst { it.id == song.id }
        val insertIdx = if (existingIdx >= 0 && existingIdx <= curIndex) {
            curIndex
        } else {
            curIndex + 1
        }
        if (existingIdx >= 0) {
            currentQueue.removeAt(existingIdx)
            exoPlayer.removeMediaItem(existingIdx)
        }
        val safeInsert = insertIdx.coerceIn(0, currentQueue.size)
        currentQueue.add(safeInsert, song)
        exoPlayer.addMediaItem(safeInsert, songToMediaItem(song))
        originalQueue = currentQueue.toList()
        val newCurIdx = currentQueue.indexOfFirst { it.id == _playbackState.value.currentSong?.id }.coerceAtLeast(0)
        _playbackState.value = _playbackState.value.copy(queue = currentQueue, currentIndex = newCurIdx)
        persistQueueStructure()
    }

    fun addToQueue(songsToAdd: List<Song>) {
        if (songsToAdd.isEmpty()) return
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            playSongList(songsToAdd, 0)
            return
        }
        val existingIds = currentQueue.map { it.id }.toSet()
        val filtered = songsToAdd.filter { it.id !in existingIds }
        if (filtered.isEmpty()) return
        currentQueue.addAll(filtered)
        exoPlayer.addMediaItems(filtered.map { songToMediaItem(it) })
        originalQueue = currentQueue.toList()
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
        persistQueueStructure()
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        val wasCurrent = index == _playbackState.value.currentIndex
        currentQueue.removeAt(index)
        exoPlayer.removeMediaItem(index)
        originalQueue = currentQueue.toList()

        if (currentQueue.isEmpty()) {
            clearQueue()
            return
        }
        val newIndex = when {
            index < _playbackState.value.currentIndex -> _playbackState.value.currentIndex - 1
            wasCurrent -> index.coerceIn(0, currentQueue.lastIndex)
            else -> _playbackState.value.currentIndex
        }
        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentIndex = newIndex,
            currentSong = currentQueue.getOrNull(newIndex)
        )
        onPlaybackVisualStateChanged(_playbackState.value)
        persistQueueStructure()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        exoPlayer.moveMediaItem(fromIndex, toIndex)
        originalQueue = currentQueue.toList()
        val newCurrentIndex = currentQueue.indexOfFirst { it.id == _playbackState.value.currentSong?.id }
            .coerceAtLeast(0)
        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentIndex = newCurrentIndex
        )
        persistQueueStructure()
    }

    fun clearQueue() {
        cancelCrossfadeAndRestoreVolume()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        originalQueue = emptyList()
        _playbackState.value = _playbackState.value.copy(
            queue = emptyList(),
            currentIndex = -1,
            currentSong = null,
            isPlaying = false,
            currentPositionMs = 0L,
            durationMs = 0L,
            stopAfterCurrentPlaylist = false
        )
        _sleepTimerState.value = _sleepTimerState.value.copy(stopAfterCurrentPlaylist = false)
        onPlaybackVisualStateChanged(_playbackState.value)
        persistQueueStructure()
    }

    fun removeDeletedSongsFromQueue(deletedIds: Set<Long>) {
        val currentQueue = _playbackState.value.queue
        if (currentQueue.none { it.id in deletedIds }) return
        val currentId = _playbackState.value.currentSong?.id
        val newQueue = currentQueue.filterNot { it.id in deletedIds }
        if (newQueue.isEmpty()) {
            clearQueue()
            return
        }
        val wasPlaying = exoPlayer.isPlaying
        if (currentId in deletedIds) {
            playSongList(newQueue, 0, 0L, autoPlay = wasPlaying)
        } else {
            val newIdx = newQueue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            playSongList(newQueue, newIdx, pos, autoPlay = wasPlaying)
        }
    }

    fun playAtIndex(index: Int) {
        val queue = _playbackState.value.queue
        if (index in queue.indices) {
            cancelCrossfadeAndRestoreVolume()
            val song = queue[index]
            audioProcessor.trackReplayGainDb = song.replayGainDb
            crossfadeTriggeredForSongId = -1L
            _playbackState.value = _playbackState.value.copy(
                currentIndex = index,
                currentSong = song,
                durationMs = song.duration,
                currentPositionMs = 0L
            )
            if (exoPlayer.mediaItemCount > index) {
                exoPlayer.seekTo(index, 0L)
                exoPlayer.volume = 1f
                exoPlayer.play()
            } else {
                val mediaItems = queue.map { songToMediaItem(it) }
                exoPlayer.setMediaItems(mediaItems, index, 0L)
                exoPlayer.prepare()
                exoPlayer.volume = 1f
                exoPlayer.play()
            }
            recordTrackPlayedOnce(song.id)
            onPlaybackVisualStateChanged(_playbackState.value)
            persistCurrentPlaybackPosition()
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pauseFromSession()
        } else {
            playFromSession()
        }
    }

    fun playFromSession() {
        if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.mediaItemCount == 0) {
            val queue = _playbackState.value.queue
            if (queue.isNotEmpty()) {
                val idx = _playbackState.value.currentIndex.coerceIn(0, queue.lastIndex)
                playSongList(queue, idx, _playbackState.value.currentPositionMs, autoPlay = true)
            }
            return
        }
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
        }
        if (settingsManager.settings.value.fadeInOut) {
            fadeInAndPlay()
        } else {
            exoPlayer.volume = 1f
            exoPlayer.play()
        }
        _playbackState.value.currentSong?.let { recordTrackPlayedOnce(it.id) }
    }

    fun pauseFromSession() {
        cancelCrossfadeAndRestoreVolume()
        fadeJob?.cancel()
        if (settingsManager.settings.value.fadeInOut && exoPlayer.isPlaying) {
            fadeOutAndPause()
        } else {
            exoPlayer.pause()
            exoPlayer.volume = 1f
        }
        persistCurrentPlaybackPosition()
    }

    private fun fadeInAndPlay() {
        fadeJob?.cancel()
        exoPlayer.volume = 0.05f
        exoPlayer.play()
        fadeJob = scope.launch {
            for (step in 1..10) {
                delay(28)
                exoPlayer.volume = step / 10f
            }
            exoPlayer.volume = 1f
        }
    }

    private fun fadeOutAndPause() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            for (step in 9 downTo 1) {
                delay(25)
                exoPlayer.volume = step / 10f
            }
            exoPlayer.pause()
            exoPlayer.volume = 1f
        }
    }

    fun playNext() {
        cancelCrossfadeAndRestoreVolume()
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return
        val currentIndex = _playbackState.value.currentIndex
        val nextIndex = currentIndex + 1

        if (nextIndex < queue.size) {
            playAtIndex(nextIndex)
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
            playAtIndex(0)
        }
    }

    fun playPrevious() {
        cancelCrossfadeAndRestoreVolume()
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return

        if (exoPlayer.currentPosition > 3000) {
            seekTo(0L)
            return
        }

        val currentIndex = _playbackState.value.currentIndex
        val prevIndex = currentIndex - 1

        if (prevIndex >= 0) {
            playAtIndex(prevIndex)
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
            playAtIndex(queue.size - 1)
        } else {
            seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        cancelCrossfadeAndRestoreVolume()
        crossfadeTriggeredForSongId = -1L
        val safePos = positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(safePos)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = safePos)
        persistCurrentPlaybackPosition()
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        val currentSong = _playbackState.value.currentSong

        if (newShuffle) {
            val base = originalQueue.ifEmpty { _playbackState.value.queue }
            val remaining = base.filter { it.id != currentSong?.id }.shuffled()
            val newQueue = if (currentSong != null) listOf(currentSong) + remaining else remaining
            val newIndex = if (currentSong != null) 0 else -1
            _playbackState.value = _playbackState.value.copy(
                isShuffle = true,
                queue = newQueue,
                currentIndex = newIndex
            )
            updatePlayerPlaylist(newQueue, newIndex)
        } else {
            val restoredQueue = originalQueue.ifEmpty { _playbackState.value.queue }
            val restoredIndex = restoredQueue.indexOfFirst { it.id == currentSong?.id }.coerceAtLeast(0)
            _playbackState.value = _playbackState.value.copy(
                isShuffle = false,
                queue = restoredQueue,
                currentIndex = restoredIndex
            )
            updatePlayerPlaylist(restoredQueue, restoredIndex)
        }
        persistQueueStructure()
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = nextMode)
        exoPlayer.repeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun toggleStopAfterCurrentTrack() {
        val next = !_playbackState.value.stopAfterCurrentTrack
        settingsManager.update { it.copy(stopAfterCurrentTrack = next) }
        exoPlayer.pauseAtEndOfMediaItems = next
        _playbackState.value = _playbackState.value.copy(stopAfterCurrentTrack = next)
        _sleepTimerState.value = _sleepTimerState.value.copy(stopAfterCurrentTrack = next)
    }

    fun toggleStopAfterCurrentPlaylist() {
        val next = !_playbackState.value.stopAfterCurrentPlaylist
        _playbackState.value = _playbackState.value.copy(stopAfterCurrentPlaylist = next)
        _sleepTimerState.value = _sleepTimerState.value.copy(stopAfterCurrentPlaylist = next)
    }

    fun startSleepTimer(minutes: Int, fadeOut: Boolean) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val totalSec = minutes * 60
        _sleepTimerState.value = SleepTimerState(
            active = true,
            remainingSeconds = totalSec,
            stopAfterCurrentTrack = false,
            fadeOutEnabled = fadeOut
        )
        sleepTimerJob = scope.launch {
            var remaining = totalSec
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _sleepTimerState.value = _sleepTimerState.value.copy(remainingSeconds = remaining)
                if (fadeOut && remaining in 1..15 && exoPlayer.isPlaying) {
                    exoPlayer.volume = (remaining / 15f).coerceIn(0.05f, 1f)
                }
            }
            if (isActive) {
                exoPlayer.pause()
                exoPlayer.volume = 1f
                _sleepTimerState.value = SleepTimerState(active = false, remainingSeconds = 0, fadeOutEnabled = fadeOut)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        exoPlayer.volume = 1f
        _sleepTimerState.value = _sleepTimerState.value.copy(active = false, remainingSeconds = 0)
    }

    private fun updatePlayerPlaylist(newQueue: List<Song>, startIndex: Int) {
        if (newQueue.isEmpty()) return
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val isCurrentlyPlaying = exoPlayer.isPlaying
        val mediaItems = newQueue.map { songToMediaItem(it) }

        exoPlayer.setMediaItems(mediaItems, startIndex.coerceAtLeast(0), currentPos)
        if (isCurrentlyPlaying) {
            exoPlayer.play()
        }
    }

    private fun handleSongEnded() {
        when (_playbackState.value.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                exoPlayer.play()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                val curIdx = _playbackState.value.currentIndex
                if (curIdx + 1 < _playbackState.value.queue.size) {
                    playNext()
                } else {
                    exoPlayer.pause()
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    val effectiveDur = if (dur > 0) dur else _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = effectiveDur
                    )

                    listenAccumulatedMs += 300L
                    if (listenAccumulatedMs >= 5000L) {
                        _playbackState.value.currentSong?.id?.let { sid ->
                            onListenTick(sid, listenAccumulatedMs)
                        }
                        listenAccumulatedMs = 0L
                        persistCurrentPlaybackPosition()
                    }

                    // Crossfade handling when enabled
                    val crossfadeSec = settingsManager.settings.value.crossfadeSeconds
                    val curSong = _playbackState.value.currentSong
                    val isStoppingAfterThisTrack = _playbackState.value.stopAfterCurrentTrack ||
                        (_playbackState.value.stopAfterCurrentPlaylist &&
                            _playbackState.value.currentIndex == _playbackState.value.queue.lastIndex)
                    if (crossfadeSec > 0 && !isStoppingAfterThisTrack &&
                        effectiveDur > (crossfadeSec * 1000L + 1000L) && curSong != null
                    ) {
                        val remainingMs = effectiveDur - pos
                        val fadeWindowMs = crossfadeSec * 1000L
                        if (remainingMs in 50L..fadeWindowMs && crossfadeTriggeredForSongId != curSong.id) {
                            val hasNext = _playbackState.value.currentIndex + 1 < _playbackState.value.queue.size ||
                                _playbackState.value.repeatMode == RepeatMode.ALL
                            if (hasNext) {
                                crossfadeTriggeredForSongId = curSong.id
                                triggerCrossfadeTransition(fadeWindowMs)
                            }
                        }
                    }
                }
                delay(300L)
            }
        }
    }

    private fun cancelCrossfadeAndRestoreVolume() {
        val activeJob = crossfadeJob
        crossfadeJob = null
        if (activeJob != null && activeJob.isActive) {
            activeJob.cancel()
            exoPlayer.volume = 1f
        }
    }

    private fun triggerCrossfadeTransition(fadeWindowMs: Long) {
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            try {
                val steps = 10
                val stepMs = (fadeWindowMs / 2 / steps).coerceAtLeast(40L)
                for (i in steps downTo 2) {
                    if (!isActive || !exoPlayer.isPlaying) break
                    exoPlayer.volume = i.toFloat() / steps
                    delay(stepMs)
                }
                if (isActive && exoPlayer.isPlaying) {
                    val queue = _playbackState.value.queue
                    val nextIndex = _playbackState.value.currentIndex + 1
                    val targetIdx = when {
                        nextIndex < queue.size -> nextIndex
                        _playbackState.value.repeatMode == RepeatMode.ALL -> 0
                        else -> -1
                    }
                    if (targetIdx >= 0) {
                        val song = queue[targetIdx]
                        audioProcessor.trackReplayGainDb = song.replayGainDb
                        crossfadeTriggeredForSongId = -1L
                        _playbackState.value = _playbackState.value.copy(
                            currentIndex = targetIdx,
                            currentSong = song,
                            durationMs = song.duration,
                            currentPositionMs = 0L
                        )
                        if (exoPlayer.mediaItemCount > targetIdx) {
                            exoPlayer.seekTo(targetIdx, 0L)
                            exoPlayer.play()
                        } else {
                            exoPlayer.setMediaItems(queue.map { songToMediaItem(it) }, targetIdx, 0L)
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                        recordTrackPlayedOnce(song.id)
                        onPlaybackVisualStateChanged(_playbackState.value)
                    }
                    for (i in 2..steps) {
                        if (!isActive || !exoPlayer.isPlaying) break
                        exoPlayer.volume = i.toFloat() / steps
                        delay(stepMs)
                    }
                }
            } finally {
                exoPlayer.volume = 1f
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun persistQueueStructure() {
        val state = _playbackState.value
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (state.queue.any { it.id < 0L }) return
        onQueuePersistRequested(
            state.activeQueueId,
            state.queue.map { it.id },
            state.currentIndex.coerceAtLeast(0),
            pos
        )
        lastSavedPosMs = pos
    }

    fun persistCurrentPlaybackPosition() {
        val state = _playbackState.value
        val curSong = state.currentSong ?: return
        if (curSong.id < 0L) return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (settingsManager.settings.value.rememberPlaybackPosition) {
            settingsManager.saveLastPlayback(curSong.id, pos)
        }
        onCursorPersistRequested(
            state.activeQueueId,
            state.currentIndex.coerceAtLeast(0),
            pos
        )
        lastSavedPosMs = pos
    }

    fun buildMediaMetadata(song: Song): MediaMetadata {
        val safeTitle = song.title.ifBlank { "Unknown Title" }
        val safeArtist = song.artist.ifBlank { "Unknown Artist" }
        val safeAlbum = song.album.ifBlank { "Unknown Album" }
        val artUri = if (settingsManager.settings.value.showArtworkInNotification) {
            song.albumArtUri
        } else {
            null
        }
        return MediaMetadata.Builder()
            .setTitle(safeTitle)
            .setDisplayTitle(safeTitle)
            .setArtist(safeArtist)
            .setSubtitle(safeArtist)
            .setAlbumTitle(safeAlbum)
            .setArtworkUri(artUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
    }

    fun buildMediaItem(song: Song): MediaItem = songToMediaItem(song)

    fun refreshSessionMediaItems() {
        val queue = _playbackState.value.queue
        if (queue.isEmpty() || exoPlayer.mediaItemCount != queue.size) return
        val idx = exoPlayer.currentMediaItemIndex.coerceIn(0, queue.lastIndex)
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exoPlayer.playWhenReady
        exoPlayer.setMediaItems(queue.map { songToMediaItem(it) }, idx, pos)
        exoPlayer.playWhenReady = wasPlaying
    }

    private fun songToMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .setMediaMetadata(buildMediaMetadata(song))
            .build()
    }

    fun release() {
        persistCurrentPlaybackPosition()
        stopProgressTracker()
        sleepTimerJob?.cancel()
        fadeJob?.cancel()
        crossfadeJob?.cancel()
        effectsManager.destroy()
        volumeObserver.destroy()
        exoPlayer.release()
        scope.cancel()
    }
}
