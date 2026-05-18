package com.skul9x.rssreader.ui.settings

import android.content.Context
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.ModelManager
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SummarizeModelViewModelTest {

    private val context = mockk<Context>(relaxed = true)
    private val modelManager = mockk<ModelManager>()
    private val appPreferences = mockk<AppPreferences>()

    @Before
    fun setUp() {
        mockkObject(ModelManager.Companion)
        mockkObject(AppPreferences.Companion)
        every { ModelManager.getInstance(any()) } returns modelManager
        every { AppPreferences.getInstance(any()) } returns appPreferences
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `when user triggers reset to default UI should receive updated default models`() = runBlocking {
        // Given
        val defaultModels = listOf(
            DynamicModel("models/gemini-3.1-flash-lite", "Gemini 3.1 Lite"),
            DynamicModel("models/gemini-2.5-flash-lite", "Gemini 2.5 Lite")
        )
        coEvery { modelManager.resetToDefaults() } just Runs
        coEvery { modelManager.getModels() } returns defaultModels

        // When: UI triggers Reset to Defaults
        modelManager.resetToDefaults()
        val modelsAfterReset = modelManager.getModels()

        // Then: Verifies manager reset was called and returns default models
        coVerify(exactly = 1) { modelManager.resetToDefaults() }
        assertEquals(2, modelsAfterReset.size)
        assertEquals("models/gemini-3.1-flash-lite", modelsAfterReset[0].modelId)
    }

    @Test
    fun `when user adds a valid model, model should be saved and returned`() = runBlocking {
        // Given
        val modelId = "models/gemini-2.5-pro"
        val displayName = "Gemini 2.5 Pro"
        coEvery { modelManager.addModel(modelId, displayName) } returns true
        coEvery { modelManager.getModels() } returns listOf(DynamicModel(modelId, displayName))

        // When
        val added = modelManager.addModel(modelId, displayName)
        val models = modelManager.getModels()

        // Then
        assertTrue(added)
        coVerify(exactly = 1) { modelManager.addModel(modelId, displayName) }
        assertEquals(1, models.size)
        assertEquals(modelId, models[0].modelId)
    }

    @Test
    fun `when user deletes a model, model should be removed`() = runBlocking {
        // Given
        val modelId = "models/gemini-2.5-pro"
        coEvery { modelManager.removeModel(modelId) } returns true
        coEvery { modelManager.getModels() } returns emptyList()

        // When
        val deleted = modelManager.removeModel(modelId)
        val models = modelManager.getModels()

        // Then
        assertTrue(deleted)
        coVerify(exactly = 1) { modelManager.removeModel(modelId) }
        assertTrue(models.isEmpty())
    }

    @Test
    fun `when user updates a model, model details should be updated`() = runBlocking {
        // Given
        val oldId = "models/old-model"
        val newId = "models/new-model"
        val newName = "New Model Name"
        coEvery { modelManager.updateModel(oldId, newId, newName) } returns true
        coEvery { modelManager.getModels() } returns listOf(DynamicModel(newId, newName))

        // When
        val updated = modelManager.updateModel(oldId, newId, newName)
        val models = modelManager.getModels()

        // Then
        assertTrue(updated)
        coVerify(exactly = 1) { modelManager.updateModel(oldId, newId, newName) }
        assertEquals(1, models.size)
        assertEquals(newId, models[0].modelId)
        assertEquals(newName, models[0].displayName)
    }
}
