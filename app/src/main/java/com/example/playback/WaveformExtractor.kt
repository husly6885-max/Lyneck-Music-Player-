package com.example.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Memory-safe, streaming audio waveform extractor.
 * Samples up to [barCount] evenly spaced timestamps using MediaExtractor + MediaCodec
 * without loading entire audio files into RAM, and caches quantized peaks to disk.
 */
class WaveformExtractor(private val context: Context) {

    companion object {
        const val DEFAULT_BAR_COUNT = 68
        private const val CACHE_DIR_NAME = "waveforms"

        fun normalizePeaks(raw: FloatArray, barCount: Int = DEFAULT_BAR_COUNT): List<Float> {
            if (raw.isEmpty()) return List(barCount) { 0.25f }
            val maxPeak = raw.maxOrNull()?.coerceAtLeast(1e-4f) ?: 1f
            return List(barCount) { idx ->
                val srcIdx = ((idx.toFloat() / barCount) * raw.size).toInt().coerceIn(0, raw.lastIndex)
                val normalized = (raw[srcIdx] / maxPeak).coerceIn(0f, 1f)
                (0.12f + normalized * 0.88f).coerceIn(0.12f, 1.0f)
            }
        }

        fun serializeBars(bars: List<Float>): String =
            bars.joinToString(",") { ((it.coerceIn(0f, 1f)) * 100f).toInt().toString() }

        fun deserializeBars(raw: String, barCount: Int = DEFAULT_BAR_COUNT): List<Float>? {
            val parts = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size < 8) return null
            val arr = FloatArray(parts.size) { parts[it].coerceIn(5, 100) / 100f }
            return if (arr.size == barCount) arr.toList() else normalizePeaks(arr, barCount)
        }
    }

    private val memoryCache = LruCache<String, List<Float>>(32)
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    }

    fun cacheKeyFor(song: Song): String {
        val safeHash = song.contentUri.toString().hashCode().toUInt().toString(16)
        return "${song.id}_${song.size}_${song.duration}_$safeHash"
    }

    suspend fun extractWaveform(
        song: Song,
        barCount: Int = DEFAULT_BAR_COUNT
    ): List<Float> = withContext(Dispatchers.IO) {
        val key = cacheKeyFor(song)
        memoryCache.get(key)?.let { return@withContext it }

        val cacheFile = File(cacheDir, "$key.wf")
        if (cacheFile.exists() && cacheFile.canRead()) {
            val cached = runCatching { cacheFile.readText() }.getOrNull()
                ?.let { deserializeBars(it, barCount) }
            if (cached != null) {
                memoryCache.put(key, cached)
                return@withContext cached
            }
        }

        val extracted = runCatching {
            extractPeaksFromUri(song.contentUri, song.duration, barCount)
        }.getOrNull() ?: buildDeterministicFallback(song, barCount)

        memoryCache.put(key, extracted)
        runCatching { cacheFile.writeText(serializeBars(extracted)) }
        extracted
    }

    private fun extractPeaksFromUri(uri: Uri, knownDurationMs: Long, barCount: Int): List<Float>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                knownDurationMs * 1000L
            }.coerceAtLeast(1_000_000L)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val peaks = FloatArray(barCount)
            val stepUs = (durationUs / barCount).coerceAtLeast(10_000L)

            for (barIdx in 0 until barCount) {
                val targetUs = (barIdx.toLong() * stepUs).coerceIn(0L, durationUs - 10_000L)
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var barRms = 0f
                var decodedSamples = 0
                var iterations = 0
                var sawOutput = false

                while (!sawOutput && iterations < 8) {
                    iterations++
                    val inIdx = codec.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 6_000L)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shorts = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var sumSq = 0.0
                            val count = shorts.remaining().coerceAtMost(1024)
                            for (s in 0 until count) {
                                val v = shorts.get().toFloat() / 32768f
                                sumSq += (v * v)
                            }
                            if (count > 0) {
                                barRms = sqrt(sumSq / count).toFloat()
                                decodedSamples = count
                                sawOutput = true
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && iterations >= 5) {
                        break
                    }
                }
                peaks[barIdx] = if (decodedSamples > 0) barRms else 0.12f
            }

            if (peaks.all { abs(it - peaks.first()) < 0.001f }) null
            else normalizePeaks(peaks, barCount)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun buildDeterministicFallback(song: Song, barCount: Int): List<Float> {
        var seed = (song.id * 31L + song.duration * 17L + song.size).toInt()
        return List(barCount) { idx ->
            seed = seed * 1103515245 + 12345
            val wave = ((seed ushr 16) and 0x7FFF) / 32767f
            val envelope = kotlin.math.sin((idx.toFloat() / barCount) * kotlin.math.PI).toFloat()
            (0.22f + wave * 0.55f * (0.4f + 0.6f * envelope)).coerceIn(0.18f, 0.95f)
        }
    }
}
