package com.skul9x.rssreader.utils

import android.content.Context
import android.media.AudioManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeControllerTest {

    @Test
    fun testGetNextVolumePercentage_initialNull_returns80() {
        val result = VolumeController.getNextVolumePercentage(null)
        assertEquals(80, result)
    }

    @Test
    fun testGetNextVolumePercentage_invalidValue_returns80() {
        val result = VolumeController.getNextVolumePercentage(50)
        assertEquals(80, result)
    }

    @Test
    fun testGetNextVolumePercentage_from80_returns85() {
        val result = VolumeController.getNextVolumePercentage(80)
        assertEquals(85, result)
    }

    @Test
    fun testGetNextVolumePercentage_from85_returns90() {
        val result = VolumeController.getNextVolumePercentage(85)
        assertEquals(90, result)
    }

    @Test
    fun testGetNextVolumePercentage_from90_returns80() {
        val result = VolumeController.getNextVolumePercentage(90)
        assertEquals(80, result)
    }

    @Test
    fun testCalculateVolumeIndex() {
        // Max volume 15
        assertEquals(12, VolumeController.calculateVolumeIndex(15, 80))
        assertEquals(13, VolumeController.calculateVolumeIndex(15, 85))
        assertEquals(14, VolumeController.calculateVolumeIndex(15, 90))

        // Max volume 100
        assertEquals(80, VolumeController.calculateVolumeIndex(100, 80))
        assertEquals(85, VolumeController.calculateVolumeIndex(100, 85))

        // Edge case max volume <= 0
        assertEquals(0, VolumeController.calculateVolumeIndex(0, 80))
    }

    @Test
    fun testSetDeviceVolume_success() {
        val context = mockk<Context>()
        val audioManager = mockk<AudioManager>(relaxed = true)

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15

        val result = VolumeController.setDeviceVolume(context, 80)
        assertTrue(result)
        verify { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 12, 0) }
    }

    @Test
    fun testSetDeviceVolume_securityException_returnsFalse() {
        val context = mockk<Context>()
        val audioManager = mockk<AudioManager>()

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } throws SecurityException("Permission denied")

        val result = VolumeController.setDeviceVolume(context, 80)
        assertFalse(result)
    }
}
