package com.skul9x.rssreader.data.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelQuotaManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var quotaManager: ModelQuotaManager

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.v(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        context = mockk()
        prefs = mockk()
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getString(any(), any()) } returns null // Empty start
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.clear() } returns editor

        quotaManager = ModelQuotaManager(context)
    }

    @Test
    fun `when markExhausted, isAvailable returns false`() = runBlocking {
        val model = "gemini-test"
        val key = "key-123"

        quotaManager.markExhausted(model, key)
        
        // Should be unavailable
        assertFalse("Should be unavailable after 429", quotaManager.isAvailable(model, key))
        
        // Persist should be called
        verify { editor.putString(any(), any()) }
        
        // Other combinations should be available
        assertTrue("Other key should be available", quotaManager.isAvailable(model, "other-key"))
        assertTrue("Other model should be available", quotaManager.isAvailable("other-model", key))
    }

    @Test
    fun `when markCooldown, isAvailable returns false`() = runBlocking {
        val model = "gemini-test"
        val key = "key-123"

        quotaManager.markCooldown(model, key)
        
        assertFalse("Should be unavailable after 503", quotaManager.isAvailable(model, key))
        
        // Other combinations should be available
        assertTrue(quotaManager.isAvailable(model, "other-key"))
    }
    
    @Test
    fun `clearAll resets everything`() = runBlocking {
        val model = "gemini-test"
        val key = "key-123"
        
        quotaManager.markExhausted(model, key)
        assertFalse(quotaManager.isAvailable(model, key))
        
        quotaManager.clearAll()
        
        assertTrue("Should be available after clear", quotaManager.isAvailable(model, key))
        verify { editor.clear() }
    }
}
