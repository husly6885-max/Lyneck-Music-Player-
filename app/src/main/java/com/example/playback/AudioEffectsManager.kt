@file:Suppress("DEPRECATION")

package com.example.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.example.data.preferences.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val label: String,
    val levelMb: Int
)

data class EqualizerState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val bands: List<EqBandInfo> = emptyList(),
    val builtInPresets: List<String> = emptyList(),
    val activePresetName: String = "Flat",
    val bassBoostSupported: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerSupported: Boolean = false,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val trebleBoost: Int = 0,
    val isHeadphonesConnected: Boolean = false
)

/**
 * Manages real Android AudioEffect instances (Equalizer, BassBoost, Virtualizer, LoudnessEnhancer)
 * bound to ExoPlayer's audioSessionId, plus automatic Headphone vs Speaker profile switching.
 */
@Suppress("DEPRECATION")
class AudioEffectsManager(
    context: Context,
    private val onOutputRouteChanged: (isHeadphones: Boolean) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    private val _eqState = MutableStateFlow(EqualizerState())
    val eqState = _eqState.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            checkOutputDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            checkOutputDevice()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        checkOutputDevice()
    }

    fun checkOutputDevice() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasHeadphones = outputs.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val prev = _eqState.value.isHeadphonesConnected
        if (prev != hasHeadphones) {
            _eqState.value = _eqState.value.copy(isHeadphonesConnected = hasHeadphones)
            onOutputRouteChanged(hasHeadphones)
        } else {
            _eqState.value = _eqState.value.copy(isHeadphonesConnected = hasHeadphones)
        }
    }

    fun attachToAudioSession(audioSessionId: Int, settings: AppSettings) {
        if (audioSessionId == 0) return
        if (currentSessionId == audioSessionId && equalizer != null) {
            applySettings(settings)
            return
        }
        releaseEffects()
        currentSessionId = audioSessionId

        var eqSupported = false
        var minMb = -1500
        var maxMb = 1500
        val bands = mutableListOf<EqBandInfo>()
        val presets = mutableListOf<String>()

        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            eqSupported = true
            val range = eq.bandLevelRange
            minMb = range[0].toInt()
            maxMb = range[1].toInt()

            val bandCount = eq.numberOfBands.toInt()
            for (i in 0 until bandCount) {
                val freqHz = eq.getCenterFreq(i.toShort()) / 1000
                val label = if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz"
                bands.add(
                    EqBandInfo(
                        index = i,
                        centerFreqHz = freqHz,
                        label = label,
                        levelMb = eq.getBandLevel(i.toShort()).toInt()
                    )
                )
            }
            for (p in 0 until eq.numberOfPresets.toInt()) {
                presets.add(eq.getPresetName(p.toShort()))
            }
        } catch (_: Exception) {
            eqSupported = false
        }

        // Fallback band structure if running on a headless/Robolectric/limited device so UI remains accurate
        if (!eqSupported && bands.isEmpty()) {
            val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
            defaultFreqs.forEachIndexed { idx, freq ->
                val label = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz"
                bands.add(EqBandInfo(idx, freq, label, 0))
            }
        }

        val bbSupported = try {
            val bb = BassBoost(0, audioSessionId)
            bassBoost = bb
            bb.strengthSupported
        } catch (_: Exception) {
            false
        }

        val virtSupported = try {
            val virt = Virtualizer(0, audioSessionId)
            virtualizer = virt
            virt.strengthSupported
        } catch (_: Exception) {
            false
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
        } catch (_: Exception) {
            loudnessEnhancer = null
        }

        _eqState.value = _eqState.value.copy(
            supported = eqSupported,
            minLevelMb = minMb,
            maxLevelMb = maxMb,
            bands = bands,
            builtInPresets = if (presets.isNotEmpty()) presets else defaultPresetNames(),
            bassBoostSupported = bbSupported,
            virtualizerSupported = virtSupported
        )

        applySettings(settings)
    }

    fun applySettings(settings: AppSettings) {
        val state = _eqState.value
        val activeCsv = if (settings.separateHeadphoneProfile) {
            if (state.isHeadphonesConnected) settings.headphoneBandLevelsCsv else settings.speakerBandLevelsCsv
        } else {
            settings.eqBandLevelsCsv
        }

        val parsedLevels = activeCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        val minMb = state.minLevelMb
        val maxMb = state.maxLevelMb

        // Apply Equalizer + Treble boost on top band
        try {
            equalizer?.let { eq ->
                eq.enabled = settings.equalizerEnabled
                val count = eq.numberOfBands.toInt()
                for (i in 0 until count) {
                    val base = parsedLevels.getOrElse(i) { 0 }
                    val trebleAdd = if (i == count - 1) (settings.trebleBoost * 0.6f).toInt() else 0
                    val clamped = (base + trebleAdd).coerceIn(minMb, maxMb)
                    eq.setBandLevel(i.toShort(), clamped.toShort())
                }
            }
        } catch (_: Exception) {
        }

        val updatedBands = state.bands.mapIndexed { idx, band ->
            val base = parsedLevels.getOrElse(idx) { band.levelMb }
            band.copy(levelMb = base.coerceIn(minMb, maxMb))
        }

        // Apply BassBoost
        try {
            bassBoost?.let { bb ->
                bb.enabled = settings.bassBoostEnabled && settings.bassBoostStrength > 0
                if (bb.strengthSupported) {
                    bb.setStrength(settings.bassBoostStrength.coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Exception) {
        }

        // Apply Virtualizer
        try {
            virtualizer?.let { virt ->
                virt.enabled = settings.virtualizerEnabled && settings.virtualizerStrength > 0
                if (virt.strengthSupported) {
                    virt.setStrength(settings.virtualizerStrength.coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Exception) {
        }

        // Apply subtle loudness / treble presence via LoudnessEnhancer if treble boost > 0
        try {
            loudnessEnhancer?.let { le ->
                val gainMb = (settings.trebleBoost * 0.5f).toInt().coerceIn(0, 500)
                le.enabled = settings.equalizerEnabled && gainMb > 0
                le.setTargetGain(gainMb)
            }
        } catch (_: Exception) {
        }

        _eqState.value = state.copy(
            enabled = settings.equalizerEnabled,
            bands = updatedBands,
            activePresetName = settings.activeEqPresetName,
            bassBoostEnabled = settings.bassBoostEnabled,
            bassBoostStrength = settings.bassBoostStrength,
            virtualizerEnabled = settings.virtualizerEnabled,
            virtualizerStrength = settings.virtualizerStrength,
            trebleBoost = settings.trebleBoost
        )
    }

    fun getPresetBandLevels(presetName: String, bandCount: Int): List<Int> {
        equalizer?.let { eq ->
            for (p in 0 until eq.numberOfPresets.toInt()) {
                if (eq.getPresetName(p.toShort()).equals(presetName, ignoreCase = true)) {
                    eq.usePreset(p.toShort())
                    return (0 until eq.numberOfBands.toInt()).map {
                        eq.getBandLevel(it.toShort()).toInt()
                    }
                }
            }
        }
        return when (presetName.lowercase()) {
            "bass boost" -> listOf(600, 400, 0, 0, 0)
            "rock" -> listOf(500, 300, -100, 300, 500)
            "pop" -> listOf(-100, 200, 500, 200, -100)
            "jazz" -> listOf(400, 200, -200, 200, 500)
            "classical" -> listOf(500, 300, -100, 400, 400)
            "dance" -> listOf(600, 400, 100, 300, 400)
            "hip hop" -> listOf(500, 300, 0, 100, 300)
            else -> List(bandCount.coerceAtLeast(5)) { 0 }
        }.take(bandCount.coerceAtLeast(5))
    }

    private fun defaultPresetNames(): List<String> =
        listOf("Flat", "Bass Boost", "Rock", "Pop", "Jazz", "Classical", "Dance", "Hip Hop")

    fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentSessionId = 0
    }

    fun destroy() {
        releaseEffects()
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {
        }
    }
}
