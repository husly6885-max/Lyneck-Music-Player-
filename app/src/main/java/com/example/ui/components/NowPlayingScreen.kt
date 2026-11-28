package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PlaybackQueueInfo
import com.example.data.model.Song
import com.example.data.preferences.AppSettings
import com.example.data.preferences.DoubleTapGestureAction
import com.example.data.preferences.PlayerScreenStyle
import com.example.data.preferences.VisualizerMode
import com.example.playback.DeviceVolumeState
import com.example.playback.PlaybackState
import com.example.playback.RepeatMode
import com.example.playback.SleepTimerState
import com.example.ui.MusicViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    volumeState: DeviceVolumeState,
    visualizerBars: List<Float>,
    waveformBars: List<Float>,
    sleepTimerState: SleepTimerState,
    settings: AppSettings,
    savedQueues: List<PlaybackQueueInfo>,
    viewModel: MusicViewModel,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenLyrics: (Song) -> Unit,
    onImportLrc: (Song) -> Unit = { onOpenLyrics(it) },
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showCreateQueueDialog by remember { mutableStateOf(false) }
    var showSaveQueuePlaylistDialog by remember { mutableStateOf(false) }
    var isLyricsOverlayVisible by remember { mutableStateOf(true) }
    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(gestureFeedbackText) {
        if (gestureFeedbackText != null) {
            delay(1300L)
            gestureFeedbackText = null
        }
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val currentPosition = if (isDraggingSlider) dragPositionMs.toLong() else playbackState.currentPositionMs
    val duration = playbackState.durationMs.coerceAtLeast(1L)
    val sliderValue = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    val playerPrimaryColor = Color.White
    val playerSecondaryColor = Color(0xFFD6E6F5)
    val accentPrimary = MaterialTheme.colorScheme.primary
    val accentSecondary = MaterialTheme.colorScheme.secondary
    val chipContainerColor = Color(0xFF06101D).copy(alpha = 0.72f)
    val chipBorderColor = Color.White.copy(alpha = 0.22f)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("now_playing_screen"),
        color = Color(0xFF02070F)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Custom Now Playing background image scaled proportionally without distortion
            AsyncImage(
                model = R.drawable.lynec_music_background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.25f),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("now_playing_custom_background")
            )

            // Subtle top & bottom gradient scrim so artwork subject stays clear while controls remain high-contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xFF02060C).copy(alpha = 0.58f),
                            0.18f to Color(0xFF02060C).copy(alpha = 0.14f),
                            0.38f to Color(0xFF02060C).copy(alpha = 0.06f),
                            0.55f to Color(0xFF02060C).copy(alpha = 0.34f),
                            0.74f to Color(0xFF02060C).copy(alpha = 0.78f),
                            1.0f to Color(0xFF02060C).copy(alpha = 0.94f)
                        )
                    )
            )

            CompositionLocalProvider(LocalContentColor provides playerPrimaryColor) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    val isWideLayout = maxWidth >= 620.dp
                    val isCompactHeight = maxHeight < 540.dp

                    if (isWideLayout) {
                        // Tablet / Foldable / Landscape adaptive two-pane layout
                        Column(modifier = Modifier.fillMaxSize()) {
                            NowPlayingTopBar(
                                sleepTimerState = sleepTimerState,
                                stopAfterCurrentTrack = playbackState.stopAfterCurrentTrack,
                                stopAfterCurrentPlaylist = playbackState.stopAfterCurrentPlaylist,
                                primaryColor = playerPrimaryColor,
                                accentPrimary = accentPrimary,
                                onCollapse = onCollapse,
                                onOpenCarMode = onOpenCarMode,
                                onOpenSleepTimer = { showSleepTimerDialog = true },
                                onOpenQueue = { showQueueSheet = true }
                            )

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Open background showcase side with Cinematic Lyrics, Visualizer & Gestures
                                NowPlayingVisualStage(
                                    song = song,
                                    currentPositionMs = currentPosition,
                                    durationMs = duration,
                                    isPlaying = playbackState.isPlaying,
                                    visualizerBars = visualizerBars,
                                    volumeState = volumeState,
                                    settings = settings,
                                    viewModel = viewModel,
                                    isLyricsOverlayVisible = isLyricsOverlayVisible,
                                    gestureFeedbackText = gestureFeedbackText,
                                    accentPrimary = accentPrimary,
                                    accentSecondary = accentSecondary,
                                    onToggleLyricsOverlay = { isLyricsOverlayVisible = !isLyricsOverlayVisible },
                                    onOpenFullLyrics = { onOpenLyrics(song) },
                                    onImportLrc = { onImportLrc(song) },
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onPrevious = {
                                        viewModel.playPrevious()
                                        gestureFeedbackText = "Previous Track"
                                    },
                                    onNext = {
                                        viewModel.playNext()
                                        gestureFeedbackText = "Next Track"
                                    },
                                    onTogglePlayPause = {
                                        viewModel.togglePlayPause()
                                        gestureFeedbackText = if (playbackState.isPlaying) "Paused" else "Playing"
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(song)
                                        gestureFeedbackText = if (song.isFavorite) "Removed Favorite" else "Added to Favorites"
                                    },
                                    onVolumeChange = { ratio ->
                                        viewModel.setDeviceVolumeRatio(ratio)
                                        gestureFeedbackText = "Volume ${(ratio * 100f).roundToInt()}%"
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(bottom = 12.dp)
                                )

                                // Controls panel on right side with dark translucent backdrop
                                Surface(
                                    modifier = Modifier
                                        .widthIn(max = 460.dp)
                                        .weight(1.1f),
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color(0xFF040B14).copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        NowPlayingControlsSection(
                                            song = song,
                                            playbackState = playbackState,
                                            volumeState = volumeState,
                                            waveformBars = waveformBars,
                                            settings = settings,
                                            sliderValue = sliderValue,
                                            currentPosition = currentPosition,
                                            duration = duration,
                                            showSpeedMenu = showSpeedMenu,
                                            primaryColor = playerPrimaryColor,
                                            secondaryColor = playerSecondaryColor,
                                            accentPrimary = accentPrimary,
                                            accentSecondary = accentSecondary,
                                            chipContainerColor = chipContainerColor,
                                            chipBorderColor = chipBorderColor,
                                            onSliderChange = { newVal ->
                                                isDraggingSlider = true
                                                dragPositionMs = newVal * duration
                                            },
                                            onSliderChangeFinished = {
                                                isDraggingSlider = false
                                                viewModel.seekTo(dragPositionMs.toLong())
                                            },
                                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                                            onOpenLyrics = { onOpenLyrics(song) },
                                            onOpenBookmarks = { showBookmarksSheet = true },
                                            onOpenEqualizer = onOpenEqualizer,
                                            onCycleVisualizer = {
                                                val all = VisualizerMode.entries
                                                val next = all[(all.indexOf(settings.visualizerMode) + 1) % all.size]
                                                viewModel.setVisualizerMode(next)
                                            },
                                            onToggleSpeedMenu = { showSpeedMenu = it },
                                            onSelectSpeed = { spd ->
                                                viewModel.setPlaybackSpeed(spd)
                                                showSpeedMenu = false
                                            },
                                            onToggleStopAfter = { viewModel.toggleStopAfterCurrent() },
                                            onToggleShuffle = { viewModel.toggleShuffle() },
                                            onPrevious = { viewModel.playPrevious() },
                                            onTogglePlayPause = { viewModel.togglePlayPause() },
                                            onNext = { viewModel.playNext() },
                                            onToggleRepeat = { viewModel.toggleRepeat() },
                                            onVolumeChange = { viewModel.setDeviceVolumeRatio(it) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard Handheld Portrait (or Multi-Window Compact) Layout
                        Column(
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .align(Alignment.Center)
                                .fillMaxSize()
                                .then(
                                    if (isCompactHeight) Modifier.verticalScroll(rememberScrollState())
                                    else Modifier
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (isCompactHeight) Arrangement.spacedBy(12.dp) else Arrangement.SpaceBetween
                        ) {
                            NowPlayingTopBar(
                                sleepTimerState = sleepTimerState,
                                stopAfterCurrentTrack = playbackState.stopAfterCurrentTrack,
                                stopAfterCurrentPlaylist = playbackState.stopAfterCurrentPlaylist,
                                primaryColor = playerPrimaryColor,
                                accentPrimary = accentPrimary,
                                onCollapse = onCollapse,
                                onOpenCarMode = onOpenCarMode,
                                onOpenSleepTimer = { showSleepTimerDialog = true },
                                onOpenQueue = { showQueueSheet = true }
                            )

                            // Open upper-center stage so custom artwork stays prominent with Cinematic Lyrics, Visualizer & Gestures
                            if (!isCompactHeight) {
                                NowPlayingVisualStage(
                                    song = song,
                                    currentPositionMs = currentPosition,
                                    durationMs = duration,
                                    isPlaying = playbackState.isPlaying,
                                    visualizerBars = visualizerBars,
                                    volumeState = volumeState,
                                    settings = settings,
                                    viewModel = viewModel,
                                    isLyricsOverlayVisible = isLyricsOverlayVisible,
                                    gestureFeedbackText = gestureFeedbackText,
                                    accentPrimary = accentPrimary,
                                    accentSecondary = accentSecondary,
                                    onToggleLyricsOverlay = { isLyricsOverlayVisible = !isLyricsOverlayVisible },
                                    onOpenFullLyrics = { onOpenLyrics(song) },
                                    onImportLrc = { onImportLrc(song) },
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onPrevious = {
                                        viewModel.playPrevious()
                                        gestureFeedbackText = "Previous Track"
                                    },
                                    onNext = {
                                        viewModel.playNext()
                                        gestureFeedbackText = "Next Track"
                                    },
                                    onTogglePlayPause = {
                                        viewModel.togglePlayPause()
                                        gestureFeedbackText = if (playbackState.isPlaying) "Paused" else "Playing"
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(song)
                                        gestureFeedbackText = if (song.isFavorite) "Removed Favorite" else "Added to Favorites"
                                    },
                                    onVolumeChange = { ratio ->
                                        viewModel.setDeviceVolumeRatio(ratio)
                                        gestureFeedbackText = "Volume ${(ratio * 100f).roundToInt()}%"
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NowPlayingControlsSection(
                                    song = song,
                                    playbackState = playbackState,
                                    volumeState = volumeState,
                                    waveformBars = waveformBars,
                                    settings = settings,
                                    sliderValue = sliderValue,
                                    currentPosition = currentPosition,
                                    duration = duration,
                                    showSpeedMenu = showSpeedMenu,
                                    primaryColor = playerPrimaryColor,
                                    secondaryColor = playerSecondaryColor,
                                    accentPrimary = accentPrimary,
                                    accentSecondary = accentSecondary,
                                    chipContainerColor = chipContainerColor,
                                    chipBorderColor = chipBorderColor,
                                    onSliderChange = { newVal ->
                                        isDraggingSlider = true
                                        dragPositionMs = newVal * duration
                                    },
                                    onSliderChangeFinished = {
                                        isDraggingSlider = false
                                        viewModel.seekTo(dragPositionMs.toLong())
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(song) },
                                    onOpenLyrics = { onOpenLyrics(song) },
                                    onOpenBookmarks = { showBookmarksSheet = true },
                                    onOpenEqualizer = onOpenEqualizer,
                                    onCycleVisualizer = {
                                        val all = VisualizerMode.entries
                                        val next = all[(all.indexOf(settings.visualizerMode) + 1) % all.size]
                                        viewModel.setVisualizerMode(next)
                                    },
                                    onToggleSpeedMenu = { showSpeedMenu = it },
                                    onSelectSpeed = { spd ->
                                        viewModel.setPlaybackSpeed(spd)
                                        showSpeedMenu = false
                                    },
                                    onToggleStopAfter = { viewModel.toggleStopAfterCurrent() },
                                    onToggleShuffle = { viewModel.toggleShuffle() },
                                    onPrevious = { viewModel.playPrevious() },
                                    onTogglePlayPause = { viewModel.togglePlayPause() },
                                    onNext = { viewModel.playNext() },
                                    onToggleRepeat = { viewModel.toggleRepeat() },
                                    onVolumeChange = { viewModel.setDeviceVolumeRatio(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Queue Sheet with Multiple Independent Queues, Reordering & Save as Playlist
    if (showQueueSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${playbackState.activeQueueName} (${playbackState.queue.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        IconButton(
                            onClick = { showSaveQueuePlaylistDialog = true },
                            enabled = playbackState.queue.any { it.id >= 0L },
                            modifier = Modifier.testTag("save_queue_as_playlist_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Save Queue as Playlist")
                        }
                        IconButton(onClick = { showCreateQueueDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "New Queue")
                        }
                        IconButton(
                            onClick = { viewModel.clearQueue() },
                            enabled = playbackState.queue.isNotEmpty()
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Queue")
                        }
                    }
                }

                // Multi-queue selector pills
                if (savedQueues.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        savedQueues.forEach { q ->
                            FilterChip(
                                selected = q.id == playbackState.activeQueueId,
                                onClick = { viewModel.switchActiveQueue(q.id) },
                                label = { Text(q.name) },
                                trailingIcon = if (savedQueues.size > 1 && q.id != playbackState.activeQueueId) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Delete Queue",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.deleteSavedQueue(q.id) }
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(playbackState.queue) { index, queueSong ->
                        val isCurrent = index == playbackState.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.playQueueIndex(index) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = queueSong.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = queueSong.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index > 0) {
                                IconButton(
                                    onClick = { viewModel.moveQueueItem(index, index - 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (index < playbackState.queue.lastIndex) {
                                IconButton(
                                    onClick = { viewModel.moveQueueItem(index, index + 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = { viewModel.removeFromQueue(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateQueueDialog) {
        CreateQueueDialog(
            onDismiss = { showCreateQueueDialog = false },
            onConfirm = { name ->
                viewModel.createNewQueue(name)
                showCreateQueueDialog = false
            }
        )
    }

    if (showSaveQueuePlaylistDialog) {
        SaveQueueAsPlaylistDialog(
            defaultName = playbackState.activeQueueName.ifBlank { "Saved Queue" },
            onDismiss = { showSaveQueuePlaylistDialog = false },
            onConfirm = { name ->
                viewModel.saveCurrentQueueAsPlaylist(name)
                showSaveQueuePlaylistDialog = false
            }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            sleepTimerState = sleepTimerState,
            stopAfterCurrentTrack = playbackState.stopAfterCurrentTrack,
            stopAfterCurrentPlaylist = playbackState.stopAfterCurrentPlaylist,
            onToggleStopAfterTrack = { viewModel.toggleStopAfterCurrent() },
            onToggleStopAfterPlaylist = { viewModel.toggleStopAfterCurrentPlaylist() },
            onStartTimer = { min, fade ->
                viewModel.startSleepTimer(min, fade)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showBookmarksSheet) {
        BookmarksSheet(
            song = song,
            currentPositionMs = currentPosition,
            viewModel = viewModel,
            onDismiss = { showBookmarksSheet = false }
        )
    }
}

@Composable
private fun NowPlayingVisualStage(
    song: Song,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    visualizerBars: List<Float>,
    volumeState: DeviceVolumeState,
    settings: AppSettings,
    viewModel: MusicViewModel,
    isLyricsOverlayVisible: Boolean,
    gestureFeedbackText: String?,
    accentPrimary: Color,
    accentSecondary: Color,
    onToggleLyricsOverlay: () -> Unit,
    onOpenFullLyrics: () -> Unit,
    onImportLrc: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val latestOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val latestOnToggleFavorite by rememberUpdatedState(onToggleFavorite)
    val latestVolumeRatio by rememberUpdatedState(volumeState.ratio)
    val latestOnVolumeChange by rememberUpdatedState(onVolumeChange)

    val effectiveVisualizerMode = when {
        settings.visualizerMode != VisualizerMode.OFF -> settings.visualizerMode
        settings.playerStyle == PlayerScreenStyle.VISUALIZER -> VisualizerMode.SPECTRUM_BARS
        else -> VisualizerMode.OFF
    }

    val gestureModifier = Modifier
        .then(
            if (settings.gesturesEnabled && settings.swipeTrackGestureEnabled) {
                Modifier.pointerInput(song.id) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag > 95f) latestOnPrevious()
                            else if (totalDrag < -95f) latestOnNext()
                        }
                    )
                }
            } else Modifier
        )
        .then(
            if (settings.gesturesEnabled && settings.doubleTapAction != DoubleTapGestureAction.NONE) {
                Modifier.pointerInput(song.id, settings.doubleTapAction) {
                    detectTapGestures(
                        onDoubleTap = {
                            when (settings.doubleTapAction) {
                                DoubleTapGestureAction.PLAY_PAUSE -> latestOnTogglePlayPause()
                                DoubleTapGestureAction.FAVORITE -> latestOnToggleFavorite()
                                DoubleTapGestureAction.NONE -> Unit
                            }
                        }
                    )
                }
            } else Modifier
        )

    Box(
        modifier = modifier
            .then(gestureModifier)
            .testTag("now_playing_visual_stage")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (settings.gesturesEnabled && settings.verticalVolumeGestureEnabled) 24.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (settings.cinematicLyricsEnabled && isLyricsOverlayVisible) {
                    CinematicLyricsOverlay(
                        song = song,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        settings = settings,
                        viewModel = viewModel,
                        onOpenFullLyrics = onOpenFullLyrics,
                        onImportLrc = onImportLrc,
                        onSeekTo = onSeekTo,
                        onToggleOverlay = onToggleLyricsOverlay
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (settings.playerStyle == PlayerScreenStyle.MINIMAL) {
                            MinimalTrackFormatBadge(song = song)
                        }
                        if (settings.cinematicLyricsEnabled) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF06101D).copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onToggleLyricsOverlay() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Lyrics, null, tint = accentPrimary, modifier = Modifier.size(16.dp))
                                    Text("Show Credits Lyrics", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            if (effectiveVisualizerMode != VisualizerMode.OFF) {
                AudioVisualizerDisplay(
                    bars = visualizerBars,
                    mode = effectiveVisualizerMode,
                    accentPrimary = accentPrimary,
                    accentSecondary = accentSecondary
                )
            }
        }

        // Dedicated right-edge vertical volume drag zone so center lyrics scrolling never conflicts
        if (settings.gesturesEnabled && settings.verticalVolumeGestureEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(26.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        var currentRatio = latestVolumeRatio
                        detectVerticalDragGestures(
                            onDragStart = { currentRatio = latestVolumeRatio },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / size.height.toFloat().coerceAtLeast(200f)
                                currentRatio = (currentRatio + delta).coerceIn(0f, 1f)
                                latestOnVolumeChange(currentRatio)
                            }
                        )
                    }
                    .testTag("vertical_volume_gesture_zone")
            )
        }

        AnimatedVisibility(
            visible = gestureFeedbackText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF06101D).copy(alpha = 0.88f),
                border = BorderStroke(1.dp, accentPrimary.copy(alpha = 0.65f))
            ) {
                Text(
                    text = gestureFeedbackText ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    sleepTimerState: SleepTimerState,
    stopAfterCurrentTrack: Boolean,
    stopAfterCurrentPlaylist: Boolean,
    primaryColor: Color,
    accentPrimary: Color,
    onCollapse: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.testTag("collapse_now_playing_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = primaryColor,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.now_playing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            if (sleepTimerState.active) {
                Text(
                    text = "Sleep ${formatSeconds(sleepTimerState.remainingSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentPrimary
                )
            } else if (stopAfterCurrentTrack) {
                Text(
                    text = "Stops after track",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else if (stopAfterCurrentPlaylist) {
                Text(
                    text = "Stops after playlist",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenCarMode) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsCar,
                    contentDescription = "Car Mode",
                    tint = primaryColor
                )
            }
            IconButton(onClick = onOpenSleepTimer) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepTimerState.active || stopAfterCurrentTrack || stopAfterCurrentPlaylist) {
                        accentPrimary
                    } else primaryColor
                )
            }
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.testTag("open_queue_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Playback Queue",
                    tint = primaryColor
                )
            }
        }
    }
}

@Composable
private fun NowPlayingControlsSection(
    song: Song,
    playbackState: PlaybackState,
    volumeState: DeviceVolumeState,
    waveformBars: List<Float>,
    settings: AppSettings,
    sliderValue: Float,
    currentPosition: Long,
    duration: Long,
    showSpeedMenu: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    accentPrimary: Color,
    accentSecondary: Color,
    chipContainerColor: Color,
    chipBorderColor: Color,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onCycleVisualizer: () -> Unit,
    onToggleSpeedMenu: (Boolean) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleStopAfter: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    // Track Metadata + Preserved Album Artwork Thumbnail + Favorite Button
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (settings.playerStyle == PlayerScreenStyle.VINYL) {
                CompactVinylArtwork(song = song, isPlaying = playbackState.isPlaying)
            } else {
                CompactAlbumArtworkCard(song = song)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("now_playing_fav_button")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) MaterialTheme.colorScheme.tertiary else secondaryColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Quick feature bar: Lyrics, Bookmarks, Equalizer, Speed, Stop After Track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onOpenLyrics,
                label = { Text("Lyrics", color = primaryColor) },
                leadingIcon = { Icon(Icons.Rounded.Lyrics, null, Modifier.size(16.dp), tint = accentPrimary) },
                colors = AssistChipDefaults.assistChipColors(containerColor = chipContainerColor),
                border = BorderStroke(1.dp, chipBorderColor)
            )
            AssistChip(
                onClick = onOpenBookmarks,
                label = { Text("Bookmarks", color = primaryColor) },
                leadingIcon = { Icon(Icons.Rounded.Bookmarks, null, Modifier.size(16.dp), tint = accentPrimary) },
                colors = AssistChipDefaults.assistChipColors(containerColor = chipContainerColor),
                border = BorderStroke(1.dp, chipBorderColor)
            )
            AssistChip(
                onClick = onOpenEqualizer,
                label = { Text("EQ", color = primaryColor) },
                leadingIcon = { Icon(Icons.Rounded.Equalizer, null, Modifier.size(16.dp), tint = accentPrimary) },
                colors = AssistChipDefaults.assistChipColors(containerColor = chipContainerColor),
                border = BorderStroke(1.dp, chipBorderColor)
            )
            Box {
                AssistChip(
                    onClick = { onToggleSpeedMenu(true) },
                    label = { Text("${String.format(Locale.US, "%.2gx", playbackState.playbackSpeed)}", color = primaryColor) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = chipContainerColor),
                    border = BorderStroke(1.dp, chipBorderColor)
                )
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { onToggleSpeedMenu(false) }
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { spd ->
                        DropdownMenuItem(
                            text = { Text("${spd}x") },
                            onClick = { onSelectSpeed(spd) }
                        )
                    }
                }
            }
            AssistChip(
                onClick = onCycleVisualizer,
                label = { Text(settings.visualizerMode.label, color = primaryColor) },
                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp), tint = accentPrimary) },
                colors = AssistChipDefaults.assistChipColors(containerColor = chipContainerColor),
                border = BorderStroke(1.dp, chipBorderColor)
            )
            AssistChip(
                onClick = onToggleStopAfter,
                label = { Text("Stop After", color = primaryColor) },
                leadingIcon = { Icon(Icons.Rounded.StopCircle, null, Modifier.size(16.dp), tint = accentPrimary) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (playbackState.stopAfterCurrentTrack) {
                        accentPrimary.copy(alpha = 0.35f)
                    } else chipContainerColor
                ),
                border = BorderStroke(1.dp, chipBorderColor)
            )
        }
    }

    // Waveform Seekbar or Classic Progress Slider & Timestamps
    Column(modifier = Modifier.fillMaxWidth()) {
        if (settings.waveformSeekbarEnabled) {
            WaveformSeekbar(
                bars = waveformBars,
                progress = sliderValue,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderChangeFinished,
                activeColor = accentPrimary,
                inactiveColor = Color.White.copy(alpha = 0.28f)
            )
        } else {
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderChangeFinished,
                colors = SliderDefaults.colors(
                    thumbColor = accentPrimary,
                    activeTrackColor = accentPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("track_progress_slider")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimeMs(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = secondaryColor
            )
            Text(
                text = formatTimeMs(duration),
                style = MaterialTheme.typography.labelMedium,
                color = secondaryColor
            )
        }
    }

    // Transport Controls
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(
            onClick = onToggleShuffle,
            modifier = Modifier.testTag("shuffle_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (playbackState.isShuffle) accentPrimary else secondaryColor.copy(alpha = 0.72f)
            )
        }

        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(48.dp)
                .testTag("prev_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous Track",
                tint = primaryColor,
                modifier = Modifier.size(36.dp)
            )
        }

        FilledIconButton(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accentPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .size(72.dp)
                .shadow(14.dp, CircleShape, spotColor = accentPrimary)
                .testTag("play_pause_button")
        ) {
            Icon(
                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(48.dp)
                .testTag("next_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next Track",
                tint = primaryColor,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(
            onClick = onToggleRepeat,
            modifier = Modifier.testTag("repeat_button")
        ) {
            val repeatIcon = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
            val repeatColor = if (playbackState.repeatMode == RepeatMode.OFF) {
                secondaryColor.copy(alpha = 0.72f)
            } else accentPrimary
            Icon(
                imageVector = repeatIcon,
                contentDescription = "Repeat",
                tint = repeatColor
            )
        }
    }

    // Real Android Device Volume Slider (AudioManager STREAM_MUSIC)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
            contentDescription = "Volume Down",
            tint = secondaryColor,
            modifier = Modifier.size(20.dp)
        )
        Slider(
            value = volumeState.ratio,
            onValueChange = onVolumeChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .testTag("volume_slider"),
            colors = SliderDefaults.colors(
                thumbColor = accentSecondary,
                activeTrackColor = accentSecondary,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            )
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "Volume Up",
            tint = secondaryColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "${volumeState.percentage}%",
            style = MaterialTheme.typography.labelSmall,
            color = accentSecondary,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun CompactAlbumArtworkCard(song: Song) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = MaterialTheme.colorScheme.primary)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C1829).copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .testTag("now_playing_album_art"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = Color.White.copy(alpha = 0.65f)
        )
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = song.album,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun CompactVinylArtwork(song: Song, isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "vinylAngle"
    )
    val rotation = if (isPlaying) angle else 0f

    Box(
        modifier = Modifier
            .size(66.dp)
            .rotate(rotation)
            .testTag("now_playing_album_art"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF232634), Color(0xFF0A0A0E))
                ),
                radius = radius
            )
            for (rFactor in listOf(0.88f, 0.74f)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.14f),
                    radius = radius * rFactor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF10243C)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.album,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun VisualizerBarsStrip(bars: List<Float>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        val count = bars.size.coerceAtLeast(1)
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.6f).coerceAtLeast(4f)
        bars.forEachIndexed { idx, amp ->
            val clamped = amp.coerceIn(0.06f, 1.0f)
            val barHeight = size.height * clamped
            val x = idx * slotWidth + (slotWidth - barWidth) / 2f
            val y = size.height - barHeight
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(secondaryColor, primaryColor)),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun MinimalTrackFormatBadge(song: Song) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF071220).copy(alpha = 0.68f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "${song.genre} • ${song.mimeType.uppercase(Locale.US)} • ${song.formattedSize}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(
    sleepTimerState: SleepTimerState,
    stopAfterCurrentTrack: Boolean,
    stopAfterCurrentPlaylist: Boolean,
    onToggleStopAfterTrack: () -> Unit,
    onToggleStopAfterPlaylist: () -> Unit,
    onStartTimer: (minutes: Int, fadeOut: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var fadeOut by remember { mutableStateOf(sleepTimerState.fadeOutEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sleepTimerState.active) {
                    Text(
                        text = "Active countdown: ${formatSeconds(sleepTimerState.remainingSeconds)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Smooth volume fade-out")
                    Switch(checked = fadeOut, onCheckedChange = { fadeOut = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stop after current track")
                    Switch(checked = stopAfterCurrentTrack, onCheckedChange = { onToggleStopAfterTrack() })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.stop_after_playlist))
                    Switch(checked = stopAfterCurrentPlaylist, onCheckedChange = { onToggleStopAfterPlaylist() })
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 30, 45, 60).forEach { min ->
                        OutlinedButton(onClick = { onStartTimer(min, fadeOut) }) {
                            Text("${min}m")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (sleepTimerState.active) {
                Button(onClick = onCancelTimer) { Text("Turn Off Timer") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (sleepTimerState.active) {
                TextButton(onClick = onDismiss) { Text("Keep Running") }
            }
        }
    )
}

@Composable
private fun SaveQueueAsPlaylistDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_queue_as_playlist), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    song: Song,
    currentPositionMs: Long,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val bookmarks by viewModel.observeBookmarksForSong(song.id).collectAsState(initial = emptyList())
    var label by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Audio Bookmarks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Note at ${formatTimeMs(currentPositionMs)}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                FilledIconButton(
                    onClick = {
                        viewModel.addBookmarkAtCurrentPosition(song, label)
                        label = ""
                    }
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Save Bookmark")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (bookmarks.isEmpty()) {
                Text(
                    text = "No bookmarks saved for this track yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.seekTo(bm.positionMs)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimeMs(bm.positionMs),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(64.dp)
                            )
                            Text(
                                text = bm.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.deleteBookmark(bm.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete Bookmark")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun formatTimeMs(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatSeconds(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}
