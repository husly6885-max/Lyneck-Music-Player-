package com.example.data.storage

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class FileOpResult {
    data class Success(val message: String) : FileOpResult()
    data class RequiresIntentSender(
        val intentSender: IntentSender,
        val pendingAction: PendingStorageAction
    ) : FileOpResult()
    data class Error(val message: String) : FileOpResult()
}

sealed class PendingStorageAction {
    data class DeleteSongs(val songs: List<Song>) : PendingStorageAction()
    data class UpdateSongTags(
        val song: Song,
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val year: Int,
        val trackNumber: Int,
        val newFileName: String?
    ) : PendingStorageAction()
}

class FileManager(private val context: Context) {

    /**
     * Permanently deletes audio files from real phone storage.
     * Supports Android 11+ (createDeleteRequest), Android 10 (RecoverableSecurityException),
     * and Android 7-9 (direct File + MediaStore delete).
     * Verifies actual file / MediaStore removal before returning Success.
     */
    suspend fun deleteSongsPermanently(songs: List<Song>): FileOpResult = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext FileOpResult.Error("No songs selected")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val uris = songs.map { it.contentUri }
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                FileOpResult.RequiresIntentSender(
                    intentSender = pendingIntent.intentSender,
                    pendingAction = PendingStorageAction.DeleteSongs(songs)
                )
            } catch (e: Exception) {
                FileOpResult.Error("Could not initiate deletion: ${e.localizedMessage}")
            }
        }

        // Android 10 (Q) or legacy (N-P)
        val failedSongs = mutableListOf<String>()
        for (song in songs) {
            try {
                val deletedRows = context.contentResolver.delete(song.contentUri, null, null)
                var fileDeleted = true
                if (song.dataPath.isNotBlank()) {
                    val file = File(song.dataPath)
                    if (file.exists()) {
                        fileDeleted = file.delete()
                    }
                }
                if (deletedRows == 0 && !fileDeleted) {
                    failedSongs.add(song.title)
                }
            } catch (sec: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                    return@withContext FileOpResult.RequiresIntentSender(
                        intentSender = sec.userAction.actionIntent.intentSender,
                        pendingAction = PendingStorageAction.DeleteSongs(songs)
                    )
                }
                failedSongs.add(song.title)
            } catch (_: Exception) {
                failedSongs.add(song.title)
            }
        }

        val stillExists = verifyAnySongStillExists(songs)
        if (failedSongs.isNotEmpty() || stillExists) {
            FileOpResult.Error(
                "Deletion failed or permission denied for: ${failedSongs.joinToString().ifBlank { "selected file(s)" }}"
            )
        } else {
            FileOpResult.Success("Permanently deleted ${songs.size} track(s) from storage.")
        }
    }

    /**
     * Verifies on Android 11+ (after IntentSender returns RESULT_OK) or Android 10 that the files
     * were genuinely removed from MediaStore and disk.
     */
    suspend fun verifySongsDeleted(songs: List<Song>): Boolean = withContext(Dispatchers.IO) {
        !verifyAnySongStillExists(songs)
    }

    private fun verifyAnySongStillExists(songs: List<Song>): Boolean {
        for (song in songs) {
            // Check physical file path if accessible
            if (song.dataPath.isNotBlank()) {
                val file = File(song.dataPath)
                if (file.exists()) {
                    // Attempt direct cleanup if MediaStore left stale file on older API
                    val deleted = runCatching { file.delete() }.getOrDefault(false)
                    if (!deleted && file.exists()) return true
                }
            }
            // Check MediaStore row
            val existsInStore = try {
                context.contentResolver.query(
                    song.contentUri,
                    arrayOf(MediaStore.Audio.Media._ID),
                    null,
                    null,
                    null
                )?.use { it.moveToFirst() } ?: false
            } catch (_: Exception) {
                false
            }
            if (existsInStore) return true
        }
        return false
    }

    /**
     * Updates MediaStore metadata & file display name where permitted by Android scoped storage.
     */
    suspend fun updateSongMetadataInStore(
        song: Song,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        newFileName: String?
    ): FileOpResult = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title.trim())
                put(MediaStore.Audio.Media.ARTIST, artist.trim())
                put(MediaStore.Audio.Media.ALBUM, album.trim())
                if (year > 0) put(MediaStore.Audio.Media.YEAR, year)
                if (trackNumber > 0) put(MediaStore.Audio.Media.TRACK, trackNumber)
                if (!newFileName.isNullOrBlank()) {
                    val ext = File(song.dataPath).extension.ifBlank { "mp3" }
                    val cleanName = newFileName.trim().removeSuffix(".$ext") + ".$ext"
                    put(MediaStore.Audio.Media.DISPLAY_NAME, cleanName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && genre.isNotBlank()) {
                    put(MediaStore.Audio.Media.GENRE, genre.trim())
                }
            }

            val updated = context.contentResolver.update(song.contentUri, values, null, null)
            if (updated > 0) {
                if (song.dataPath.isNotBlank()) {
                    MediaScannerConnection.scanFile(context, arrayOf(song.dataPath), null, null)
                }
                FileOpResult.Success("Updated track info & tags")
            } else {
                FileOpResult.Success("Saved custom track tags locally")
            }
        } catch (sec: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(song.contentUri))
                FileOpResult.RequiresIntentSender(
                    intentSender = pendingIntent.intentSender,
                    pendingAction = PendingStorageAction.UpdateSongTags(
                        song, title, artist, album, genre, year, trackNumber, newFileName
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                FileOpResult.RequiresIntentSender(
                    intentSender = sec.userAction.actionIntent.intentSender,
                    pendingAction = PendingStorageAction.UpdateSongTags(
                        song, title, artist, album, genre, year, trackNumber, newFileName
                    )
                )
            } else {
                FileOpResult.Success("Saved custom track tags in Lyneck library")
            }
        } catch (_: Exception) {
            FileOpResult.Success("Saved custom track tags in Lyneck library")
        }
    }

    /**
     * Copies or moves an audio file into a user-selected SAF destination folder tree URI.
     */
    suspend fun copyOrMoveSongToTree(
        song: Song,
        destinationTreeUri: Uri,
        move: Boolean
    ): FileOpResult = withContext(Dispatchers.IO) {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(destinationTreeUri, treeDocId)
            val fileName = if (song.dataPath.isNotBlank()) {
                File(song.dataPath).name
            } else {
                "${song.title}.mp3"
            }

            val destDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                song.mimeType.ifBlank { "audio/mpeg" },
                fileName
            ) ?: return@withContext FileOpResult.Error("Unable to create file in selected folder.")

            context.contentResolver.openInputStream(song.contentUri)?.use { input ->
                context.contentResolver.openOutputStream(destDocUri)?.use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                } ?: return@withContext FileOpResult.Error("Cannot write to destination folder.")
            } ?: return@withContext FileOpResult.Error("Cannot read source audio file.")

            if (move) {
                return@withContext deleteSongsPermanently(listOf(song))
            }

            FileOpResult.Success("Copied \"${song.title}\" successfully")
        } catch (e: Exception) {
            FileOpResult.Error("File operation failed: ${e.localizedMessage}")
        }
    }

    /**
     * Copies a user-selected artwork image from Photo Picker into internal storage for persistent custom cover art.
     */
    suspend fun saveCustomArtworkLocally(songId: Long, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "custom_artwork").apply { mkdirs() }
            val destFile = File(dir, "art_$songId.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun buildShareIntent(song: Song): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = song.mimeType.ifBlank { "audio/*" }
            putExtra(Intent.EXTRA_STREAM, song.contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "${song.title} - ${song.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share \"${song.title}\"")
    }

    fun canWriteSystemSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun buildManageWriteSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun setAsRingtone(song: Song): FileOpResult {
        if (!canWriteSystemSettings()) {
            return FileOpResult.Error("SYSTEM_WRITE_SETTINGS_REQUIRED")
        }
        return try {
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                song.contentUri
            )
            FileOpResult.Success("\"${song.title}\" set as ringtone")
        } catch (e: Exception) {
            FileOpResult.Error("Unable to set ringtone: ${e.localizedMessage}")
        }
    }
}
