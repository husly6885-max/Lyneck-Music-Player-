package com.example.data.lyrics

import android.content.Context
import android.net.Uri
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val rawText: String,
    val embeddedOffsetMs: Long = 0L
)

class LyricsManager(private val context: Context) {

    fun parse(rawText: String): ParsedLyrics = parseRaw(rawText)

    companion object {
        private val lineTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val inlineKaraokeRegex = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
        private val offsetTagRegex = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

        fun parseRaw(rawText: String): ParsedLyrics {
            if (rawText.isBlank()) {
                return ParsedLyrics(emptyList(), isSynced = false, rawText = "")
            }

            val syncedLines = mutableListOf<LyricLine>()
            val rawLines = rawText.lines()
            var embeddedOffset = 0L

            for (line in rawLines) {
                offsetTagRegex.find(line)?.let { offMatch ->
                    embeddedOffset = offMatch.groupValues[1].toLongOrNull() ?: embeddedOffset
                }
                val matches = lineTimestampRegex.findAll(line).toList()
                if (matches.isNotEmpty()) {
                    val cleanedText = lineTimestampRegex.replace(line, "")
                        .replace(inlineKaraokeRegex, "")
                        .trim()
                    if (cleanedText.isNotBlank() || matches.size == 1) {
                        for (match in matches) {
                            val min = match.groupValues[1].toLongOrNull() ?: 0L
                            val sec = match.groupValues[2].toLongOrNull() ?: 0L
                            val fracStr = match.groupValues.getOrNull(3) ?: "0"
                            val ms = when (fracStr.length) {
                                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                                3 -> fracStr.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                            val timestampMs = (min * 60_000L) + (sec * 1000L) + ms
                            syncedLines.add(LyricLine(timestampMs, cleanedText.ifBlank { "♪" }))
                        }
                    }
                }
            }

            return if (syncedLines.isNotEmpty()) {
                ParsedLyrics(
                    lines = syncedLines.sortedBy { it.timestampMs },
                    isSynced = true,
                    rawText = rawText,
                    embeddedOffsetMs = embeddedOffset
                )
            } else {
                val plainLines = rawLines
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapIndexed { idx, text ->
                        LyricLine(timestampMs = idx.toLong() * 1000L, text = text)
                    }
                ParsedLyrics(
                    lines = plainLines,
                    isSynced = false,
                    rawText = rawText,
                    embeddedOffsetMs = 0L
                )
            }
        }

        fun findActiveLineIndex(
            parsed: ParsedLyrics,
            positionMs: Long,
            durationMs: Long = 0L,
            offsetMs: Long = 0L
        ): Int {
            if (parsed.lines.isEmpty()) return -1
            val effectivePos = (positionMs + offsetMs + parsed.embeddedOffsetMs).coerceAtLeast(0L)
            return if (parsed.isSynced) {
                parsed.lines.indexOfLast { it.timestampMs <= effectivePos }.coerceAtLeast(0)
            } else {
                val dur = durationMs.coerceAtLeast(1L)
                val fraction = (positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 0.999f)
                (fraction * parsed.lines.size).toInt().coerceIn(0, parsed.lines.lastIndex)
            }
        }
    }

    suspend fun findLyricsForSong(song: Song, autoLoadExternalLrc: Boolean): ParsedLyrics? =
        withContext(Dispatchers.IO) {
            // 1. Check external .lrc next to audio file if path is accessible
            if (autoLoadExternalLrc && song.dataPath.isNotBlank()) {
                val audioFile = File(song.dataPath)
                val base = audioFile.nameWithoutExtension
                val parent = audioFile.parentFile
                if (parent != null) {
                    val lrcFile = File(parent, "$base.lrc")
                    if (lrcFile.exists() && lrcFile.canRead()) {
                        val content = runCatching { lrcFile.readText() }.getOrNull()
                        if (!content.isNullOrBlank()) {
                            return@withContext parse(content)
                        }
                    }
                }
            }

            // 2. Check embedded ID3v2 USLT lyrics directly from ContentResolver InputStream
            val embedded = readEmbeddedId3Lyrics(song.contentUri)
            if (!embedded.isNullOrBlank()) {
                return@withContext parse(embedded)
            }

            null
        }

    suspend fun readLrcFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun readEmbeddedId3Lyrics(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractUsltFromId3Stream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractUsltFromId3Stream(stream: InputStream): String? {
        val header = ByteArray(10)
        if (stream.read(header) < 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }

        val majorVersion = header[3].toInt() and 0xFF
        val flags = header[5].toInt() and 0xFF
        val tagSize = syncSafeInt(header, 6)
        if (tagSize <= 0 || tagSize > 8 * 1024 * 1024) return null

        val tagData = ByteArray(tagSize)
        var totalRead = 0
        while (totalRead < tagSize) {
            val read = stream.read(tagData, totalRead, tagSize - totalRead)
            if (read <= 0) break
            totalRead += read
        }

        var offset = 0
        if ((flags and 0x40) != 0 && totalRead >= 4) {
            val extSize = if (majorVersion == 4) syncSafeInt(tagData, 0) else bigEndianInt(tagData, 0)
            offset += extSize.coerceAtLeast(4)
        }

        while (offset + 10 <= totalRead) {
            val frameId = String(tagData, offset, 4, Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            val frameSize = if (majorVersion == 4) {
                syncSafeInt(tagData, offset + 4)
            } else {
                bigEndianInt(tagData, offset + 4)
            }
            offset += 10
            if (frameSize <= 0 || offset + frameSize > totalRead) break

            if (frameId == "USLT") {
                return decodeUsltFrame(tagData, offset, frameSize)
            }
            offset += frameSize
        }
        return null
    }

    private fun decodeUsltFrame(data: ByteArray, start: Int, length: Int): String? {
        if (length < 5) return null
        val encoding = data[start].toInt() and 0xFF
        var pos = start + 4
        val end = start + length

        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }

        if (encoding == 1 || encoding == 2) {
            while (pos + 1 < end) {
                if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                    pos += 2
                    break
                }
                pos += 2
            }
        } else {
            while (pos < end) {
                if (data[pos] == 0.toByte()) {
                    pos += 1
                    break
                }
                pos++
            }
        }

        if (pos >= end) return null
        return String(data, pos, end - pos, charset).trim('\u0000', ' ', '\n', '\r')
    }

    private fun syncSafeInt(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0x7F) shl 21) or
            ((buf[offset + 1].toInt() and 0x7F) shl 14) or
            ((buf[offset + 2].toInt() and 0x7F) shl 7) or
            (buf[offset + 3].toInt() and 0x7F)
    }

    private fun bigEndianInt(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
    }
}
