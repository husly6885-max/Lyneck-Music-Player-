package com.example

import android.content.Context
import com.example.data.local.LyneckDatabase
import com.example.data.lyrics.LyricsManager
import com.example.data.playlist.M3uPlaylistManager
import com.example.data.preferences.SettingsManager
import com.example.data.repository.MusicRepository
import com.example.data.scanner.MediaScanner
import com.example.data.storage.FileManager

class LyneckContainer private constructor(appContext: Context) {

    val settingsManager = SettingsManager(appContext)
    val database = LyneckDatabase.getDatabase(appContext)
    val mediaScanner = MediaScanner(appContext)
    val repository = MusicRepository(mediaScanner, database.musicDao(), settingsManager)
    val fileManager = FileManager(appContext)
    val lyricsManager = LyricsManager(appContext)
    val m3uManager = M3uPlaylistManager(appContext, database.musicDao(), settingsManager)

    companion object {
        @Volatile
        private var INSTANCE: LyneckContainer? = null

        fun get(context: Context): LyneckContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LyneckContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
