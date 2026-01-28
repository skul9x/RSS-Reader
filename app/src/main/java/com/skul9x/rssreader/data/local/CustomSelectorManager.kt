package com.skul9x.rssreader.data.local
import android.content.Context
import android.content.SharedPreferences
import java.net.URI
import com.skul9x.rssreader.data.local.CustomSelectorManager.Companion.KEY_SELECTORS
import org.json.JSONObject

/**
 * Manages custom CSS selectors for specific domains.
 * Allows the user to override the default extraction logic for specific websites.
 */
class CustomSelectorManager private constructor(context: Context) {

    companion object {
        private const val PREF_NAME = "custom_selectors_prefs"
        const val KEY_SELECTORS = "domain_selectors"
        
        @Volatile
        private var INSTANCE: CustomSelectorManager? = null

        fun getInstance(context: Context): CustomSelectorManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomSelectorManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // In-memory cache: Domain -> Selector (e.g., "giaoducthoidai.vn" -> ".article__body")
    private var selectorLogin: MutableMap<String, String> = mutableMapOf()

    init {
        loadSelectors()
    }

    private fun loadSelectors() {
        val jsonString = prefs.getString(KEY_SELECTORS, null)
        if (jsonString != null) {
            try {
                val jsonObject = JSONObject(jsonString)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = jsonObject.getString(key)
                    selectorLogin[key] = value
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveSelectors() {
        val jsonObject = JSONObject()
        selectorLogin.forEach { (key, value) ->
            try {
                jsonObject.put(key, value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        prefs.edit().putString(KEY_SELECTORS, jsonObject.toString()).apply()
    }

    /**
     * Get the custom selector for a given URL's domain.
     */
    fun getSelectorForUrl(url: String): String? {
        return try {
            val domain = URI(url).host?.removePrefix("www.")
            domain?.let { selectorLogin[it] }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save a custom selector for a URL's domain.
     */
    fun addSelector(url: String, selector: String) {
        try {
            val domain = URI(url).host?.removePrefix("www.")
            if (domain != null) {
                selectorLogin[domain] = selector
                saveSelectors()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Remove a custom selector for a URL's domain.
     */
    fun removeSelector(url: String) {
        try {
            val domain = URI(url).host?.removePrefix("www.")
            if (domain != null) {
                selectorLogin.remove(domain)
                saveSelectors()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Get extracted domain string for display
     */
    fun getDomain(url: String): String {
        return try {
            URI(url).host?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }
    }
}
