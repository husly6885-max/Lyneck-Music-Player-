package com.example.data.playlist

import android.content.Context
import android.net.Uri
import com.example.data.local.dao.MusicDao
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ExcludedFolderEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistSongEntity
import com.example.data.model.Song
import com.example.data.preferences.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class M3uPlaylistManager(
    private val context: Context,
    private val musicDao: MusicDao,
    private val settingsManager: SettingsManager
) {
    suspend fun exportPlaylistToM3u(
        playlistName: String,
        songs: List<Song>,
        destinationUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder()
            sb.appendLine("#EXTM3U")
            sb.appendLine("#PLAYLIST:$playlistName")
            for (song in songs) {
                val durSec = (song.duration / 1000).coerceAtLeast(0)
                sb.appendLine("#EXTINF:$durSec,${song.artist} - ${song.title}")
                val linePath = song.dataPath.ifBlank { song.contentUri.toString() }
                sb.appendLine(linePath)
            }
            context.contentResolver.openOutputStream(destinationUri)?.bufferedWriter()?.use { writer ->
                writer.write(sb.toString())
            } ?: error("Cannot open output stream for M3U export")
            "Exported ${songs.size} tracks to M3U"
        }
    }

    suspend fun importPlaylistFromM3u(
        uri: Uri,
        fallbackName: String,
        allSongs: List<Song>
    ): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Cannot read M3U file")

            var playlistName = fallbackName.removeSuffix(".m3u8").removeSuffix(".m3u").ifBlank { "Imported Playlist" }
            val matchedIds = mutableListOf<Long>()

            val byPath = allSongs.filter { it.dataPath.isNotBlank() }.associateBy { it.dataPath.lowercase() }
            val byFileName = allSongs.filter { it.dataPath.isNotBlank() }.associateBy { File(it.dataPath).name.lowercase() }
            val byTitle = allSongs.associateBy { it.title.trim().lowercase() }
            val byUri = allSongs.associateBy { it.contentUri.toString() }

            var pendingExtInfTitle: String? = null

            for (rawLine in raw.lines()) {
                val line = rawLine.trim()
                if (line.isBlank()) continue
                if (line.startsWith("#PLAYLIST:", ignoreCase = true)) {
                    val parsedName = line.substringAfter(":").trim()
                    if (parsedName.isNotBlank()) playlistName = parsedName
                    continue
                }
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    pendingExtInfTitle = line.substringAfter(",", "").trim()
                    continue
                }
                if (line.startsWith("#")) continue

                val matchedSong = byUri[line]
                    ?: byPath[line.lowercase()]
                    ?: byFileName[File(line).name.lowercase()]
                    ?: pendingExtInfTitle?.let { ext ->
                        val cleanTitle = ext.substringAfter(" - ", ext).trim().lowercase()
                        byTitle[cleanTitle]
                    }

                if (matchedSong != null && matchedSong.id !in matchedIds) {
                    matchedIds.add(matchedSong.id)
                }
                pendingExtInfTitle = null
            }

            val playlistId = musicDao.insertPlaylist(PlaylistEntity(name = playlistName))
            if (matchedIds.isNotEmpty()) {
                val entries = matchedIds.mapIndexed { idx, sid ->
                    PlaylistSongEntity(playlistId = playlistId, songId = sid, orderIndex = idx)
                }
                musicDao.addSongsToPlaylist(entries)
            }
            playlistName to matchedIds.size
        }
    }

    suspend fun exportFullBackupJson(destinationUri: Uri, allSongs: List<Song>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val songMap = allSongs.associateBy { it.id }
                val root = JSONObject()
                root.put("version", 2)
                root.put("exportedAt", System.currentTimeMillis())
                root.put("settings", settingsManager.exportToJson())

                val favArray = JSONArray()
                val favs = musicDao.getAllFavoritesSync()
                for (fav in favs) {
                    val s = songMap[fav.songId]
                    favArray.put(JSONObject().apply {
                        put("songId", fav.songId)
                        put("path", s?.dataPath ?: "")
                        put("title", s?.title ?: "")
                        put("addedAt", fav.addedAt)
                    })
                }
                root.put("favorites", favArray)

                val plArray = JSONArray()
                val playlists = musicDao.getAllPlaylistsSync()
                for (pl in playlists) {
                    val plSongs = musicDao.getPlaylistSongsSync(pl.id)
                    val songsArr = JSONArray()
                    for (ps in plSongs) {
                        val s = songMap[ps.songId]
                        songsArr.put(JSONObject().apply {
                            put("songId", ps.songId)
                            put("path", s?.dataPath ?: "")
                            put("title", s?.title ?: "")
                            put("orderIndex", ps.orderIndex)
                        })
                    }
                    plArray.put(JSONObject().apply {
                        put("name", pl.name)
                        put("createdAt", pl.createdAt)
                        put("songs", songsArr)
                    })
                }
                root.put("playlists", plArray)

                val excludedArr = JSONArray()
                for (ef in musicDao.getExcludedFoldersSync()) {
                    excludedArr.put(ef.folderPath)
                }
                root.put("excludedFolders", excludedArr)

                val bookmarksArr = JSONArray()
                for (bm in musicDao.getAllBookmarks().first()) {
                    bookmarksArr.put(JSONObject().apply {
                        put("songId", bm.songId)
                        put("positionMs", bm.positionMs)
                        put("label", bm.label)
                        put("createdAt", bm.createdAt)
                    })
                }
                root.put("bookmarks", bookmarksArr)

                context.contentResolver.openOutputStream(destinationUri)?.bufferedWriter()?.use {
                    it.write(root.toString(2))
                } ?: error("Failed to write backup file")

                "Exported settings, ${playlists.size} playlists & ${favs.size} favorites"
            }
        }

    suspend fun importFullBackupJson(sourceUri: Uri, allSongs: List<Song>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Failed to read backup file")
                val root = JSONObject(raw)

                root.optJSONObject("settings")?.let { settingsManager.importFromJson(it) }

                val byId = allSongs.associateBy { it.id }
                val byPath = allSongs.filter { it.dataPath.isNotBlank() }.associateBy { it.dataPath }
                val byTitle = allSongs.associateBy { it.title.lowercase() }

                fun resolveSongId(obj: JSONObject): Long? {
                    val id = obj.optLong("songId", -1L)
                    if (byId.containsKey(id)) return id
                    val path = obj.optString("path", "")
                    if (path.isNotBlank() && byPath.containsKey(path)) return byPath[path]?.id
                    val title = obj.optString("title", "").lowercase()
                    if (title.isNotBlank() && byTitle.containsKey(title)) return byTitle[title]?.id
                    return null
                }

                val favArr = root.optJSONArray("favorites")
                var favCount = 0
                if (favArr != null) {
                    for (i in 0 until favArr.length()) {
                        val obj = favArr.getJSONObject(i)
                        val resolved = resolveSongId(obj)
                        if (resolved != null) {
                            musicDao.insertFavorite(FavoriteEntity(songId = resolved, addedAt = obj.optLong("addedAt", System.currentTimeMillis())))
                            favCount++
                        }
                    }
                }

                val plArr = root.optJSONArray("playlists")
                var plCount = 0
                if (plArr != null) {
                    val existingNames = musicDao.getAllPlaylistsSync().map { it.name.lowercase() }.toSet()
                    for (i in 0 until plArr.length()) {
                        val plObj = plArr.getJSONObject(i)
                        val name = plObj.optString("name", "Restored Playlist")
                        val uniqueName = if (name.lowercase() in existingNames) "$name (Restored)" else name
                        val newId = musicDao.insertPlaylist(PlaylistEntity(name = uniqueName, createdAt = plObj.optLong("createdAt", System.currentTimeMillis())))
                        val songsArr = plObj.optJSONArray("songs")
                        val entries = mutableListOf<PlaylistSongEntity>()
                        if (songsArr != null) {
                            for (j in 0 until songsArr.length()) {
                                val sObj = songsArr.getJSONObject(j)
                                val sid = resolveSongId(sObj)
                                if (sid != null) {
                                    entries.add(PlaylistSongEntity(playlistId = newId, songId = sid, orderIndex = j))
                                }
                            }
                        }
                        if (entries.isNotEmpty()) musicDao.addSongsToPlaylist(entries)
                        plCount++
                    }
                }

                val excArr = root.optJSONArray("excludedFolders")
                if (excArr != null) {
                    for (i in 0 until excArr.length()) {
                        val p = excArr.optString(i)
                        if (p.isNotBlank()) musicDao.addExcludedFolder(ExcludedFolderEntity(folderPath = p))
                    }
                }

                val bmArr = root.optJSONArray("bookmarks")
                if (bmArr != null) {
                    for (i in 0 until bmArr.length()) {
                        val b = bmArr.getJSONObject(i)
                        val sid = b.optLong("songId", -1L)
                        if (byId.containsKey(sid)) {
                            musicDao.insertBookmark(
                                BookmarkEntity(
                                    songId = sid,
                                    positionMs = b.optLong("positionMs", 0L),
                                    label = b.optString("label", "Bookmark"),
                                    createdAt = b.optLong("createdAt", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                }

                "Restored settings, $plCount playlists & $favCount favorites"
            }
        }
}
