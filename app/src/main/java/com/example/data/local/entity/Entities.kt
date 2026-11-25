package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recently_played")
data class RecentlyPlayedEntity(
    @PrimaryKey val songId: Long,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "song_metadata")
data class SongMetadataEntity(
    @PrimaryKey val songId: Long,
    val customTitle: String? = null,
    val customArtist: String? = null,
    val customAlbum: String? = null,
    val customGenre: String? = null,
    val customYear: Int? = null,
    val customTrackNumber: Int? = null,
    val customArtworkUri: String? = null,
    val playCount: Int = 0,
    val totalListenTimeMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val replayGainDb: Float = 0f
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val lyricsText: String,
    val isSynced: Boolean = false,
    val offsetMs: Long = 0L,
    val sourcePath: String = ""
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val positionMs: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderPath: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "equalizer_presets")
data class EqualizerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bandLevelsCsv: String,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val trebleBoost: Int = 0,
    val isCustom: Boolean = true
)

@Entity(tableName = "saved_queues")
data class SavedQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_queue_items", primaryKeys = ["queueId", "orderIndex"])
data class SavedQueueItemEntity(
    val queueId: Long,
    val orderIndex: Int,
    val songId: Long
)
