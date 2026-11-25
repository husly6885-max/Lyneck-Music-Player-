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
import com.example.data.preferences.PlayerScreenStyle
import com.example.data.scanner.MediaScanner
import com.example.playback.DeviceVolumeState
import com.example.playback.LyneckAudioProcessor
import com.example.ui.ScrollPositionState
import com.example.ui.components.AlphaIndexHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LyneckCoreUnitTest {

    @Test
    fun `lyrics parser extracts multi-timestamps and strips karaoke inline tags`() {
        val raw = """
            [ti:Neon Echoes]
            [00:05.50]First <00:05.80>line <00:06.10>here
            [00:12.00][01:02.25]Chorus line
        """.trimIndent()

        val parsed = LyricsManager.parseRaw(raw)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(5500L, parsed.lines[0].timestampMs)
        assertEquals("First line here", parsed.lines[0].text)
        assertEquals(12000L, parsed.lines[1].timestampMs)
        assertEquals("Chorus line", parsed.lines[1].text)
        assertEquals(62250L, parsed.lines[2].timestampMs)
    }

    @Test
    fun `lyrics parser handles plain text lyrics`() {
        val parsed = LyricsManager.parseRaw("Verse one\nChorus two")
        assertFalse(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals("Verse one", parsed.lines[0].text)
        assertEquals("Chorus two", parsed.lines[1].text)
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
        assertEquals(AppThemeMode.OCEAN_NAVY, defaults.themeMode)
        assertEquals(AccentColorOption.SKY_BLUE, defaults.accentColor)
        assertEquals(5, AppThemeMode.entries.size)
        assertEquals(7, AccentColorOption.entries.size)
        assertEquals(4, PlayerScreenStyle.entries.size)
        assertEquals(7, SortOrder.entries.size)
        assertEquals(6, SearchScope.entries.size)
        assertEquals(4, SmartPlaylistType.entries.size)
    }
}
