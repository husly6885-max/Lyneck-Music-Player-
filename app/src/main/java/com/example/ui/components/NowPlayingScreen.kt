package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.data.preferences.PlayerScreenStyle
import com.example.playback.DeviceVolumeState
import com.example.playback.PlaybackState
import com.example.playback.RepeatMode
import com.example.playback.SleepTimerState
import com.example.ui.MusicViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    volumeState: DeviceVolumeState,
    visualizerBars: List<Float>,
    sleepTimerState: SleepTimerState,
    settings: AppSettings,
    savedQueues: List<PlaybackQueueInfo>,
    viewModel: MusicViewModel,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenLyrics: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showCreateQueueDialog by remember { mutableStateOf(false) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val currentPosition = if (isDraggingSlider) dragPositionMs.toLong() else playbackState.currentPositionMs
    val duration = playbackState.durationMs.coerceAtLeast(1L)
    val sliderValue = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("now_playing_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            // Top bar: Collapse, Car Mode, Sleep Timer, Queue
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
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (sleepTimerState.active) {
                        Text(
                            text = "Sleep ${formatSeconds(sleepTimerState.remainingSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (playbackState.stopAfterCurrentTrack) {
                        Text(
                            text = "Stops after track",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenCarMode) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = "Car Mode"
                        )
                    }
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerState.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.testTag("open_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Playback Queue"
                        )
                    }
                }
            }

            // Responsive Artwork Showcase (scales gracefully on compact/low-end screens)
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when (settings.playerStyle) {
                    PlayerScreenStyle.CLASSIC -> ClassicArtworkBox(song)
                    PlayerScreenStyle.VINYL -> VinylTurntableBox(song, playbackState.isPlaying)
                    PlayerScreenStyle.VISUALIZER -> VisualizerArtworkBox(song, visualizerBars)
                    PlayerScreenStyle.MINIMAL -> MinimalArtworkBox(song)
                }
            }

            // Track Metadata & Quick Action Icons
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${song.artist} • ${song.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavorite(song) },
                        modifier = Modifier.testTag("now_playing_fav_button")
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Quick feature bar: Lyrics, Bookmarks, Equalizer, Speed, Stop After Track
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { onOpenLyrics(song) },
                        label = { Text("Lyrics") },
                        leadingIcon = { Icon(Icons.Rounded.Lyrics, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { showBookmarksSheet = true },
                        label = { Text("Bookmarks") },
                        leadingIcon = { Icon(Icons.Rounded.Bookmarks, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = onOpenEqualizer,
                        label = { Text("EQ") },
                        leadingIcon = { Icon(Icons.Rounded.Equalizer, null, Modifier.size(16.dp)) }
                    )
                    Box {
                        AssistChip(
                            onClick = { showSpeedMenu = true },
                            label = { Text("${String.format(Locale.US, "%.2gx", playbackState.playbackSpeed)}") }
                        )
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { spd ->
                                DropdownMenuItem(
                                    text = { Text("${spd}x") },
                                    onClick = {
                                        viewModel.setPlaybackSpeed(spd)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                    AssistChip(
                        onClick = { viewModel.toggleStopAfterCurrent() },
                        label = { Text("Stop After") },
                        leadingIcon = { Icon(Icons.Rounded.StopCircle, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (playbackState.stopAfterCurrentTrack) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // Progress Slider & Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        isDraggingSlider = true
                        dragPositionMs = newVal * duration
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        viewModel.seekTo(dragPositionMs.toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("track_progress_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeMs(currentPosition),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimeMs(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.testTag("shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous Track",
                        modifier = Modifier.size(36.dp)
                    )
                }

                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.testTag("repeat_button")
                ) {
                    val repeatIcon = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                    val repeatColor = if (playbackState.repeatMode == RepeatMode.OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else MaterialTheme.colorScheme.primary
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Slider(
                    value = volumeState.ratio,
                    onValueChange = { viewModel.setDeviceVolumeRatio(it) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Volume Up",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${volumeState.percentage}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        }
    }

    // Queue Sheet with Multiple Independent Queues & Reordering
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
                        fontWeight = FontWeight.Bold
                    )
                    Row {
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

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            sleepTimerState = sleepTimerState,
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
private fun ClassicArtworkBox(song: Song) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.78f)
            .heightIn(max = 320.dp)
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                spotColor = MaterialTheme.colorScheme.primary
            )
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(84.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
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
private fun VinylTurntableBox(song: Song, isPlaying: Boolean) {
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
            .fillMaxWidth(0.76f)
            .aspectRatio(1f)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1F1F28), Color(0xFF0A0A0E))
                ),
                radius = radius
            )
            for (rFactor in listOf(0.88f, 0.78f, 0.68f, 0.58f)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius * rFactor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.46f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
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
private fun VisualizerArtworkBox(song: Song, bars: List<Float>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier.fillMaxWidth(0.84f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = primaryColor.copy(alpha = 0.4f)
            )
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.album,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val count = bars.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.62f).coerceAtLeast(4f)
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
}

@Composable
private fun MinimalArtworkBox(song: Song) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.76f)
            .aspectRatio(1.1f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = song.genre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${song.mimeType.uppercase(Locale.US)} • ${song.formattedSize}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(
    sleepTimerState: SleepTimerState,
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
