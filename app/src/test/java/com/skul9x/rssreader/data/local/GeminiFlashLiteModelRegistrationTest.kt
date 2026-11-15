package com.skul9x.rssreader.data.local

import android.content.Context
import android.content.SharedPreferences
import com.skul9x.rssreader.data.network.gemini.DynamicModel
import com.skul9x.rssreader.data.network.gemini.GeminiModels
import com.skul9x.rssreader.data.network.gemini.SummarizeModel
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiFlashLiteModelRegistrationTest {

    private val context = mockk<Context>(relaxed = true)
    private val modelPrefs = mockk<SharedPreferences>(relaxed = true)
    private val modelEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val appPrefsShared = mockk<SharedPreferences>(relaxed = true)
    private val appPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    private lateinit var modelManager: ModelManager
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        every { context.applicationContext } returns context

        // Mock for ModelManager prefs
        every { context.getSharedPreferences("dynamic_models", Context.MODE_PRIVATE) } returns modelPrefs
        every { modelPrefs.edit() } returns modelEditor
        every { modelEditor.putString(any(), any()) } returns modelEditor
        every { modelEditor.remove(any()) } returns modelEditor
        every { modelEditor.apply() } just Runs

        // Mock for AppPreferences prefs
        every { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) } returns appPrefsShared
        every { appPrefsShared.edit() } returns appPrefsEditor
        every { appPrefsEditor.putString(any(), any()) } returns appPrefsEditor
        every { appPrefsEditor.apply() } just Runs

        // Reset ModelManager singleton
        val modelInstanceField = ModelManager::class.java.getDeclaredField("instance")
        modelInstanceField.isAccessible = true
        modelInstanceField.set(null, null)
        modelManager = ModelManager.getInstance(context)

        // Reset AppPreferences singleton
        val appPrefInstanceField = AppPreferences::class.java.getDeclaredField("instance")
        appPrefInstanceField.isAccessible = true
        appPrefInstanceField.set(null, null)
        appPreferences = AppPreferences.getInstance(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGeminiModels_containsFlashLiteLatest() {
        // 1. Enum value verification
        val modelEnum = GeminiModels.GEMINI_FLASH_LITE_LATEST
        assertEquals("models/gemini-flash-lite-latest", modelEnum.modelId)
        assertEquals("Gemini Flash Lite (Latest)", modelEnum.displayName)

        // 2. Lookup via fromModelId using GeminiModels alias
        val resolved = GeminiModels.fromModelId("models/gemini-flash-lite-latest")
        assertNotNull(resolved)
        assertEquals(GeminiModels.GEMINI_FLASH_LITE_LATEST, resolved)

        // 3. Lookup via fromModelId using SummarizeModel enum
        val resolvedFromSummarizeModel = SummarizeModel.fromModelId("models/gemini-flash-lite-latest")
        assertEquals(SummarizeModel.GEMINI_FLASH_LITE_LATEST, resolvedFromSummarizeModel)

        // 4. Lookup via MODEL_MAP cache
        val mapResolved = GeminiModels.MODEL_MAP["models/gemini-flash-lite-latest"]
        assertEquals(GeminiModels.GEMINI_FLASH_LITE_LATEST, mapResolved)

        // 5. Negative lookup
        assertNull(GeminiModels.fromModelId("models/non-existent-model"))
        assertNull(GeminiModels.fromModelId(null))
    }

    @Test
    fun testModelManager_defaultModelsIncludeFlashLiteLatest() = runBlocking {
        // 1. Static DEFAULT_MODELS verification
        val defaultList = ModelManager.DEFAULT_MODELS
        assertTrue(defaultList.any { it.modelId == "models/gemini-flash-lite-latest" })
        assertEquals("models/gemini-flash-lite-latest", defaultList.first().modelId)
        assertEquals("Gemini Flash Lite (Latest)", defaultList.first().displayName)

        // 2. Dynamic self-healing retrieval from empty storage
        every { modelPrefs.getString("saved_models", null) } returns null
        val models = modelManager.getModels()
        assertTrue(models.any { it.modelId == "models/gemini-flash-lite-latest" && it.displayName == "Gemini Flash Lite (Latest)" })

        // 3. Reset to defaults also contains the model
        modelManager.resetToDefaults()
        val resetModels = modelManager.getModels()
        assertTrue(resetModels.any { it.modelId == "models/gemini-flash-lite-latest" })
    }

    @Test
    fun testModelValidation_acceptsFlashLiteLatest() {
        // 1. Valid model ID checks on Companion
        assertTrue(ModelManager.isValidModelId("models/gemini-flash-lite-latest"))
        assertTrue(ModelManager.validateModelId("models/gemini-flash-lite-latest"))

        // 2. Valid model ID checks on instance
        assertTrue(modelManager.isValidModelId("models/gemini-flash-lite-latest"))
        assertTrue(modelManager.validateModelId("models/gemini-flash-lite-latest"))

        // 3. Invalid model ID edge cases
        assertFalse(ModelManager.isValidModelId(null))
        assertFalse(ModelManager.isValidModelId(""))
        assertFalse(ModelManager.isValidModelId("   "))
        assertFalse(ModelManager.isValidModelId("gemini-flash-lite-latest")) // missing models/ prefix
        assertFalse(ModelManager.isValidModelId("models/abc")) // length <= 10
        assertFalse(ModelManager.validateModelId("models/"))
    }

    @Test
    fun testAppPreferences_handlesFlashLiteLatest() {
        // 1. Setting and getting model ID directly
        appPreferences.setSelectedModelId("models/gemini-flash-lite-latest")
        verify { appPrefsEditor.putString("selected_summarize_model", "models/gemini-flash-lite-latest") }

        every { appPrefsShared.getString("selected_summarize_model", any()) } returns "models/gemini-flash-lite-latest"
        assertEquals("models/gemini-flash-lite-latest", appPreferences.getSelectedModelId())
        assertEquals(SummarizeModel.GEMINI_FLASH_LITE_LATEST, appPreferences.getSelectedSummarizeModel())

        // 2. Setting and getting via enum
        appPreferences.setSelectedSummarizeModel(SummarizeModel.GEMINI_FLASH_LITE_LATEST)
        verify { appPrefsEditor.putString("selected_summarize_model", "models/gemini-flash-lite-latest") }

        // 3. Backward compatibility migration from legacy enum string "GEMINI_FLASH_LITE_LATEST"
        every { appPrefsShared.getString("selected_summarize_model", any()) } returns "GEMINI_FLASH_LITE_LATEST"
        assertEquals("models/gemini-flash-lite-latest", appPreferences.getSelectedModelId())
        assertEquals(SummarizeModel.GEMINI_FLASH_LITE_LATEST, appPreferences.getSelectedSummarizeModel())
    }
}

