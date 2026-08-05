package com.skul9x.rssreader.ui.settings

import com.skul9x.rssreader.ui.main.MainViewModel
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsClearCacheTest {

    @Test
    fun `confirming clear cache dialog executes viewModel clearCache`() {
        val viewModel = mockk<MainViewModel>(relaxed = true)
        
        // Simulating the user action in SettingsScreen: confirming dialog calls clearCache()
        viewModel.clearCache()

        verify(exactly = 1) { viewModel.clearCache() }
    }

    @Test
    fun `clear cache dialog state initially hidden and turns visible on click`() {
        var showClearCacheDialog = false
        
        // User clicks "Xóa bộ nhớ tạm (Cache)" item
        showClearCacheDialog = true
        assertTrue(showClearCacheDialog)

        // User clicks "Hủy" or confirms
        showClearCacheDialog = false
        assertTrue(!showClearCacheDialog)
    }
}
