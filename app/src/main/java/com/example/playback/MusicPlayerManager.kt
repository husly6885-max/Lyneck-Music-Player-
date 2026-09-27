package com.example.playback

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val volume: Float = 1.0f // 0.0 to 1.0
)

class MusicPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    private var originalQueue: List<Song> = emptyList()

    init {
        setupPlayerListener()
        updateVolumeState()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        _playbackState.value = _playbackState.value.copy(
                            durationMs = exoPlayer.duration.coerceAtLeast(0L),
                            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                        )
                    }
                    Player.STATE_ENDED -> {
                        handleSongEnded()
                    }
                    else -> Unit
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentMediaId = mediaItem?.mediaId?.toLongOrNull()
                val currentSong = _playbackState.value.queue.find { it.id == currentMediaId }
                    ?: _playbackState.value.currentSong

                val index = _playbackState.value.queue.indexOfFirst { it.id == currentMediaId }

                _playbackState.value = _playbackState.value.copy(
                    currentSong = currentSong,
                    currentIndex = if (index >= 0) index else _playbackState.value.currentIndex,
                    currentPositionMs = 0L,
                    durationMs = currentSong?.duration ?: 0L
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = error.message ?: "Failed to play audio track."
                scope.launch {
                    _errorEvents.emit(msg)
                }
                // Automatically try next song if possible
                playNext()
            }
        })
    }

    fun playSongList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return

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

        _playbackState.value = _playbackState.value.copy(
            queue = queueToPlay,
            currentIndex = actualStartIndex,
            currentSong = targetSong,
            durationMs = targetSong.duration
        )

        val mediaItems = queueToPlay.map { songToMediaItem(it) }
        exoPlayer.setMediaItems(mediaItems, actualStartIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun playSingleSong(song: Song) {
        val existingIndex = _playbackState.value.queue.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            playAtIndex(existingIndex)
        } else {
            val newQueue = listOf(song) + _playbackState.value.queue
            playSongList(newQueue, 0)
        }
    }

    fun playAtIndex(index: Int) {
        val queue = _playbackState.value.queue
        if (index in queue.indices) {
            val song = queue[index]
            _playbackState.value = _playbackState.value.copy(
                currentIndex = index,
                currentSong = song,
                durationMs = song.duration
            )
            if (exoPlayer.mediaItemCount > index) {
                exoPlayer.seekTo(index, 0L)
                exoPlayer.play()
            } else {
                val mediaItems = queue.map { songToMediaItem(it) }
                exoPlayer.setMediaItems(mediaItems, index, 0L)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.mediaItemCount == 0) {
                val song = _playbackState.value.currentSong
                if (song != null) {
                    playSingleSong(song)
                }
            } else {
                exoPlayer.play()
            }
        }
    }

    fun playNext() {
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
        val queue = _playbackState.value.queue
        if (queue.isEmpty()) return

        // If played more than 3 seconds, restart current track
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
        val safePos = positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(safePos)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = safePos)
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        val currentSong = _playbackState.value.currentSong

        if (newShuffle) {
            val remaining = originalQueue.filter { it.id != currentSong?.id }.shuffled()
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

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clamped
        _playbackState.value = _playbackState.value.copy(volume = clamped)
    }

    private fun updateVolumeState() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val initialRatio = if (maxVolume > 0) currentVolume / maxVolume else 1.0f
        _playbackState.value = _playbackState.value.copy(volume = initialRatio)
    }

    private fun updatePlayerPlaylist(newQueue: List<Song>, startIndex: Int) {
        val currentPos = exoPlayer.currentPosition
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
            RepeatMode.ALL, RepeatMode.OFF -> {
                playNext()
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
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = if (dur > 0) dur else _playbackState.value.durationMs
                    )
                }
                delay(400)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.release()
    }
}
