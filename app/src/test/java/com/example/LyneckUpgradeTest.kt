package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LyneckDatabase
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.EqualizerPresetEntity
import com.example.data.local.entity.ExcludedFolderEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.LyricsEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistSongEntity
import com.example.data.local.entity.SavedQueueEntity
import com.example.data.lyrics.LyricsManager
import com.example.data.model.Song
import com.example.data.playlist.M3uPlaylistManager
import com.example.data.preferences.AccentColorOption
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PlayerScreenStyle
import com.example.data.preferences.SettingsManager
import com.example.data.scanner.MediaScanner
import com.example.data.storage.FileManager
import com.example.playback.DeviceVolumeState
import com.example.playback.LyneckAudioProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyneckUpgradeTest {

    private lateinit var context: Context
    private lateinit var db: LyneckDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LyneckDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `lyrics manager parses synced LRC and strips inline karaoke tags`() {
        val manager = LyricsManager(context)
        val raw = """
            [ti:Neon Echoes]
            [00:05.50]First <00:05.80>line <00:06.10>here
            [00:12.00][01:02.25]Chorus line
        """.trimIndent()

        val parsed = manager.parse(raw)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(5500L, parsed.lines[0].timestampMs)
        assertEquals("First line here", parsed.lines[0].text)
        assertEquals(12000L, parsed.lines[1].timestampMs)
        assertEquals("Chorus line", parsed.lines[1].text)
        assertEquals(62250L, parsed.lines[2].timestampMs)
        assertEquals("Chorus line", parsed.lines[2].text)
    }

    @Test
    fun `lyrics manager handles unsynced plain lyrics`() {
        val manager = LyricsManager(context)
        val parsed = manager.parse("Line one\nLine two")
        assertFalse(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals("Line one", parsed.lines[0].text)
        assertEquals("Line two", parsed.lines[1].text)
    }

    @Test
    fun `settings manager persists values and round-trips JSON`() {
        val settingsManager = SettingsManager(context)
        settingsManager.update {
            it.copy(
                themeMode = AppThemeMode.AMOLED,
                accentColor = AccentColorOption.EMERALD_MINT,
                playerStyle = PlayerScreenStyle.VINYL,
                crossfadeSeconds = 6,
                playbackSpeed = 1.25f,
                audioBalance = -0.5f,
                volumeNormalization = true,
                replayGainEnabled = true,
                skipSilence = true
            )
        }
        val exported = settingsManager.exportToJson()

        // Reset and restore
        settingsManager.update {
            it.copy(
                themeMode = AppThemeMode.LIGHT,
                accentColor = AccentColorOption.CRIMSON_ROSE,
                crossfadeSeconds = 0,
                playbackSpeed = 1.0f,
                audioBalance = 0f,
                volumeNormalization = false
            )
        }
        settingsManager.importFromJson(exported)

        val restored = settingsManager.settings.value
        assertEquals(AppThemeMode.AMOLED, restored.themeMode)
        assertEquals(AccentColorOption.EMERALD_MINT, restored.accentColor)
        assertEquals(PlayerScreenStyle.VINYL, restored.playerStyle)
        assertEquals(6, restored.crossfadeSeconds)
        assertEquals(1.25f, restored.playbackSpeed, 0.01f)
        assertEquals(-0.5f, restored.audioBalance, 0.01f)
        assertTrue(restored.volumeNormalization)
        assertTrue(restored.replayGainEnabled)
        assertTrue(restored.skipSilence)
    }

    @Test
    fun `dao playlist reorder multi-queue and purgeDeletedSong work cleanly`() = runTest {
        val dao = db.musicDao()

        val plId = dao.insertPlaylist(PlaylistEntity(name = "Roadtrip"))
        dao.renamePlaylist(plId, "Night Drive")
        val playlists = dao.getAllPlaylists().first()
        assertEquals("Night Drive", playlists.first().name)

        dao.addSongsToPlaylist(
            listOf(
                PlaylistSongEntity(playlistId = plId, songId = 10L, orderIndex = 0),
                PlaylistSongEntity(playlistId = plId, songId = 20L, orderIndex = 1),
                PlaylistSongEntity(playlistId = plId, songId = 30L, orderIndex = 2)
            )
        )
        dao.replacePlaylistOrder(plId, listOf(30L, 10L, 20L))
        assertEquals(listOf(30L, 10L, 20L), dao.getSongsForPlaylist(plId).first())

        // Bookmarks, favorites, lyrics, excluded folder, custom EQ, saved queue
        dao.insertFavorite(FavoriteEntity(songId = 20L))
        dao.insertBookmark(BookmarkEntity(songId = 20L, positionMs = 45000L, label = "Solo"))
        dao.upsertLyrics(LyricsEntity(songId = 20L, lyricsText = "Hello", isSynced = false))
        dao.addExcludedFolder(ExcludedFolderEntity("/storage/emulated/0/Ringtones"))
        dao.insertEqualizerPreset(EqualizerPresetEntity(name = "Studio", bandLevelsCsv = "200,0,100,200,300"))

        val q1 = dao.upsertSavedQueue(SavedQueueEntity(name = "Main", isActive = true))
        val q2 = dao.upsertSavedQueue(SavedQueueEntity(name = "Gym", isActive = false))
        dao.replaceQueueItems(q1, listOf(10L, 20L, 30L))
        dao.setActiveQueue(q2)
        assertEquals(q2, dao.getActiveQueueSync()?.id)

        // Purge deleted song 20L everywhere
        dao.purgeDeletedSong(20L)
        assertFalse(dao.isFavorite(20L))
        assertEquals(listOf(30L, 10L), dao.getSongsForPlaylist(plId).first())
        assertEquals(listOf(10L, 30L), dao.getQueueSongIdsSync(q1))
        assertNull(dao.getLyrics(20L))
        assertTrue(dao.getBookmarksForSong(20L).first().isEmpty())
    }

    @Test
    fun `media scanner detects duplicate tracks accurately`() {
        val scanner = MediaScanner(context)
        val songs = listOf(
            Song(1L, "Midnight City", "M83", "Hurry Up", 243000L, Uri.parse("content://1"), 1L, size = 5_000_000L),
            Song(2L, "midnight city ", "m83", "Hits", 243500L, Uri.parse("content://2"), 2L, size = 5_200_000L),
            Song(3L, "Unique Track", "Artist B", "Album B", 180000L, Uri.parse("content://3"), 3L, size = 3_100_000L)
        )
        val dups = scanner.findDuplicates(songs)
        assertEquals(1, dups.size)
        assertEquals(2, dups.first().songs.size)
    }

    @Test
    fun `m3u export import and full backup round-trip restore data`() = runTest {
        val dao = db.musicDao()
        val settingsManager = SettingsManager(context)
        val m3uManager = M3uPlaylistManager(context, dao, settingsManager)

        val songs = listOf(
            Song(101L, "Starlight", "Muse", "Black Holes", 240000L, Uri.parse("content://101"), 1L, dataPath = "/music/starlight.mp3"),
            Song(102L, "Time", "Hans Zimmer", "Inception", 275000L, Uri.parse("content://102"), 2L, dataPath = "/music/time.mp3")
        )

        val m3uFile = File(context.cacheDir, "test_export.m3u")
        val exportRes = m3uManager.exportPlaylistToM3u("SciFi Mix", songs, Uri.fromFile(m3uFile))
        assertTrue(exportRes.isSuccess)
        assertTrue(m3uFile.readText().contains("#PLAYLIST:SciFi Mix"))

        val importRes = m3uManager.importPlaylistFromM3u(Uri.fromFile(m3uFile), "fallback.m3u", songs)
        assertTrue(importRes.isSuccess)
        val (importedName, importedCount) = importRes.getOrThrow()
        assertEquals("SciFi Mix", importedName)
        assertEquals(2, importedCount)

        dao.insertFavorite(FavoriteEntity(songId = 101L))
        val backupFile = File(context.cacheDir, "lyneck_test_backup.json")
        assertTrue(m3uManager.exportFullBackupJson(Uri.fromFile(backupFile), songs).isSuccess)

        dao.deleteFavorite(101L)
        val restoreRes = m3uManager.importFullBackupJson(Uri.fromFile(backupFile), songs)
        assertTrue(restoreRes.isSuccess)
        assertTrue(dao.isFavorite(101L))
    }

    @Test
    fun `file manager verifies permanent file removal accurately`() = runTest {
        val fileManager = FileManager(context)
        val tempFile = File(context.cacheDir, "temp_track_delete_test.mp3").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val song = Song(
            id = 999L,
            title = "Temp Track",
            artist = "Tester",
            album = "Cache",
            duration = 15000L,
            contentUri = Uri.fromFile(tempFile),
            albumId = 1L,
            dataPath = tempFile.absolutePath
        )

        // While physical file still exists on disk, verifySongsDeleted returns false
        assertFalse(fileManager.verifySongsDeleted(listOf(song)))

        // Once physical file is removed and URI doesn't exist in MediaStore, returns true
        assertTrue(tempFile.delete())
        assertTrue(fileManager.verifySongsDeleted(listOf(song)))
    }

    @Test
    fun `device volume state percentage calculation is accurate`() {
        val half = DeviceVolumeState(currentStep = 8, maxStep = 16, ratio = 0.5f)
        assertEquals(50, half.percentage)
        val full = DeviceVolumeState(currentStep = 15, maxStep = 15, ratio = 1.0f)
        assertEquals(100, full.percentage)
    }

    @Test
    fun `audio processor applies real stereo balance on PCM16 samples`() {
        val processor = LyneckAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.balance = 1.0f // Full Right: Left channel gain = 0f, Right channel gain = 1f

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(10000.toShort()) // Left
        input.putShort(10000.toShort()) // Right
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertNotNull(output)
        val leftOut = output.getShort().toInt()
        val rightOut = output.getShort().toInt()
        assertEquals(0, leftOut)
        assertEquals(10000, rightOut)
    }
}
