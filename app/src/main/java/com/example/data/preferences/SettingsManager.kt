package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class AppThemeMode {
    OCEAN_NAVY,
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

enum class AccentColorOption(val label: String, val primaryHex: Long, val secondaryHex: Long) {
    SKY_BLUE("Ocean Sky", 0xFF4EA8DE, 0xFF72C5F5),
    NEON_PURPLE("Neon Purple", 0xFFA855F7, 0xFF06B6D4),
    ELECTRIC_CYAN("Electric Cyan", 0xFF06B6D4, 0xFFA855F7),
    CRIMSON_ROSE("Crimson Rose", 0xFFF43F5E, 0xFFFB923C),
    EMERALD_MINT("Emerald Mint", 0xFF10B981, 0xFF38BDF8),
    SOLAR_AMBER("Solar Amber", 0xFFF59E0B, 0xFFEF4444),
    ROYAL_INDIGO("Royal Indigo", 0xFF6366F1, 0xFFEC4899)
}

enum class PlayerScreenStyle(val label: String) {
    CLASSIC("Classic Artwork"),
    VINYL("Vinyl Turntable"),
    VISUALIZER("Visualizer Studio"),
    MINIMAL("Minimalist")
}

data class AppSettings(
    // Appearance
    val themeMode: AppThemeMode = AppThemeMode.OCEAN_NAVY,
    val accentColor: AccentColorOption = AccentColorOption.SKY_BLUE,
    val playerStyle: PlayerScreenStyle = PlayerScreenStyle.CLASSIC,
    val keepScreenOnInCarMode: Boolean = true,

    // Playback
    val gaplessPlayback: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val fadeInOut: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val stopAfterCurrentTrack: Boolean = false,
    val rememberPlaybackPosition: Boolean = true,
    val lastSongId: Long = -1L,
    val lastPositionMs: Long = 0L,

    // Audio & Equalizer
    val equalizerEnabled: Boolean = false,
    val activeEqPresetName: String = "Flat",
    val eqBandLevelsCsv: String = "0,0,0,0,0",
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 350, // 0..1000
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 300, // 0..1000
    val trebleBoost: Int = 0, // 0..1000
    val audioBalance: Float = 0f, // -1.0f (Left) to +1.0f (Right)
    val volumeNormalization: Boolean = false,
    val replayGainEnabled: Boolean = false,
    val separateHeadphoneProfile: Boolean = false,
    val headphoneBandLevelsCsv: String = "200,100,0,100,200",
    val speakerBandLevelsCsv: String = "0,0,150,200,150",

    // Lyrics
    val autoLoadExternalLrc: Boolean = true,
    val karaokeHighlight: Boolean = true,
    val defaultLyricsOffsetMs: Long = 0L,

    // Library
    val sortOrder: SortOrder = SortOrder.TITLE_ASC,
    val autoScanOnStartup: Boolean = true,
    val minTrackDurationSec: Int = 10,

    // Notifications & Service
    val showArtworkInNotification: Boolean = true,
    val keepPlayingOnTaskRemove: Boolean = true
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("lyneck_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeStr = prefs.getString("theme_mode", AppThemeMode.OCEAN_NAVY.name) ?: AppThemeMode.OCEAN_NAVY.name
        val themeMode = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.OCEAN_NAVY)

        val accentStr = prefs.getString("accent_color", AccentColorOption.SKY_BLUE.name)
            ?: AccentColorOption.SKY_BLUE.name
        val accentColor = runCatching { AccentColorOption.valueOf(accentStr) }
            .getOrDefault(AccentColorOption.SKY_BLUE)

        val styleStr = prefs.getString("player_style", PlayerScreenStyle.CLASSIC.name)
            ?: PlayerScreenStyle.CLASSIC.name
        val playerStyle = runCatching { PlayerScreenStyle.valueOf(styleStr) }
            .getOrDefault(PlayerScreenStyle.CLASSIC)

        val sortStr = prefs.getString("sort_order", SortOrder.TITLE_ASC.name) ?: SortOrder.TITLE_ASC.name
        val sortOrder = runCatching { SortOrder.valueOf(sortStr) }.getOrDefault(SortOrder.TITLE_ASC)

        return AppSettings(
            themeMode = themeMode,
            accentColor = accentColor,
            playerStyle = playerStyle,
            keepScreenOnInCarMode = prefs.getBoolean("keep_screen_on_car", true),
            gaplessPlayback = prefs.getBoolean("gapless_playback", true),
            crossfadeSeconds = prefs.getInt("crossfade_seconds", 0),
            fadeInOut = prefs.getBoolean("fade_in_out", true),
            playbackSpeed = prefs.getFloat("playback_speed", 1.0f),
            skipSilence = prefs.getBoolean("skip_silence", false),
            stopAfterCurrentTrack = prefs.getBoolean("stop_after_current", false),
            rememberPlaybackPosition = prefs.getBoolean("remember_position", true),
            lastSongId = prefs.getLong("last_song_id", -1L),
            lastPositionMs = prefs.getLong("last_position_ms", 0L),
            equalizerEnabled = prefs.getBoolean("eq_enabled", false),
            activeEqPresetName = prefs.getString("eq_preset", "Flat") ?: "Flat",
            eqBandLevelsCsv = prefs.getString("eq_bands", "0,0,0,0,0") ?: "0,0,0,0,0",
            bassBoostEnabled = prefs.getBoolean("bass_boost_enabled", false),
            bassBoostStrength = prefs.getInt("bass_boost_strength", 350),
            virtualizerEnabled = prefs.getBoolean("virtualizer_enabled", false),
            virtualizerStrength = prefs.getInt("virtualizer_strength", 300),
            trebleBoost = prefs.getInt("treble_boost", 0),
            audioBalance = prefs.getFloat("audio_balance", 0f),
            volumeNormalization = prefs.getBoolean("volume_normalization", false),
            replayGainEnabled = prefs.getBoolean("replay_gain", false),
            separateHeadphoneProfile = prefs.getBoolean("separate_headphone_profile", false),
            headphoneBandLevelsCsv = prefs.getString("headphone_eq_bands", "200,100,0,100,200") ?: "200,100,0,100,200",
            speakerBandLevelsCsv = prefs.getString("speaker_eq_bands", "0,0,150,200,150") ?: "0,0,150,200,150",
            autoLoadExternalLrc = prefs.getBoolean("auto_load_lrc", true),
            karaokeHighlight = prefs.getBoolean("karaoke_highlight", true),
            defaultLyricsOffsetMs = prefs.getLong("default_lyrics_offset", 0L),
            sortOrder = sortOrder,
            autoScanOnStartup = prefs.getBoolean("auto_scan_startup", true),
            minTrackDurationSec = prefs.getInt("min_track_duration_sec", 10),
            showArtworkInNotification = prefs.getBoolean("notif_artwork", true),
            keepPlayingOnTaskRemove = prefs.getBoolean("keep_playing_task_remove", true)
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        saveSettings(updated)
    }

    fun saveLastPlayback(songId: Long, positionMs: Long) {
        prefs.edit()
            .putLong("last_song_id", songId)
            .putLong("last_position_ms", positionMs)
            .apply()
        _settings.value = _settings.value.copy(lastSongId = songId, lastPositionMs = positionMs)
    }

    private fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("theme_mode", s.themeMode.name)
            .putString("accent_color", s.accentColor.name)
            .putString("player_style", s.playerStyle.name)
            .putBoolean("keep_screen_on_car", s.keepScreenOnInCarMode)
            .putBoolean("gapless_playback", s.gaplessPlayback)
            .putInt("crossfade_seconds", s.crossfadeSeconds)
            .putBoolean("fade_in_out", s.fadeInOut)
            .putFloat("playback_speed", s.playbackSpeed)
            .putBoolean("skip_silence", s.skipSilence)
            .putBoolean("stop_after_current", s.stopAfterCurrentTrack)
            .putBoolean("remember_position", s.rememberPlaybackPosition)
            .putLong("last_song_id", s.lastSongId)
            .putLong("last_position_ms", s.lastPositionMs)
            .putBoolean("eq_enabled", s.equalizerEnabled)
            .putString("eq_preset", s.activeEqPresetName)
            .putString("eq_bands", s.eqBandLevelsCsv)
            .putBoolean("bass_boost_enabled", s.bassBoostEnabled)
            .putInt("bass_boost_strength", s.bassBoostStrength)
            .putBoolean("virtualizer_enabled", s.virtualizerEnabled)
            .putInt("virtualizer_strength", s.virtualizerStrength)
            .putInt("treble_boost", s.trebleBoost)
            .putFloat("audio_balance", s.audioBalance)
            .putBoolean("volume_normalization", s.volumeNormalization)
            .putBoolean("replay_gain", s.replayGainEnabled)
            .putBoolean("separate_headphone_profile", s.separateHeadphoneProfile)
            .putString("headphone_eq_bands", s.headphoneBandLevelsCsv)
            .putString("speaker_eq_bands", s.speakerBandLevelsCsv)
            .putBoolean("auto_load_lrc", s.autoLoadExternalLrc)
            .putBoolean("karaoke_highlight", s.karaokeHighlight)
            .putLong("default_lyrics_offset", s.defaultLyricsOffsetMs)
            .putString("sort_order", s.sortOrder.name)
            .putBoolean("auto_scan_startup", s.autoScanOnStartup)
            .putInt("min_track_duration_sec", s.minTrackDurationSec)
            .putBoolean("notif_artwork", s.showArtworkInNotification)
            .putBoolean("keep_playing_task_remove", s.keepPlayingOnTaskRemove)
            .apply()
    }

    fun exportToJson(): JSONObject {
        val s = _settings.value
        return JSONObject().apply {
            put("themeMode", s.themeMode.name)
            put("accentColor", s.accentColor.name)
            put("playerStyle", s.playerStyle.name)
            put("gaplessPlayback", s.gaplessPlayback)
            put("crossfadeSeconds", s.crossfadeSeconds)
            put("fadeInOut", s.fadeInOut)
            put("playbackSpeed", s.playbackSpeed.toDouble())
            put("skipSilence", s.skipSilence)
            put("rememberPlaybackPosition", s.rememberPlaybackPosition)
            put("equalizerEnabled", s.equalizerEnabled)
            put("activeEqPresetName", s.activeEqPresetName)
            put("eqBandLevelsCsv", s.eqBandLevelsCsv)
            put("bassBoostEnabled", s.bassBoostEnabled)
            put("bassBoostStrength", s.bassBoostStrength)
            put("virtualizerEnabled", s.virtualizerEnabled)
            put("virtualizerStrength", s.virtualizerStrength)
            put("trebleBoost", s.trebleBoost)
            put("audioBalance", s.audioBalance.toDouble())
            put("volumeNormalization", s.volumeNormalization)
            put("replayGainEnabled", s.replayGainEnabled)
            put("separateHeadphoneProfile", s.separateHeadphoneProfile)
            put("autoLoadExternalLrc", s.autoLoadExternalLrc)
            put("karaokeHighlight", s.karaokeHighlight)
            put("sortOrder", s.sortOrder.name)
            put("minTrackDurationSec", s.minTrackDurationSec)
            put("showArtworkInNotification", s.showArtworkInNotification)
            put("keepPlayingOnTaskRemove", s.keepPlayingOnTaskRemove)
        }
    }

    fun importFromJson(obj: JSONObject) {
        update { cur ->
            cur.copy(
                themeMode = runCatching { AppThemeMode.valueOf(obj.optString("themeMode", cur.themeMode.name)) }.getOrDefault(cur.themeMode),
                accentColor = runCatching { AccentColorOption.valueOf(obj.optString("accentColor", cur.accentColor.name)) }.getOrDefault(cur.accentColor),
                playerStyle = runCatching { PlayerScreenStyle.valueOf(obj.optString("playerStyle", cur.playerStyle.name)) }.getOrDefault(cur.playerStyle),
                gaplessPlayback = obj.optBoolean("gaplessPlayback", cur.gaplessPlayback),
                crossfadeSeconds = obj.optInt("crossfadeSeconds", cur.crossfadeSeconds),
                fadeInOut = obj.optBoolean("fadeInOut", cur.fadeInOut),
                playbackSpeed = obj.optDouble("playbackSpeed", cur.playbackSpeed.toDouble()).toFloat(),
                skipSilence = obj.optBoolean("skipSilence", cur.skipSilence),
                rememberPlaybackPosition = obj.optBoolean("rememberPlaybackPosition", cur.rememberPlaybackPosition),
                equalizerEnabled = obj.optBoolean("equalizerEnabled", cur.equalizerEnabled),
                activeEqPresetName = obj.optString("activeEqPresetName", cur.activeEqPresetName),
                eqBandLevelsCsv = obj.optString("eqBandLevelsCsv", cur.eqBandLevelsCsv),
                bassBoostEnabled = obj.optBoolean("bassBoostEnabled", cur.bassBoostEnabled),
                bassBoostStrength = obj.optInt("bassBoostStrength", cur.bassBoostStrength),
                virtualizerEnabled = obj.optBoolean("virtualizerEnabled", cur.virtualizerEnabled),
                virtualizerStrength = obj.optInt("virtualizerStrength", cur.virtualizerStrength),
                trebleBoost = obj.optInt("trebleBoost", cur.trebleBoost),
                audioBalance = obj.optDouble("audioBalance", cur.audioBalance.toDouble()).toFloat(),
                volumeNormalization = obj.optBoolean("volumeNormalization", cur.volumeNormalization),
                replayGainEnabled = obj.optBoolean("replayGainEnabled", cur.replayGainEnabled),
                separateHeadphoneProfile = obj.optBoolean("separateHeadphoneProfile", cur.separateHeadphoneProfile),
                autoLoadExternalLrc = obj.optBoolean("autoLoadExternalLrc", cur.autoLoadExternalLrc),
                karaokeHighlight = obj.optBoolean("karaokeHighlight", cur.karaokeHighlight),
                sortOrder = runCatching { SortOrder.valueOf(obj.optString("sortOrder", cur.sortOrder.name)) }.getOrDefault(cur.sortOrder),
                minTrackDurationSec = obj.optInt("minTrackDurationSec", cur.minTrackDurationSec),
                showArtworkInNotification = obj.optBoolean("showArtworkInNotification", cur.showArtworkInNotification),
                keepPlayingOnTaskRemove = obj.optBoolean("keepPlayingOnTaskRemove", cur.keepPlayingOnTaskRemove)
            )
        }
    }
}
