package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.lyrics.LyricsManager
import com.example.data.lyrics.ParsedLyrics
import com.example.data.model.Song
import com.example.data.preferences.AppSettings
import com.example.ui.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CinematicLyricsOverlay(
    song: Song,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    settings: AppSettings,
    viewModel: MusicViewModel,
    onOpenFullLyrics: () -> Unit,
    onImportLrc: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dbEntity by viewModel.observeLyrics(song.id).collectAsState(initial = null)
    var discovered by remember(song.id) { mutableStateOf<ParsedLyrics?>(null) }

    LaunchedEffect(song.id) {
        discovered = viewModel.loadLyricsIfMissing(song)
    }

    val parsed = remember(dbEntity, discovered) {
        val raw = dbEntity?.lyricsText ?: discovered?.rawText ?: ""
        viewModel.lyricsManager.parse(raw)
    }
    val offsetMs = dbEntity?.offsetMs ?: settings.defaultLyricsOffsetMs

    // Empty lyrics state: never show fake lyrics
    if (parsed.lines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("cinematic_lyrics_empty"),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF040B14).copy(alpha = 0.62f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "No lyrics found",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onImportLrc,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import .LRC")
                        }
                        FilledTonalButton(onClick = onOpenFullLyrics) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Lyrics")
                        }
                    }
                }
            }
        }
        return
    }

    val activeLineIndex = remember(parsed, currentPositionMs, durationMs, offsetMs) {
        LyricsManager.findActiveLineIndex(
            parsed = parsed,
            positionMs = currentPositionMs,
            durationMs = durationMs,
            offsetMs = offsetMs
        )
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var userPausedAutoScroll by remember(song.id) { mutableStateOf(false) }
    var tempManualScrollPause by remember(song.id) { mutableStateOf(false) }

    // Detect user manual drag/scroll inside the credits list and pause auto-scroll temporarily
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            tempManualScrollPause = true
        } else if (tempManualScrollPause && !userPausedAutoScroll) {
            delay(4000L)
            tempManualScrollPause = false
        }
    }

    // Movie-ending-credits-style continuous vertical synchronization with low CPU overhead
    LaunchedEffect(activeLineIndex, isPlaying, userPausedAutoScroll, tempManualScrollPause) {
        if (!userPausedAutoScroll && !tempManualScrollPause && activeLineIndex >= 0) {
            val targetIdx = (activeLineIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetIdx,
                scrollOffset = 0
            )
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val fontSize = settings.lyricsFontSizeSp.coerceIn(13, 28)
    val scrimAlpha = settings.lyricsBackdropContrast.coerceIn(0f, 0.9f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("cinematic_lyrics_overlay")
    ) {
        // Adjustable contrast backdrop behind scrolling credits while keeping background artwork visible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF02060D).copy(alpha = (scrimAlpha * 0.65f)),
                        0.5f to Color(0xFF02060D).copy(alpha = scrimAlpha),
                        1.0f to Color(0xFF02060D).copy(alpha = (scrimAlpha * 0.85f))
                    )
                )
        ) {
            // Vertical fading credits viewport
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 68.dp, bottom = 84.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.16f to Color.Black,
                                0.84f to Color.Black,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                itemsIndexed(parsed.lines) { idx, line ->
                    val isActive = idx == activeLineIndex
                    val targetAlpha = when {
                        isActive -> 1f
                        idx == activeLineIndex - 1 || idx == activeLineIndex + 1 -> 0.72f
                        else -> 0.44f
                    }
                    val animatedAlpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(durationMillis = 220),
                        label = "lyric_line_alpha"
                    )
                    val targetColor = if (isActive && settings.karaokeHighlight) {
                        accentColor
                    } else {
                        Color.White.copy(alpha = animatedAlpha)
                    }
                    val animatedColor by animateColorAsState(
                        targetValue = targetColor,
                        animationSpec = tween(durationMillis = 220),
                        label = "lyric_line_color"
                    )

                    Text(
                        text = line.text,
                        fontSize = if (isActive) (fontSize + 3).sp else fontSize.sp,
                        lineHeight = if (isActive) (fontSize + 9).sp else (fontSize + 6).sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = animatedColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isActive && settings.karaokeHighlight) {
                                    Modifier.background(Color.White.copy(alpha = 0.09f))
                                } else Modifier
                            )
                            .clickable(enabled = parsed.isSynced) {
                                userPausedAutoScroll = false
                                tempManualScrollPause = false
                                onSeekTo((line.timestampMs - offsetMs).coerceAtLeast(0L))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Compact top control bar for Credits Auto-Scroll, Font Size, Contrast, Full Editor
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF050E1B).copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            userPausedAutoScroll = !userPausedAutoScroll
                            if (!userPausedAutoScroll) tempManualScrollPause = false
                        }
                        .testTag("lyrics_autoscroll_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (userPausedAutoScroll || tempManualScrollPause) {
                                Icons.Rounded.PlayArrow
                            } else {
                                Icons.Rounded.Pause
                            },
                            contentDescription = "Toggle Auto-Scroll",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (userPausedAutoScroll) "Paused" else if (!isPlaying) "Ready" else "Credits Sync",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF050E1B).copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val nextSize = when {
                                    fontSize < 16 -> 18
                                    fontSize < 21 -> 22
                                    else -> 15
                                }
                                viewModel.setLyricsFontSizeSp(nextSize)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.FormatSize, contentDescription = "Cycle Font Size", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                val nextContrast = when {
                                    scrimAlpha < 0.35f -> 0.55f
                                    scrimAlpha < 0.7f -> 0.78f
                                    else -> 0.25f
                                }
                                viewModel.setLyricsBackdropContrast(nextContrast)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.Contrast, contentDescription = "Adjust Contrast", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onOpenFullLyrics,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Full Lyrics Sheet", tint = accentColor, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onToggleOverlay,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hide Overlay", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Return to current line floating pill when manually scrolled away
            AnimatedVisibility(
                visible = (tempManualScrollPause || userPausedAutoScroll) && activeLineIndex >= 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            userPausedAutoScroll = false
                            tempManualScrollPause = false
                            coroutineScope.launch {
                                listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
                            }
                        }
                        .testTag("lyrics_return_current_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "Return to current line",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Return to current line",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
