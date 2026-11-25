package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.MusicDao
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

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        RecentlyPlayedEntity::class,
        SongMetadataEntity::class,
        LyricsEntity::class,
        BookmarkEntity::class,
        ListeningHistoryEntity::class,
        ExcludedFolderEntity::class,
        EqualizerPresetEntity::class,
        SavedQueueEntity::class,
        SavedQueueItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LyneckDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: LyneckDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `song_metadata` (
                        `songId` INTEGER NOT NULL,
                        `customTitle` TEXT,
                        `customArtist` TEXT,
                        `customAlbum` TEXT,
                        `customGenre` TEXT,
                        `customYear` INTEGER,
                        `customTrackNumber` INTEGER,
                        `customArtworkUri` TEXT,
                        `playCount` INTEGER NOT NULL DEFAULT 0,
                        `totalListenTimeMs` INTEGER NOT NULL DEFAULT 0,
                        `lastPlayedAt` INTEGER NOT NULL DEFAULT 0,
                        `replayGainDb` REAL NOT NULL DEFAULT 0.0,
                        PRIMARY KEY(`songId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lyrics` (
                        `songId` INTEGER NOT NULL,
                        `lyricsText` TEXT NOT NULL,
                        `isSynced` INTEGER NOT NULL DEFAULT 0,
                        `offsetMs` INTEGER NOT NULL DEFAULT 0,
                        `sourcePath` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`songId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `listening_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `playedAt` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `excluded_folders` (
                        `folderPath` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`folderPath`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `equalizer_presets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `bandLevelsCsv` TEXT NOT NULL,
                        `bassBoost` INTEGER NOT NULL DEFAULT 0,
                        `virtualizer` INTEGER NOT NULL DEFAULT 0,
                        `trebleBoost` INTEGER NOT NULL DEFAULT 0,
                        `isCustom` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_queues` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 0,
                        `currentIndex` INTEGER NOT NULL DEFAULT 0,
                        `positionMs` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_queue_items` (
                        `queueId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `songId` INTEGER NOT NULL,
                        PRIMARY KEY(`queueId`, `orderIndex`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): LyneckDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LyneckDatabase::class.java,
                    "lyneck_music_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
