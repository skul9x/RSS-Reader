package com.skul9x.rssreader.data.local

import android.content.Context
import android.content.SharedPreferences
import com.skul9x.rssreader.data.network.gemini.SummarizeModel
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AppPreferencesTest {
    private val context = mockk<Context>(relaxed = true)
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private lateinit var appPreferences: AppPreferences

    @Before
    fun setup() {
        every { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) } returns sharedPrefs
        every { sharedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        
        // Use reflection to reset the singleton instance for testing
        val instanceField = AppPreferences::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)
        
        appPreferences = AppPreferences.getInstance(context)
    }

    @Test
    fun `setSelectedSummarizeModel should save the model name to shared preferences`() {
        val model = SummarizeModel.GEMINI_3_FLASH_PREVIEW
        
        appPreferences.setSelectedSummarizeModel(model)
        
        // Verify that it calls putString with the correct key and model name
        verify { editor.putString("selected_summarize_model", model.name) }
        verify { editor.apply() }
    }

    @Test
    fun `getSelectedSummarizeModel should retrieve the saved model from shared preferences`() {
        val model = SummarizeModel.GEMINI_3_FLASH_PREVIEW
        every { sharedPrefs.getString("selected_summarize_model", any()) } returns model.name
        
        val retrievedModel = appPreferences.getSelectedSummarizeModel()
        
        assertEquals(model, retrievedModel)
    }

    @Test
    fun `getSelectedSummarizeModel should return default model if none is saved`() {
        val defaultModel = SummarizeModel.GEMINI_2_5_FLASH_LITE
        every { sharedPrefs.getString("selected_summarize_model", any()) } returns null
        
        val retrievedModel = appPreferences.getSelectedSummarizeModel()
        
        assertEquals(defaultModel, retrievedModel)
    }
}
