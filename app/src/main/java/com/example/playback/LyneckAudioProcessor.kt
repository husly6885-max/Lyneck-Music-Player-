package com.example.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Real-time PCM DSP processor for Media3 ExoPlayer:
 * - Left / Right Stereo Audio Balance (-1.0f..+1.0f)
 * - Volume Normalization (adaptive RMS loudness gain) & ReplayGain dB
 * - Live 24-bar audio visualizer amplitude spectrum (no RECORD_AUDIO permission required)
 */
@OptIn(UnstableApi::class)
class LyneckAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var balance: Float = 0f // -1.0 = full left, 0.0 = center, +1.0 = full right

    @Volatile
    var normalizeVolume: Boolean = false

    @Volatile
    var replayGainEnabled: Boolean = false

    @Volatile
    var trackReplayGainDb: Float = 0f

    @Volatile
    var visualizerActive: Boolean = false

    private val _visualizerBars = MutableStateFlow(List(24) { 0.05f })
    val visualizerBars = _visualizerBars.asStateFlow()

    private var smoothedGain = 1.0f
    private var lastVizEmitMs = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val bal = balance.coerceIn(-1f, 1f)

        val leftGain = if (bal > 0f) (1f - bal) else 1f
        val rightGain = if (bal < 0f) (1f + bal) else 1f

        val rgLinear = if (replayGainEnabled && trackReplayGainDb != 0f) {
            10.0.pow((trackReplayGainDb.coerceIn(-12f, 12f) / 20.0)).toFloat()
        } else {
            1.0f
        }

        val dup = inputBuffer.slice().order(inputBuffer.order())
        val totalSamples = remaining / 2

        // First pass for RMS if normalization or visualizer is active
        var sumSq = 0.0
        val sampleBuckets = if (visualizerActive) FloatArray(24) else null
        val samplesPerBar = if (visualizerActive) (totalSamples / 24).coerceAtLeast(1) else 1

        if (normalizeVolume || visualizerActive) {
            var idx = 0
            while (dup.remaining() >= 2) {
                val sample = dup.getShort().toFloat() / 32768f
                sumSq += (sample * sample)
                if (sampleBuckets != null) {
                    val barIdx = (idx / samplesPerBar).coerceIn(0, 23)
                    val a = abs(sample)
                    if (a > sampleBuckets[barIdx]) {
                        sampleBuckets[barIdx] = a
                    }
                }
                idx++
            }
        }

        if (normalizeVolume && totalSamples > 0) {
            val rms = sqrt(sumSq / totalSamples).toFloat().coerceAtLeast(0.01f)
            val targetRms = 0.22f
            val desiredGain = (targetRms / rms).coerceIn(0.6f, 2.2f)
            smoothedGain = smoothedGain * 0.92f + desiredGain * 0.08f
        } else {
            smoothedGain = 1.0f
        }

        val combinedGain = smoothedGain * rgLinear

        // Process and write PCM16 samples
        var channelIdx = 0
        while (inputBuffer.remaining() >= 2) {
            val rawSample = inputBuffer.getShort().toFloat()
            val chanGain = when {
                channels >= 2 && channelIdx % channels == 0 -> leftGain
                channels >= 2 && channelIdx % channels == 1 -> rightGain
                else -> 1.0f
            }
            val scaled = (rawSample * combinedGain * chanGain)
                .coerceIn(-32767f, 32767f)
            outputBuffer.putShort(scaled.toInt().toShort())
            channelIdx++
        }
        // Copy trailing odd byte if any
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()

        if (visualizerActive && sampleBuckets != null) {
            val now = System.currentTimeMillis()
            if (now - lastVizEmitMs >= 50L) {
                lastVizEmitMs = now
                val prev = _visualizerBars.value
                val next = List(24) { i ->
                    val boosted = min(1f, sampleBuckets[i] * 1.8f).coerceAtLeast(0.05f)
                    prev[i] * 0.45f + boosted * 0.55f
                }
                _visualizerBars.value = next
            }
        }
    }

    fun resetVisualizer() {
        _visualizerBars.value = List(24) { 0.05f }
    }

    override fun onReset() {
        smoothedGain = 1.0f
        resetVisualizer()
    }
}
