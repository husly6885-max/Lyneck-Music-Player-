package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.preferences.VisualizerMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AudioVisualizerDisplay(
    bars: List<Float>,
    mode: VisualizerMode,
    modifier: Modifier = Modifier,
    accentPrimary: Color = MaterialTheme.colorScheme.primary,
    accentSecondary: Color = MaterialTheme.colorScheme.secondary
) {
    if (mode == VisualizerMode.OFF) return

    when (mode) {
        VisualizerMode.OFF -> Unit
        VisualizerMode.SPECTRUM_BARS -> {
            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("audio_visualizer_spectrum")
            ) {
                val safeBars = if (bars.isEmpty()) List(24) { 0.08f } else bars
                val count = safeBars.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.58f).coerceAtLeast(3.dp.toPx())
                safeBars.forEachIndexed { idx, amp ->
                    val clamped = amp.coerceIn(0.06f, 1.0f)
                    val barHeight = size.height * clamped
                    val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val y = size.height - barHeight
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(accentSecondary, accentPrimary)),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
        VisualizerMode.WAVEFORM -> {
            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("audio_visualizer_waveform")
            ) {
                val safeBars = if (bars.isEmpty()) List(24) { 0.08f } else bars
                val midY = size.height / 2f
                val stepX = size.width / (safeBars.size - 1).coerceAtLeast(1)

                val topPath = Path().apply {
                    moveTo(0f, midY)
                    safeBars.forEachIndexed { idx, amp ->
                        val x = idx * stepX
                        val offset = amp.coerceIn(0.04f, 1f) * (size.height * 0.46f)
                        val sign = if (idx % 2 == 0) -1f else 1f
                        lineTo(x, midY + sign * offset)
                    }
                }
                drawPath(
                    path = topPath,
                    brush = Brush.horizontalGradient(listOf(accentPrimary, accentSecondary, accentPrimary)),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val glowPath = Path().apply {
                    moveTo(0f, midY)
                    safeBars.forEachIndexed { idx, amp ->
                        val x = idx * stepX
                        val offset = amp.coerceIn(0.03f, 1f) * (size.height * 0.28f)
                        lineTo(x, midY - offset)
                    }
                }
                drawPath(
                    path = glowPath,
                    color = accentSecondary.copy(alpha = 0.45f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        VisualizerMode.CIRCULAR -> {
            Box(
                modifier = modifier
                    .size(118.dp)
                    .testTag("audio_visualizer_circular"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val safeBars = if (bars.isEmpty()) List(24) { 0.1f } else bars
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.28f
                    val maxRay = size.minDimension * 0.20f

                    drawCircle(
                        color = accentPrimary.copy(alpha = 0.25f),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val stepAngle = (2.0 * PI) / safeBars.size.coerceAtLeast(1)
                    safeBars.forEachIndexed { idx, amp ->
                        val angle = -PI / 2.0 + idx * stepAngle
                        val clamped = amp.coerceIn(0.08f, 1f)
                        val start = Offset(
                            x = center.x + (baseRadius * cos(angle)).toFloat(),
                            y = center.y + (baseRadius * sin(angle)).toFloat()
                        )
                        val endRadius = baseRadius + clamped * maxRay
                        val end = Offset(
                            x = center.x + (endRadius * cos(angle)).toFloat(),
                            y = center.y + (endRadius * sin(angle)).toFloat()
                        )
                        drawLine(
                            brush = Brush.linearGradient(listOf(accentPrimary, accentSecondary), start, end),
                            start = start,
                            end = end,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformSeekbar(
    bars: List<Float>,
    progress: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.White.copy(alpha = 0.30f)
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val safeBars = remember(bars) {
        if (bars.isEmpty()) List(64) { 0.3f } else bars
    }
    var dragRatio by remember { mutableFloatStateOf(safeProgress) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val ratio = (offset.x / width).coerceIn(0f, 1f)
                    currentOnValueChange(ratio)
                    currentOnFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragRatio = (startOffset.x / width).coerceIn(0f, 1f)
                        currentOnValueChange(dragRatio)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragRatio = (change.position.x / width).coerceIn(0f, 1f)
                        currentOnValueChange(dragRatio)
                    },
                    onDragEnd = { currentOnFinished() },
                    onDragCancel = { currentOnFinished() }
                )
            }
            .testTag("waveform_seekbar")
    ) {
        val count = safeBars.size.coerceAtLeast(1)
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.56f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
        val maxBarHeight = size.height * 0.78f
        val centerY = size.height / 2f
        val playheadX = safeProgress * size.width

        safeBars.forEachIndexed { idx, amp ->
            val clamped = amp.coerceIn(0.12f, 1f)
            val barHeight = maxBarHeight * clamped
            val x = idx * slotWidth + (slotWidth - barWidth) / 2f
            val top = centerY - (barHeight / 2f)
            val barCenterFraction = (idx + 0.5f) / count
            val color = if (barCenterFraction <= safeProgress) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // Crisp vertical playhead scrubber indicator
        drawLine(
            color = Color.White,
            start = Offset(playheadX.coerceIn(2f, size.width - 2f), size.height * 0.08f),
            end = Offset(playheadX.coerceIn(2f, size.width - 2f), size.height * 0.92f),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
