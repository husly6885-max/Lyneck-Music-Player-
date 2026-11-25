package com.example.data.repository

import com.example.data.local.dao.MusicDao
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.EqualizerPresetEntity
import com.example.data.local.entity.ExcludedFolderEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.ListeningHistoryEntity
import com.example.data.local.entity.LyricsEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistSongEntity
import com.example.data.local.entity.RecentlyPlayedEntity
import com.example.data.local.entity.SavedQueueEntity
import com.example.data.local.entity.SongMetadataEntity
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.AudioBookmark
import com.example.data.model.AudioFileDetails
import com.example.data.model.DuplicateGroup
import com.example.data.model.Folder
import com.example.data.model.Genre
import com.example.data.model.ListeningStatsSummary
import com.example.data.model.PlaybackQueueInfo
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.data.model.SortOrder
import com.example.data.preferences.SettingsManager
import com.example.data.scanner.MediaScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val mediaScanner: MediaScanner,
    private val musicDao: MusicDao,
    private val settingsManager: SettingsManager
) {
    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    val favoriteSongIds: Flow<List<Long>> = musicDao.getFavoriteSongIds()
    val excludedFolders: Flow<List<String>> = musicDao.getExcludedFolders().map { list -> list.map { it.folderPath } }
    val allMetadata: Flow<List<SongMetadataEntity>> = musicDao.getAllSongMetadata()

    // Reactive enriched song list combining MediaStore scan + favorites + excluded folders + metadata overrides + sort order
    val allSongs: Flow<List<Song>> = combine(
        _rawSongs,
        favoriteSongIds,
        excludedFolders,
        allMetadata,
        settingsManager.settings
    ) { raw, favIds, excluded, metadataList, settings ->
        val favSet = favIds.toSet()
        val excludedSet = excluded.toSet()
        val metaMap = metadataList.associateBy { it.songId }

        val enriched = raw
            .filter { it.folderPath !in excludedSet }
            .map { song ->
                val meta = metaMap[song.id]
                song.copy(
                    isFavorite = song.id in favSet,
                    title = meta?.customTitle?.takeIf { it.isNotBlank() } ?: song.title,
                    artist = meta?.customArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                    album = meta?.customAlbum?.takeIf { it.isNotBlank() } ?: song.album,
                    genre = meta?.customGenre?.takeIf { it.isNotBlank() } ?: song.genre,
                    year = meta?.customYear ?: song.year,
                    trackNumber = meta?.customTrackNumber ?: song.trackNumber,
                    customArtUri = meta?.customArtworkUri,
                    playCount = meta?.playCount ?: 0,
                    replayGainDb = meta?.replayGainDb ?: 0f
                )
            }

        sortSongs(enriched, settings.sortOrder)
    }

    // All scanned songs without excluded folder filtering (used in Folder Manager)
    val rawScannedSongs = _rawSongs.asStateFlow()

    val favoriteSongs: Flow<List<Song>> = combine(allSongs, favoriteSongIds) { songs, favIds ->
        val songMap = songs.associateBy { it.id }
        favIds.mapNotNull { songMap[it] }
    }

    val recentlyPlayedSongIds: Flow<List<Long>> = musicDao.getRecentlyPlayedSongIds()
    val recentlyPlayedSongs: Flow<List<Song>> = combine(allSongs, recentlyPlayedSongIds) { songs, recentIds ->
        val songMap = songs.associateBy { it.id }
        recentIds.mapNotNull { songMap[it] }
    }

    val recentlyAddedSongs: Flow<List<Song>> = allSongs.map { songs ->
        songs.sortedByDescending { it.dateAdded }.take(100)
    }

    val mostPlayedSongs: Flow<List<Song>> = combine(allSongs, musicDao.getMostPlayedMetadata()) { songs, stats ->
        val songMap = songs.associateBy { it.id }
        stats.mapNotNull { stat -> songMap[stat.songId]?.copy(playCount = stat.playCount) }
    }

    val playlists: Flow<List<Playlist>> = musicDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            Playlist(id = entity.id, name = entity.name, createdAt = entity.createdAt)
        }
    }

    val customEqualizerPresets: Flow<List<EqualizerPresetEntity>> = musicDao.getCustomEqualizerPresets()

    val listeningStats: Flow<ListeningStatsSummary> = combine(
        allSongs,
        musicDao.getTotalListeningTimeMs(),
        musicDao.getTotalPlaysCount(),
        musicDao.getMostPlayedMetadata()
    ) { songs, totalTimeMs, totalPlays, mostPlayed ->
        val songMap = songs.associateBy { it.id }
        val topSongs = mostPlayed.mapNotNull { meta ->
            songMap[meta.songId]?.let { it to meta.playCount }
        }.take(10)

        val artistCounts = mutableMapOf<String, Int>()
        mostPlayed.forEach { meta ->
            val song = songMap[meta.songId]
            if (song != null) {
                artistCounts[song.artist] = (artistCounts[song.artist] ?: 0) + meta.playCount
            }
        }
        val topArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }

        ListeningStatsSummary(
            totalListeningTimeMs = totalTimeMs,
            totalPlays = totalPlays,
            topSongs = topSongs,
            topArtists = topArtists
        )
    }

    val listeningHistory: Flow<List<Pair<Song, Long>>> = combine(
        allSongs,
        musicDao.getListeningHistory()
    ) { songs, entries ->
        val songMap = songs.associateBy { it.id }
        entries.mapNotNull { entry ->
            songMap[entry.songId]?.let { it to entry.playedAt }
        }
    }

    val savedQueues: Flow<List<PlaybackQueueInfo>> = musicDao.getAllSavedQueues().map { list ->
        list.map { q ->
            PlaybackQueueInfo(
                id = q.id,
                name = q.name,
                isActive = q.isActive,
                currentIndex = q.currentIndex,
                positionMs = q.positionMs
            )
        }
    }

    private fun sortSongs(songs: List<Song>, sortOrder: SortOrder): List<Song> {
        return when (sortOrder) {
            SortOrder.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> songs.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
            SortOrder.ALBUM_ASC -> songs.sortedBy { it.album.lowercase() }
            SortOrder.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
            SortOrder.DURATION_DESC -> songs.sortedByDescending { it.duration }
            SortOrder.DURATION_ASC -> songs.sortedBy { it.duration }
        }
    }

    suspend fun scanSongs(): List<Song> {
        _isScanning.value = true
        val minDurMs = settingsManager.settings.value.minTrackDurationSec * 1000L
        val songs = mediaScanner.scanDeviceSongs(minDurMs)
        _rawSongs.value = songs
        _isScanning.value = false
        return songs
    }

    fun getAlbums(songs: List<Song>): List<Album> = mediaScanner.extractAlbums(songs)
    fun getArtists(songs: List<Song>): List<Artist> = mediaScanner.extractArtists(songs)
    fun getGenres(songs: List<Song>): List<Genre> = mediaScanner.extractGenres(songs)
    fun getFolders(songs: List<Song>): List<Folder> = mediaScanner.extractFolders(songs)
    fun findDuplicates(songs: List<Song>): List<DuplicateGroup> = mediaScanner.findDuplicates(songs)
    suspend fun getAudioFileDetails(song: Song): AudioFileDetails = mediaScanner.getAudioFileDetails(song)

    suspend fun toggleFavorite(songId: Long) {
        val isFav = musicDao.isFavorite(songId)
        if (isFav) musicDao.deleteFavorite(songId) else musicDao.insertFavorite(FavoriteEntity(songId = songId))
    }

    suspend fun recordSongPlayed(songId: Long, durationListenedMs: Long = 0L) {
        val now = System.currentTimeMillis()
        musicDao.insertRecentlyPlayed(RecentlyPlayedEntity(songId = songId, playedAt = now))
        val existing = musicDao.getSongMetadata(songId) ?: SongMetadataEntity(songId = songId)
        musicDao.upsertSongMetadata(
            existing.copy(
                playCount = existing.playCount + 1,
                totalListenTimeMs = existing.totalListenTimeMs + durationListenedMs,
                lastPlayedAt = now
            )
        )
        musicDao.insertListeningHistory(
            ListeningHistoryEntity(songId = songId, playedAt = now, durationMs = durationListenedMs)
        )
    }

    suspend fun addListeningDuration(songId: Long, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val existing = musicDao.getSongMetadata(songId) ?: SongMetadataEntity(songId = songId)
        musicDao.upsertSongMetadata(
            existing.copy(totalListenTimeMs = existing.totalListenTimeMs + deltaMs)
        )
        musicDao.insertListeningHistory(
            ListeningHistoryEntity(songId = songId, playedAt = System.currentTimeMillis(), durationMs = deltaMs)
        )
    }

    // Playlists
    suspend fun createPlaylist(name: String): Long = musicDao.insertPlaylist(PlaylistEntity(name = name.trim()))
    suspend fun renamePlaylist(playlistId: Long, newName: String) = musicDao.renamePlaylist(playlistId, newName.trim())
    suspend fun deletePlaylist(playlistId: Long) {
        musicDao.clearPlaylistSongs(playlistId)
        musicDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val nextOrder = musicDao.getMaxPlaylistOrder(playlistId) + 1
        musicDao.addSongToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                orderIndex = nextOrder,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        var order = musicDao.getMaxPlaylistOrder(playlistId) + 1
        val entries = songIds.map { sid ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = sid,
                orderIndex = order++,
                addedAt = System.currentTimeMillis()
            )
        }
        musicDao.addSongsToPlaylist(entries)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        musicDao.removeSongFromPlaylist(playlistId, songId)

    suspend fun reorderPlaylistSongs(playlistId: Long, orderedSongIds: List<Long>) =
        musicDao.replacePlaylistOrder(playlistId, orderedSongIds)

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return combine(allSongs, musicDao.getSongsForPlaylist(playlistId)) { songs, ids ->
            val songMap = songs.associateBy { it.id }
            ids.mapNotNull { songMap[it] }
        }
    }

    fun getPlaylistSongCount(playlistId: Long): Flow<Int> = musicDao.getPlaylistSongCount(playlistId)

    // Metadata & Artwork overrides
    suspend fun saveCustomSongMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int
    ) {
        val existing = musicDao.getSongMetadata(songId) ?: SongMetadataEntity(songId = songId)
        musicDao.upsertSongMetadata(
            existing.copy(
                customTitle = title.trim(),
                customArtist = artist.trim(),
                customAlbum = album.trim(),
                customGenre = genre.trim(),
                customYear = year,
                customTrackNumber = trackNumber
            )
        )
    }

    suspend fun saveCustomArtwork(songId: Long, artworkUri: String?) {
        val existing = musicDao.getSongMetadata(songId) ?: SongMetadataEntity(songId = songId)
        musicDao.upsertSongMetadata(existing.copy(customArtworkUri = artworkUri))
    }

    // Lyrics
    fun observeLyrics(songId: Long): Flow<LyricsEntity?> = musicDao.observeLyrics(songId)
    suspend fun getLyrics(songId: Long): LyricsEntity? = musicDao.getLyrics(songId)
    suspend fun saveLyrics(songId: Long, text: String, isSynced: Boolean, offsetMs: Long, sourcePath: String = "") {
        musicDao.upsertLyrics(
            LyricsEntity(
                songId = songId,
                lyricsText = text,
                isSynced = isSynced,
                offsetMs = offsetMs,
                sourcePath = sourcePath
            )
        )
    }

    // Bookmarks
    fun getBookmarksForSong(songId: Long): Flow<List<AudioBookmark>> =
        musicDao.getBookmarksForSong(songId).map { list ->
            list.map { AudioBookmark(it.id, it.songId, it.positionMs, it.label, it.createdAt) }
        }

    suspend fun addBookmark(songId: Long, positionMs: Long, label: String): Long =
        musicDao.insertBookmark(BookmarkEntity(songId = songId, positionMs = positionMs, label = label))

    suspend fun deleteBookmark(bookmarkId: Long) = musicDao.deleteBookmark(bookmarkId)

    // Excluded Folders
    suspend fun excludeFolder(path: String) = musicDao.addExcludedFolder(ExcludedFolderEntity(folderPath = path))
    suspend fun includeFolder(path: String) = musicDao.removeExcludedFolder(path)

    // Custom EQ Presets
    suspend fun saveEqualizerPreset(
        name: String,
        bandLevelsCsv: String,
        bassBoost: Int,
        virtualizer: Int,
        trebleBoost: Int
    ): Long = musicDao.insertEqualizerPreset(
        EqualizerPresetEntity(
            name = name.trim(),
            bandLevelsCsv = bandLevelsCsv,
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            trebleBoost = trebleBoost,
            isCustom = true
        )
    )

    suspend fun deleteEqualizerPreset(id: Long) = musicDao.deleteEqualizerPreset(id)

    // Multi-Queue Persistence
    suspend fun ensureDefaultQueueExists(): SavedQueueEntity {
        val all = musicDao.getAllSavedQueuesSync()
        val active = all.firstOrNull { it.isActive } ?: all.firstOrNull()
        if (active != null) {
            if (!active.isActive) musicDao.setActiveQueue(active.id)
            return active.copy(isActive = true)
        }
        val id = musicDao.upsertSavedQueue(SavedQueueEntity(name = "Main Queue", isActive = true))
        return SavedQueueEntity(id = id, name = "Main Queue", isActive = true)
    }

    suspend fun createSavedQueue(name: String): SavedQueueEntity {
        val id = musicDao.upsertSavedQueue(SavedQueueEntity(name = name.trim(), isActive = false))
        return SavedQueueEntity(id = id, name = name.trim(), isActive = false)
    }

    suspend fun switchActiveQueue(queueId: Long): Pair<SavedQueueEntity?, List<Long>> {
        musicDao.setActiveQueue(queueId)
        val q = musicDao.getActiveQueueSync()
        val songIds = musicDao.getQueueSongIdsSync(queueId)
        return q to songIds
    }

    suspend fun deleteSavedQueue(queueId: Long) {
        musicDao.clearQueueItems(queueId)
        musicDao.deleteSavedQueue(queueId)
    }

    suspend fun saveQueueState(queueId: Long, songIds: List<Long>, currentIndex: Int, positionMs: Long) {
        musicDao.replaceQueueItems(queueId, songIds)
        musicDao.updateQueueCursor(queueId, currentIndex, positionMs)
    }

    suspend fun getQueueSongIds(queueId: Long): List<Long> = musicDao.getQueueSongIdsSync(queueId)

    suspend fun purgeDeletedSongs(songIds: List<Long>) {
        for (id in songIds) {
            musicDao.purgeDeletedSong(id)
        }
        _rawSongs.value = _rawSongs.value.filterNot { it.id in songIds }
    }
}
