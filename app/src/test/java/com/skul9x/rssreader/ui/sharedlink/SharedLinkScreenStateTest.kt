package com.skul9x.rssreader.ui.sharedlink

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedLinkScreenStateTest {

    @Test
    fun testVolumeButtonOpacity_whenInactive_isDimmed() {
        val alpha = getVolumeButtonAlpha(isVolumeControlActive = false)
        assertEquals(0.4f, alpha, 0.001f)
    }

    @Test
    fun testVolumeButtonOpacity_whenActive_isFullOpacity() {
        val alpha = getVolumeButtonAlpha(isVolumeControlActive = true)
        assertEquals(1.0f, alpha, 0.001f)
    }

    @Test
    fun testVolumeButtonText_reflectsCurrentPercentage() {
        assertEquals("", getVolumeButtonText(null))
        assertEquals("80%", getVolumeButtonText(80))
        assertEquals("85%", getVolumeButtonText(85))
        assertEquals("90%", getVolumeButtonText(90))
    }
}
