package com.skul9x.rssreader.data.local

import android.content.Context
import android.content.SharedPreferences
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModelManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val prefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    
    private lateinit var modelManager: ModelManager

    @Before
    fun setUp() {
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor
        every { prefsEditor.remove(any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        
        // Reset singleton
        val instanceField = ModelManager::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)
        
        modelManager = ModelManager.getInstance(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getModels should trigger self-healing and return defaults if storage is empty`() = runBlocking {
        // Given: preferences return null (no dynamic models saved)
        every { sharedPrefs.getString("saved_models", null) } returns null
        
        // When
        val models = modelManager.getModels()
        
        // Then: SharedPreferences should be saved with default list
        verify { prefsEditor.putString("saved_models", any()) }
        assertEquals(5, models.size)
        assertEquals("models/gemini-3.5-flash-lite", models[0].modelId)
    }

    @Test
    fun `addModel should validate modelId format and append new model`() = runBlocking {
        // Given
        every { sharedPrefs.getString("saved_models", null) } returns null
        
        // When: Add invalid format model
        val invalidAdded = modelManager.addModel("gemini-invalid", "Invalid Name")
        // When: Add valid model
        val validAdded = modelManager.addModel("models/gemini-2.5-pro", "Gemini 2.5 Pro")
        
        // Then
        assertFalse(invalidAdded)
        assertTrue(validAdded)
        
        val models = modelManager.getModels()
        assertEquals(6, models.size)
        assertEquals("models/gemini-2.5-pro", models.last().modelId)
    }

    @Test
    fun `removeModel should execute self-healing if empty after deletion`() = runBlocking {
        // Given: Storage contains only one custom model
        val singleModelJson = """[{"modelId":"models/custom-model","displayName":"Custom"}]"""
        every { sharedPrefs.getString("saved_models", null) } returns singleModelJson
        
        // When: delete that only model
        val deleted = modelManager.removeModel("models/custom-model")
        
        // Then: deleted is true, list size is reset to defaults (5)
        assertTrue(deleted)
        val models = modelManager.getModels()
        assertEquals(5, models.size)
        assertEquals("models/gemini-3.5-flash-lite", models[0].modelId)
    }
}
