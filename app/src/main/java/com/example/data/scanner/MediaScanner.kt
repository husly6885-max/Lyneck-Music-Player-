package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.AudioFileDetails
import com.example.data.model.DuplicateGroup
import com.example.data.model.Folder
import com.example.data.model.Genre
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv", "ts", "mpg", "mpeg"
        )

        fun isAudioOnlyTrack(mimeType: String?, filePathOrTitle: String?): Boolean {
            val mime = mimeType?.trim()?.lowercase() ?: ""
            if (mime.startsWith("video/")) return false
            val ext = filePathOrTitle?.substringAfterLast('.', "")?.trim()?.lowercase() ?: ""
            if (ext in VIDEO_EXTENSIONS) return false
            return true
        }
    }

    @Suppress("DEPRECATION")
    suspend fun scanDeviceSongs(minDurationMs: Long = 10_000L): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val genreLookup = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            loadLegacyGenreMap()
        } else {
            emptyMap()
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.coerceAtLeast(1000L).toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                collection,
                projectionList.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: ""
                    val rawArtist = cursor.getString(artistCol) ?: ""
                    val rawAlbum = cursor.getString(albumCol) ?: ""
                    val duration = cursor.getLong(durationCol)
                    val dataPath = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val albumId = cursor.getLong(albumIdCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val year = if (yearCol >= 0) cursor.getInt(yearCol) else 0
                    val rawTrack = if (trackCol >= 0) cursor.getInt(trackCol) else 0
                    val trackNumber = if (rawTrack > 1000) rawTrack % 1000 else rawTrack
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "audio/mpeg" else "audio/mpeg"

                    // Never treat video tracks as background music files
                    if (!isAudioOnlyTrack(mimeType, dataPath.ifBlank { rawTitle })) continue

                    val rawGenre = if (genreCol >= 0) cursor.getString(genreCol) else genreLookup[id]

                    val cleanArtist = if (rawArtist.trim() == "<unknown>" || rawArtist.isBlank()) {
                        "Unknown Artist"
                    } else rawArtist.trim()

                    val cleanAlbum = if (rawAlbum.trim() == "<unknown>" || rawAlbum.isBlank()) {
                        "Unknown Album"
                    } else rawAlbum.trim()

                    val cleanTitle = if (rawTitle.isBlank()) {
                        if (dataPath.isNotBlank()) File(dataPath).nameWithoutExtension else "Song $id"
                    } else rawTitle.trim()

                    val cleanGenre = if (rawGenre.isNullOrBlank() || rawGenre.trim() == "<unknown>") {
                        "Unknown Genre"
                    } else rawGenre.trim()

                    val folderPath = if (dataPath.isNotBlank()) File(dataPath).parent ?: "" else ""

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
                            dateAdded = dateAdded,
                            genre = cleanGenre,
                            year = year,
                            trackNumber = trackNumber,
                            mimeType = mimeType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songsList
    }

    @Suppress("DEPRECATION")
    private fun loadLegacyGenreMap(): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        try {
            val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                genresUri,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null
            )?.use { gCursor ->
                val idCol = gCursor.getColumnIndex(MediaStore.Audio.Genres._ID)
                val nameCol = gCursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
                while (gCursor.moveToNext()) {
                    if (idCol < 0 || nameCol < 0) continue
                    val genreId = gCursor.getLong(idCol)
                    val genreName = gCursor.getString(nameCol) ?: continue
                    if (genreName.isBlank()) continue
                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    context.contentResolver.query(
                        membersUri,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null,
                        null,
                        null
                    )?.use { mCursor ->
                        val audioIdCol = mCursor.getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (mCursor.moveToNext()) {
                            if (audioIdCol >= 0) {
                                map[mCursor.getLong(audioIdCol)] = genreName
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    fun extractAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.albumId to it.album }
            .map { (key, albumSongs) ->
                val (albumId, albumName) = key
                val first = albumSongs.firstOrNull()
                Album(
                    id = albumId,
                    name = albumName,
                    artist = first?.artist ?: "Unknown Artist",
                    songCount = albumSongs.size,
                    albumArtUri = first?.albumArtUri
                        ?: Uri.parse("content://media/external/audio/albumart/$albumId"),
                    year = albumSongs.maxOfOrNull { it.year } ?: 0
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

    fun extractGenres(songs: List<Song>): List<Genre> {
        return songs.groupBy { it.genre }
            .map { (genreName, genreSongs) ->
                Genre(name = genreName, songCount = genreSongs.size)
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

    fun findDuplicates(songs: List<Song>): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val byMetadata = songs.groupBy {
            "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}|${it.duration / 2000}"
        }
        byMetadata.forEach { (key, list) ->
            if (list.size > 1) {
                groups.add(
                    DuplicateGroup(
                        key = "meta_$key",
                        reason = "Same title, artist & duration",
                        songs = list
                    )
                )
            }
        }
        val alreadyGroupedIds = groups.flatMap { it.songs.map { s -> s.id } }.toSet()
        val bySizeAndDuration = songs
            .filter { it.id !in alreadyGroupedIds && it.size > 0 }
            .groupBy { "${it.size}|${it.duration / 1000}" }
        bySizeAndDuration.forEach { (key, list) ->
            if (list.size > 1) {
                groups.add(
                    DuplicateGroup(
                        key = "size_$key",
                        reason = "Identical file size & length",
                        songs = list
                    )
                )
            }
        }
        return groups
    }

    suspend fun getAudioFileDetails(song: Song): AudioFileDetails = withContext(Dispatchers.IO) {
        var bitrateKbps = 0
        var sampleRateHz = 0
        var channels = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, song.contentUri)
            bitrateKbps = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            }
            val tracks = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.toIntOrNull() ?: 0
            channels = if (tracks > 0) 2 else 2
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        val exists = if (song.dataPath.isNotBlank()) File(song.dataPath).exists() else true
        AudioFileDetails(
            song = song,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            channels = channels,
            fileExists = exists
        )
    }
}
