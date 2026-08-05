package com.skul9x.rssreader.ui.sharedlink

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.skul9x.rssreader.data.local.ApiKeyManager
import com.skul9x.rssreader.data.local.AppDatabase
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.CustomSelectorManager
import com.skul9x.rssreader.data.network.ModelQuotaManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedLinkViewModelTest {

    private lateinit var application: Application
    private lateinit var audioManager: AudioManager
    private lateinit var viewModel: SharedLinkViewModel

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(CustomSelectorManager)
        every { CustomSelectorManager.getInstance(any()) } returns mockk(relaxed = true)

        mockkObject(ApiKeyManager)
        every { ApiKeyManager.getInstance(any()) } returns mockk(relaxed = true)

        mockkObject(AppDatabase)
        val database = mockk<AppDatabase>(relaxed = true)
        every { AppDatabase.getDatabase(any()) } returns database

        mockkObject(AppPreferences)
        every { AppPreferences.getInstance(any()) } returns mockk(relaxed = true)

        mockkObject(ModelQuotaManager)
        every { ModelQuotaManager.getInstance(any()) } returns mockk(relaxed = true)

        application = mockk<Application>(relaxed = true)
        audioManager = mockk<AudioManager>(relaxed = true)
        every { application.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15

        viewModel = SharedLinkViewModel(application)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInitialState_volumeInactiveAndNull() {
        val state = viewModel.uiState.value
        assertFalse(state.isVolumeControlActive)
        assertNull(state.currentVolumePercent)
    }

    @Test
    fun testProcessUrl_doesNotModifyVolumeState() {
        viewModel.processUrl("")
        val state = viewModel.uiState.value
        assertFalse(state.isVolumeControlActive)
        assertNull(state.currentVolumePercent)
    }

    @Test
    fun testCycleVolume_firstClick_sets80PercentActive() {
        viewModel.cycleVolume()

        val state = viewModel.uiState.value
        assertTrue(state.isVolumeControlActive)
        assertEquals(80, state.currentVolumePercent)
        verify { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, any(), 0) }
    }

    @Test
    fun testCycleVolume_subsequentClicks_cycles85_90_80() {
        // 1st click -> 80%
        viewModel.cycleVolume()
        assertEquals(80, viewModel.uiState.value.currentVolumePercent)
        assertTrue(viewModel.uiState.value.isVolumeControlActive)

        // 2nd click -> 85%
        viewModel.cycleVolume()
        assertEquals(85, viewModel.uiState.value.currentVolumePercent)

        // 3rd click -> 90%
        viewModel.cycleVolume()
        assertEquals(90, viewModel.uiState.value.currentVolumePercent)

        // 4th click -> 80%
        viewModel.cycleVolume()
        assertEquals(80, viewModel.uiState.value.currentVolumePercent)
    }
}
