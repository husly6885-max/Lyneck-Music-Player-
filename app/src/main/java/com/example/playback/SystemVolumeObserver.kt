package com.example.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class DeviceVolumeState(
    val currentStep: Int = 8,
    val maxStep: Int = 15,
    val ratio: Float = 0.53f
) {
    val percentage: Int
        get() = if (maxStep > 0) ((currentStep.toFloat() / maxStep) * 100).roundToInt() else 0
}

/**
 * Controls actual Android STREAM_MUSIC volume via AudioManager and stays synchronized
 * with physical phone volume buttons via VOLUME_CHANGED_ACTION broadcast.
 */
class SystemVolumeObserver(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _volumeState = MutableStateFlow(readCurrentVolume())
    val volumeState = _volumeState.asStateFlow()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    _volumeState.value = readCurrentVolume()
                }
            }
        }
    }

    init {
        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(volumeReceiver, filter)
        }
    }

    fun readCurrentVolume(): DeviceVolumeState {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
        return DeviceVolumeState(
            currentStep = cur,
            maxStep = max,
            ratio = cur.toFloat() / max.toFloat()
        )
    }

    fun refresh() {
        _volumeState.value = readCurrentVolume()
    }

    fun setVolumeRatio(ratio: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetStep = (ratio.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
        _volumeState.value = readCurrentVolume()
    }

    fun setVolumeStep(step: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetStep = step.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
        _volumeState.value = readCurrentVolume()
    }

    fun destroy() {
        try {
            context.unregisterReceiver(volumeReceiver)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
