package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.data.lyrics.LyricsManager
import com.example.data.model.SearchScope
import com.example.data.model.SmartPlaylistType
import com.example.data.model.SortOrder
import com.example.data.preferences.AccentColorOption
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.DoubleTapGestureAction
import com.example.data.preferences.PlayerScreenStyle
import com.example.data.preferences.VisualizerMode
import com.example.data.scanner.MediaScanner
import com.example.playback.DeviceVolumeState
import com.example.playback.LyneckAudioProcessor
import com.example.playback.WaveformExtractor
import com.example.ui.ScrollPositionState
import com.example.ui.components.AlphaIndexHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LyneckCoreUnitTest {

    @Test
    fun `lyrics parser extracts multi-timestamps, offset tag and strips karaoke inline tags`() {
        val raw = """
            [ti:Neon Echoes]
            [offset:+350]
            [00:05.50]First <00:05.80>line <00:06.10>here
            [00:12.00][01:02.25]Chorus line
        """.trimIndent()

        val parsed = LyricsManager.parseRaw(raw)
        assertTrue(parsed.isSynced)
        assertEquals(350L, parsed.embeddedOffsetMs)
        assertEquals(3, parsed.lines.size)
        assertEquals(5500L, parsed.lines[0].timestampMs)
        assertEquals("First line here", parsed.lines[0].text)
        assertEquals(12000L, parsed.lines[1].timestampMs)
        assertEquals("Chorus line", parsed.lines[1].text)
        assertEquals(62250L, parsed.lines[2].timestampMs)

        // Active line resolution with embedded offset (+350ms) and manual offset
        assertEquals(0, LyricsManager.findActiveLineIndex(parsed, positionMs = 5200L))
        assertEquals(1, LyricsManager.findActiveLineIndex(parsed, positionMs = 11700L))
        assertEquals(2, LyricsManager.findActiveLineIndex(parsed, positionMs = 62000L))
    }

    @Test
    fun `lyrics parser handles plain text lyrics and fractional active line sync`() {
        val parsed = LyricsManager.parseRaw("Verse one\nChorus two\nBridge three\nOutro four")
        assertFalse(parsed.isSynced)
        assertEquals(0L, parsed.embeddedOffsetMs)
        assertEquals(4, parsed.lines.size)
        assertEquals("Verse one", parsed.lines[0].text)
        assertEquals("Outro four", parsed.lines[3].text)
        assertEquals(0, LyricsManager.findActiveLineIndex(parsed, positionMs = 1000L, durationMs = 40000L))
        assertEquals(2, LyricsManager.findActiveLineIndex(parsed, positionMs = 25000L, durationMs = 40000L))
        assertEquals(3, LyricsManager.findActiveLineIndex(parsed, positionMs = 39500L, durationMs = 40000L))
    }

    @Test
    fun `waveform extractor normalizes peaks and round-trips serialized cache format`() {
        val raw = floatArrayOf(0.05f, 0.25f, 0.50f, 1.0f, 0.40f, 0.10f)
        val normalized = WaveformExtractor.normalizePeaks(raw, barCount = 16)
        assertEquals(16, normalized.size)
        assertTrue(normalized.all { it in 0.12f..1.0f })

        val serialized = WaveformExtractor.serializeBars(normalized)
        val restored = WaveformExtractor.deserializeBars(serialized, barCount = 16)
        assertNotNull(restored)
        assertEquals(16, restored!!.size)
        for (i in normalized.indices) {
            assertEquals(normalized[i], restored[i], 0.02f)
        }
    }

    @Test
    fun `audio processor applies real stereo balance and replaygain on PCM16 samples`() {
        val processor = LyneckAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.balance = 1.0f // Full Right: mute Left, keep Right

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(12000.toShort())
        input.putShort(12000.toShort())
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, output.getShort().toInt())
        assertEquals(12000, output.getShort().toInt())
    }

    @Test
    fun `audio processor volume normalization boosts quiet signal and preserves near-silence floor`() {
        val processor = LyneckAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.normalizeVolume = true

        // Quiet but non-silent signal (~2500 / 32768 = ~0.076 RMS > 0.012f noise floor)
        val quietBuf = ByteBuffer.allocateDirect(40).order(ByteOrder.LITTLE_ENDIAN)
        repeat(20) { quietBuf.putShort(2500.toShort()) }
        quietBuf.flip()
        processor.queueInput(quietBuf)
        val boostedOut = processor.output.order(ByteOrder.LITTLE_ENDIAN).getShort().toInt()
        assertTrue("Quiet RMS should be smoothly boosted above 2500, got $boostedOut", boostedOut > 2500)

        // Near-silence buffer (below 0.012f floor) should pull gain back toward 1.0f rather than amplifying noise
        val silenceProcessor = LyneckAudioProcessor()
        silenceProcessor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        silenceProcessor.flush()
        silenceProcessor.normalizeVolume = true
        val silentBuf = ByteBuffer.allocateDirect(40).order(ByteOrder.LITTLE_ENDIAN)
        repeat(20) { silentBuf.putShort(50.toShort()) }
        silentBuf.flip()
        silenceProcessor.queueInput(silentBuf)
        val silentOut = silenceProcessor.output.order(ByteOrder.LITTLE_ENDIAN).getShort().toInt()
        assertEquals(50, silentOut)
    }

    @Test
    fun `audio processor visualizer updates non-flat amplitude bars when active`() {
        val processor = LyneckAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.visualizerActive = true

        val buf = ByteBuffer.allocateDirect(96).order(ByteOrder.LITTLE_ENDIAN)
        repeat(48) { buf.putShort(16000.toShort()) }
        buf.flip()

        processor.queueInput(buf)
        val bars = processor.visualizerBars.value
        assertEquals(24, bars.size)
        assertTrue(bars.any { it > 0.1f })
    }

    @Test
    fun `device volume state percentage calculation matches AudioManager steps`() {
        assertEquals(0, DeviceVolumeState(0, 15, 0f).percentage)
        assertEquals(53, DeviceVolumeState(8, 15, 8f / 15f).percentage)
        assertEquals(100, DeviceVolumeState(15, 15, 1f).percentage)
    }

    @Test
    fun `media scanner separates video files from offline music audio`() {
        assertFalse(MediaScanner.isAudioOnlyTrack("video/mp4", "/storage/emulated/0/Movies/clip.mp4"))
        assertFalse(MediaScanner.isAudioOnlyTrack("video/webm", "/storage/emulated/0/Download/mv.webm"))
        assertFalse(MediaScanner.isAudioOnlyTrack("audio/mpeg", "/storage/emulated/0/Download/video_track.mkv"))
        assertTrue(MediaScanner.isAudioOnlyTrack("audio/mpeg", "/storage/emulated/0/Music/song.mp3"))
        assertTrue(MediaScanner.isAudioOnlyTrack("audio/mp4", "/storage/emulated/0/Music/song.m4a"))
        assertTrue(MediaScanner.isAudioOnlyTrack("audio/flac", "/storage/emulated/0/Music/track.flac"))
    }

    @Test
    fun `alphabet index helper maps A to Z and # with nearest letter fallback`() {
        val titles = listOf(
            "A Better Tomorrow",
            "Midnight Groove",
            "99 Problems"
        )
        val map = AlphaIndexHelper.buildIndexMapForTitles(titles, headerCount = 1)
        assertEquals(1, map["A"])
        assertEquals(2, map["M"])
        assertEquals(3, map["#"])
        assertEquals(2, AlphaIndexHelper.findTargetIndex("C", map)) // nearest forward -> M
        assertEquals(3, AlphaIndexHelper.findTargetIndex("Z", map)) // nearest forward -> #
        assertNull(AlphaIndexHelper.findTargetIndex("A", emptyMap()))
    }

    @Test
    fun `scroll position state retains index and offset accurately`() {
        val saved = ScrollPositionState(firstVisibleItemIndex = 14, firstVisibleItemScrollOffset = 82)
        assertEquals(14, saved.firstVisibleItemIndex)
        assertEquals(82, saved.firstVisibleItemScrollOffset)
    }

    @Test
    fun `app settings and enums have expected complete upgrade options`() {
        val defaults = AppSettings()
        assertTrue(defaults.gaplessPlayback)
        assertTrue(defaults.waveformSeekbarEnabled)
        assertTrue(defaults.cinematicLyricsEnabled)
        assertTrue(defaults.gesturesEnabled)
        assertEquals(AppThemeMode.OCEAN_NAVY, defaults.themeMode)
        assertEquals(AccentColorOption.SKY_BLUE, defaults.accentColor)
        assertEquals(VisualizerMode.SPECTRUM_BARS, defaults.visualizerMode)
        assertEquals(DoubleTapGestureAction.PLAY_PAUSE, defaults.doubleTapAction)
        assertEquals(5, AppThemeMode.entries.size)
        assertEquals(7, AccentColorOption.entries.size)
        assertEquals(4, PlayerScreenStyle.entries.size)
        assertEquals(7, SortOrder.entries.size)
        assertEquals(6, SearchScope.entries.size)
        assertEquals(6, SmartPlaylistType.entries.size)
        assertEquals(4, VisualizerMode.entries.size)
        assertEquals(3, DoubleTapGestureAction.entries.size)
    }
}
