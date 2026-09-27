package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.Folder
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {

    suspend fun scanDeviceSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        // Select valid music files with duration > 10 seconds to avoid short sound effects
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: "Unknown Title"
                    val rawArtist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val rawAlbum = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durationCol)
                    val dataPath = cursor.getString(dataCol) ?: ""
                    val albumId = cursor.getLong(albumIdCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol)

                    val cleanArtist = if (rawArtist.trim() == "<unknown>" || rawArtist.isBlank()) {
                        "Unknown Artist"
                    } else {
                        rawArtist.trim()
                    }

                    val cleanAlbum = if (rawAlbum.trim() == "<unknown>" || rawAlbum.isBlank()) {
                        "Unknown Album"
                    } else {
                        rawAlbum.trim()
                    }

                    val cleanTitle = if (rawTitle.isBlank()) {
                        if (dataPath.isNotBlank()) File(dataPath).nameWithoutExtension else "Song $id"
                    } else {
                        rawTitle.trim()
                    }

                    val folderPath = if (dataPath.isNotBlank()) {
                        File(dataPath).parent ?: ""
                    } else {
                        ""
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    songsList.add(
                        Song(
                            id = id,
                            title = cleanTitle,
                            artist = cleanArtist,
                            album = cleanAlbum,
                            duration = duration,
                            contentUri = contentUri,
                            albumId = albumId,
                            dataPath = dataPath,
                            folderPath = folderPath,
                            size = size,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songsList
    }

    fun extractAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.albumId to it.album }
            .map { (key, albumSongs) ->
                val (albumId, albumName) = key
                val artist = albumSongs.firstOrNull()?.artist ?: "Unknown Artist"
                Album(
                    id = albumId,
                    name = albumName,
                    artist = artist,
                    songCount = albumSongs.size,
                    albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun extractArtists(songs: List<Song>): List<Artist> {
        return songs.groupBy { it.artist }
            .map { (artistName, artistSongs) ->
                val albumCount = artistSongs.map { it.albumId }.distinct().size
                Artist(
                    name = artistName,
                    songCount = artistSongs.size,
                    albumCount = albumCount
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun extractFolders(songs: List<Song>): List<Folder> {
        return songs.filter { it.folderPath.isNotBlank() }
            .groupBy { it.folderPath }
            .map { (path, folderSongs) ->
                val folderName = File(path).name.ifBlank { path }
                Folder(
                    path = path,
                    name = folderName,
                    songCount = folderSongs.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }
}
