package com.example.data.repository

import com.example.data.local.dao.MusicDao
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistSongEntity
import com.example.data.local.entity.RecentlyPlayedEntity
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.Folder
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.data.scanner.MediaScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val mediaScanner: MediaScanner,
    private val musicDao: MusicDao
) {
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs = _allSongs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Favorite IDs from Room
    val favoriteSongIds: Flow<List<Long>> = musicDao.getFavoriteSongIds()

    // Reactive favorites list (Song objects)
    val favoriteSongs: Flow<List<Song>> = combine(allSongs, favoriteSongIds) { songs, favIds ->
        val favSet = favIds.toSet()
        songs.filter { it.id in favSet }.map { it.copy(isFavorite = true) }
    }

    // Recently played
    val recentlyPlayedSongIds: Flow<List<Long>> = musicDao.getRecentlyPlayedSongIds()
    val recentlyPlayedSongs: Flow<List<Song>> = combine(allSongs, recentlyPlayedSongIds) { songs, recentIds ->
        val songMap = songs.associateBy { it.id }
        recentIds.mapNotNull { songMap[it] }
    }

    // Playlists
    val playlists: Flow<List<Playlist>> = musicDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            Playlist(
                id = entity.id,
                name = entity.name,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun scanSongs(): List<Song> {
        _isScanning.value = true
        val songs = mediaScanner.scanDeviceSongs()
        _allSongs.value = songs
        _isScanning.value = false
        return songs
    }

    fun getAlbums(): List<Album> {
        return mediaScanner.extractAlbums(_allSongs.value)
    }

    fun getArtists(): List<Artist> {
        return mediaScanner.extractArtists(_allSongs.value)
    }

    fun getFolders(): List<Folder> {
        return mediaScanner.extractFolders(_allSongs.value)
    }

    suspend fun toggleFavorite(songId: Long) {
        val isFav = musicDao.isFavorite(songId)
        if (isFav) {
            musicDao.deleteFavorite(songId)
        } else {
            musicDao.insertFavorite(FavoriteEntity(songId = songId))
        }
    }

    suspend fun markRecentlyPlayed(songId: Long) {
        musicDao.insertRecentlyPlayed(RecentlyPlayedEntity(songId = songId, playedAt = System.currentTimeMillis()))
    }

    suspend fun createPlaylist(name: String): Long {
        return musicDao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        musicDao.clearPlaylistSongs(playlistId)
        musicDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        musicDao.addSongToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        musicDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return combine(allSongs, musicDao.getSongsForPlaylist(playlistId)) { songs, ids ->
            val songMap = songs.associateBy { it.id }
            ids.mapNotNull { songMap[it] }
        }
    }

    fun getPlaylistSongCount(playlistId: Long): Flow<Int> {
        return musicDao.getPlaylistSongCount(playlistId)
    }
}
