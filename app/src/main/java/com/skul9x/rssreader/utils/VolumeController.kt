package com.skul9x.rssreader.utils

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/**
 * Utility controller to manage device media stream volume (AudioManager.STREAM_MUSIC)
 * and handle step cycling logic (80% -> 85% -> 90% -> 80%).
 */
object VolumeController {

    val VOLUME_STEPS: List<Int> = listOf(80, 85, 90)
    val VOLUME_PERCENTAGES: List<Int> = VOLUME_STEPS

    /**
     * Calculates the next volume percentage in the sequence (80% -> 85% -> 90% -> 80%).
     * Returns 80 if currentPercent is null or not a valid step.
     */
    fun getNextVolumePercentage(currentPercent: Int?): Int {
        return when (currentPercent) {
            80 -> 85
            85 -> 90
            90 -> 80
            else -> 80
        }
    }

    /**
     * Maps volume percentage (0-100) to stream volume index based on maxVolume.
     */
    fun calculateVolumeIndex(maxVolume: Int, percentage: Int): Int {
        if (maxVolume <= 0) return 0
        val target = (percentage / 100.0) * maxVolume
        return target.roundToInt().coerceIn(0, maxVolume)
    }

    /**
     * Safely adjusts device volume for STREAM_MUSIC.
     * Returns true if successful, false if audioManager is missing or an exception occurred.
     */
    fun setDeviceVolume(context: Context, percentage: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return false
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetIndex = calculateVolumeIndex(maxVolume, percentage)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
