package com.example.data.model

import android.net.Uri

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val albumArtUri: Uri,
    val year: Int = 0
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

data class Genre(
    val name: String,
    val songCount: Int
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SmartPlaylistType(val title: String, val subtitle: String) {
    RECENTLY_PLAYED("Recently Played", "Your latest played tracks"),
    MOST_PLAYED("Most Played", "Your all-time top tracks"),
    RECENTLY_ADDED("Recently Added", "Newest audio files on device"),
    FAVORITES("Favorites", "Songs you loved")
}

enum class SortOrder(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST_ASC("Artist (A-Z)"),
    ALBUM_ASC("Album (A-Z)"),
    DATE_ADDED_DESC("Date Added (Newest)"),
    DURATION_DESC("Duration (Longest)"),
    DURATION_ASC("Duration (Shortest)")
}

enum class SearchScope(val label: String) {
    ALL("All"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    GENRE("Genre"),
    FOLDER("Folder")
}

data class DuplicateGroup(
    val key: String,
    val reason: String,
    val songs: List<Song>
)

data class AudioBookmark(
    val id: Long,
    val songId: Long,
    val positionMs: Long,
    val label: String,
    val createdAt: Long
)

data class PlaybackQueueInfo(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val currentIndex: Int,
    val positionMs: Long,
    val itemCount: Int = 0
)

data class ListeningStatsSummary(
    val totalListeningTimeMs: Long = 0L,
    val totalPlays: Int = 0,
    val topSongs: List<Pair<Song, Int>> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList()
)

data class AudioFileDetails(
    val song: Song,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val channels: Int = 0,
    val fileExists: Boolean = true
)
