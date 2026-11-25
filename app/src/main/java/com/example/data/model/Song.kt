package com.example.data.model

import android.net.Uri
import java.util.Locale

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val contentUri: Uri,
    val albumId: Long,
    val dataPath: String = "",
    val folderPath: String = "",
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false,
    val genre: String = "Unknown Genre",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val mimeType: String = "audio/mpeg",
    val playCount: Int = 0,
    val customArtUri: String? = null,
    val replayGainDb: Float = 0f
) {
    val albumArtUri: Uri
        get() = if (!customArtUri.isNullOrBlank()) {
            Uri.parse(customArtUri)
        } else {
            Uri.parse("content://media/external/audio/albumart/$albumId")
        }

    val formattedDuration: String
        get() {
            val totalSeconds = (duration / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            val mb = size / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.2f MB", mb)
        }
}
