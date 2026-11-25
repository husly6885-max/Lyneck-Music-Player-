package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
import com.example.data.local.entity.SavedQueueItemEntity
import com.example.data.local.entity.SongMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Favorites
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun getFavoriteSongIds(): Flow<List<Long>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAllFavoritesSync(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun deleteFavorite(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Playlist Songs
    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC, addedAt ASC")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Long>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC, addedAt ASC")
    suspend fun getPlaylistSongsSync(playlistId: Long): List<PlaylistSongEntity>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getMaxPlaylistOrder(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(playlistSongs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    suspend fun removeSongFromAllPlaylists(songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun getPlaylistSongCount(playlistId: Long): Flow<Int>

    @Transaction
    suspend fun replacePlaylistOrder(playlistId: Long, orderedSongIds: List<Long>) {
        clearPlaylistSongs(playlistId)
        val entities = orderedSongIds.mapIndexed { index, songId ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                orderIndex = index,
                addedAt = System.currentTimeMillis()
            )
        }
        if (entities.isNotEmpty()) {
            addSongsToPlaylist(entities)
        }
    }

    // Recently Played
    @Query("SELECT songId FROM recently_played ORDER BY playedAt DESC LIMIT 100")
    fun getRecentlyPlayedSongIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayed(recent: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE songId = :songId")
    suspend fun deleteRecentlyPlayed(songId: Long)

    @Query("DELETE FROM recently_played")
    suspend fun clearRecentlyPlayed()

    // Song Metadata & Play Stats
    @Query("SELECT * FROM song_metadata")
    fun getAllSongMetadata(): Flow<List<SongMetadataEntity>>

    @Query("SELECT * FROM song_metadata WHERE songId = :songId")
    suspend fun getSongMetadata(songId: Long): SongMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongMetadata(metadata: SongMetadataEntity)

    @Query("DELETE FROM song_metadata WHERE songId = :songId")
    suspend fun deleteSongMetadata(songId: Long)

    @Query("SELECT * FROM song_metadata WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
    fun getMostPlayedMetadata(): Flow<List<SongMetadataEntity>>

    // Lyrics
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    fun observeLyrics(songId: Long): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: Long): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyrics(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun deleteLyrics(songId: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE songId = :songId ORDER BY positionMs ASC")
    fun getBookmarksForSong(songId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("DELETE FROM bookmarks WHERE songId = :songId")
    suspend fun deleteBookmarksForSong(songId: Long)

    // Listening History
    @Insert
    suspend fun insertListeningHistory(entry: ListeningHistoryEntity)

    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC LIMIT 200")
    fun getListeningHistory(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_history")
    fun getTotalListeningTimeMs(): Flow<Long>

    @Query("SELECT COUNT(*) FROM listening_history")
    fun getTotalPlaysCount(): Flow<Int>

    @Query("DELETE FROM listening_history WHERE songId = :songId")
    suspend fun deleteHistoryForSong(songId: Long)

    @Query("DELETE FROM listening_history")
    suspend fun clearListeningHistory()

    // Excluded Folders
    @Query("SELECT * FROM excluded_folders ORDER BY folderPath ASC")
    fun getExcludedFolders(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT * FROM excluded_folders")
    suspend fun getExcludedFoldersSync(): List<ExcludedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addExcludedFolder(folder: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE folderPath = :folderPath")
    suspend fun removeExcludedFolder(folderPath: String)

    // Custom Equalizer Presets
    @Query("SELECT * FROM equalizer_presets ORDER BY name ASC")
    fun getCustomEqualizerPresets(): Flow<List<EqualizerPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEqualizerPreset(preset: EqualizerPresetEntity): Long

    @Query("DELETE FROM equalizer_presets WHERE id = :presetId")
    suspend fun deleteEqualizerPreset(presetId: Long)

    // Multiple Queues
    @Query("SELECT * FROM saved_queues ORDER BY id ASC")
    fun getAllSavedQueues(): Flow<List<SavedQueueEntity>>

    @Query("SELECT * FROM saved_queues ORDER BY id ASC")
    suspend fun getAllSavedQueuesSync(): List<SavedQueueEntity>

    @Query("SELECT * FROM saved_queues WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveQueueSync(): SavedQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedQueue(queue: SavedQueueEntity): Long

    @Query("UPDATE saved_queues SET isActive = CASE WHEN id = :queueId THEN 1 ELSE 0 END")
    suspend fun setActiveQueue(queueId: Long)

    @Query("UPDATE saved_queues SET currentIndex = :index, positionMs = :positionMs, updatedAt = :updatedAt WHERE id = :queueId")
    suspend fun updateQueueCursor(queueId: Long, index: Int, positionMs: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM saved_queues WHERE id = :queueId")
    suspend fun deleteSavedQueue(queueId: Long)

    @Query("SELECT songId FROM saved_queue_items WHERE queueId = :queueId ORDER BY orderIndex ASC")
    suspend fun getQueueSongIdsSync(queueId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM saved_queue_items WHERE queueId = :queueId")
    fun observeQueueItemCount(queueId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<SavedQueueItemEntity>)

    @Query("DELETE FROM saved_queue_items WHERE queueId = :queueId")
    suspend fun clearQueueItems(queueId: Long)

    @Query("DELETE FROM saved_queue_items WHERE songId = :songId")
    suspend fun removeSongFromAllQueues(songId: Long)

    @Transaction
    suspend fun replaceQueueItems(queueId: Long, songIds: List<Long>) {
        clearQueueItems(queueId)
        if (songIds.isNotEmpty()) {
            insertQueueItems(
                songIds.mapIndexed { idx, sid ->
                    SavedQueueItemEntity(queueId = queueId, orderIndex = idx, songId = sid)
                }
            )
        }
    }

    @Transaction
    suspend fun purgeDeletedSong(songId: Long) {
        deleteFavorite(songId)
        removeSongFromAllPlaylists(songId)
        deleteRecentlyPlayed(songId)
        deleteSongMetadata(songId)
        deleteLyrics(songId)
        deleteBookmarksForSong(songId)
        deleteHistoryForSong(songId)
        removeSongFromAllQueues(songId)
    }
}
