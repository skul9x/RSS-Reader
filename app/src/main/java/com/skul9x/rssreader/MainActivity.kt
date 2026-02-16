package com.skul9x.rssreader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.ScreenMode
import com.skul9x.rssreader.ui.feeds.FeedManagementScreen
import com.skul9x.rssreader.ui.logs.ActivityLogsScreen
import com.skul9x.rssreader.ui.main.MainScreen
import com.skul9x.rssreader.ui.main.MainScreenPortrait
import com.skul9x.rssreader.ui.settings.SettingsScreen
import com.skul9x.rssreader.ui.sharedlink.SharedLinkScreen
import com.skul9x.rssreader.ui.theme.RSSReaderTheme
import com.skul9x.rssreader.utils.ActivityLogger
import com.skul9x.rssreader.utils.DebugLogger
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Main Activity for RSS Reader.
 * Handles navigation, theme setup, screen orientation management, and share intent handling.
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        // Regex pattern to extract URLs from text
        private val URL_PATTERN = Regex("https?://[^\\s<>\"']+")
    }
    
    // Shared URL state - updated when receiving share intents
    private var pendingSharedUrl = mutableStateOf<String?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize ActivityLogger
        ActivityLogger.initialize(this)
        
        // Apply screen mode on startup
        applyScreenMode()
        
        // Handle initial intent (app launched via share)
        handleIntent(intent)

        setContent {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            
            RSSReaderTheme(
                darkTheme = true,
                useAMOLEDDark = isPortrait  // True black for AMOLED battery saving in portrait
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RssReaderApp(
                        onOrientationChange = { applyScreenMode() },
                        sharedUrl = pendingSharedUrl.value,
                        onSharedUrlConsumed = { pendingSharedUrl.value = null }
                    )
                }
            }
        }
    }
    
    /**
     * Handle new intent when app is already running (singleTask launchMode).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DebugLogger.log(TAG, "onNewIntent received: ${intent.action}")
        handleIntent(intent)
    }
    
    /**
     * Process intent to extract shared URL.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        DebugLogger.log(TAG, "handleIntent: action=${intent.action}, type=${intent.type}")
        
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    DebugLogger.log(TAG, "ACTION_SEND received: $sharedText")
                    sharedText?.let { extractUrlAndNavigate(it) }
                }
            }
            Intent.ACTION_VIEW -> {
                val url = intent.data?.toString()
                DebugLogger.log(TAG, "ACTION_VIEW received: $url")
                url?.let { extractUrlAndNavigate(it) }
            }
        }
    }
    
    /**
     * Extract URL from text and set it for navigation.
     */
    private fun extractUrlAndNavigate(text: String) {
        // Try to find a URL in the text
        val url = URL_PATTERN.find(text)?.value ?: text.takeIf { 
            it.startsWith("http://") || it.startsWith("https://") 
        }
        
        if (url != null) {
            DebugLogger.log(TAG, "Extracted URL: $url")
            pendingSharedUrl.value = url
        } else {
            DebugLogger.log(TAG, "No valid URL found in: $text")
        }
    }

    /**
     * Apply screen orientation based on user settings.
     */
    private fun applyScreenMode() {
        val prefs = AppPreferences.getInstance(this)
        requestedOrientation = when (prefs.getScreenMode()) {
            ScreenMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val keyCode = event.keyCode
        
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            // Handle media buttons - only intercept specific keys
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    DebugLogger.log("MainActivity", ">>> MEDIA_NEXT detected -> Starting Service with ACTION_NEXT")
                    val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                    intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_NEXT
                    startService(intent)
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    DebugLogger.log("MainActivity", ">>> MEDIA_PREVIOUS detected -> Starting Service with ACTION_PREVIOUS")
                    val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                    intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_PREVIOUS
                    startService(intent)
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    // Logic Gate Error Fix: Don't swallow PLAY if we can't handle it. 
                    // Let the system or other apps handle it if we are not reading.
                    DebugLogger.log("MainActivity", ">>> MEDIA_PLAY detected (passed through)")
                    return super.dispatchKeyEvent(event)
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    DebugLogger.log("MainActivity", ">>> MEDIA_PAUSE/STOP detected -> Starting Service with ACTION_STOP")
                    val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                    intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_STOP
                    startService(intent)
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    // Logic Gate Error Fix: Check if reading before deciding to STOP.
                    val isReading = com.skul9x.rssreader.service.NewsReaderService.serviceState.value.isReading
                    
                    if (isReading) {
                        DebugLogger.log("MainActivity", ">>> MEDIA_PLAY_PAUSE detected (Reading) -> Starting Service with ACTION_STOP")
                        val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                        intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_STOP
                        startService(intent)
                        return true
                    } else {
                        DebugLogger.log("MainActivity", ">>> MEDIA_PLAY_PAUSE detected (Not Reading) -> Passed through")
                        return super.dispatchKeyEvent(event)
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Root composable with navigation.
 * Automatically switches between landscape and portrait layouts.
 * Supports navigation to SharedLinkScreen when a URL is shared from another app.
 */
@Composable
fun RssReaderApp(
    onOrientationChange: () -> Unit = {},
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    
    // Detect current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Navigate to SharedLinkScreen when a URL is shared
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            DebugLogger.log("RssReaderApp", "Navigating to shared_link with URL: $sharedUrl")
            val encodedUrl = URLEncoder.encode(sharedUrl, "UTF-8")
            navController.navigate("shared_link/$encodedUrl") {
                // Don't create multiple instances
                launchSingleTop = true
            }
            onSharedUrlConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            // Automatically choose layout based on actual screen orientation
            if (isLandscape) {
                MainScreen(
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            } else {
                MainScreenPortrait(
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToFeeds = {
                    navController.navigate("feeds")
                },
                onOrientationSettingChanged = onOrientationChange,
                onNavigateToActivityLogs = {
                    navController.navigate("activity_logs")
                },
                onNavigateToFirebaseLog = {
                    navController.navigate("firebase_logs")
                },
                onNavigateToHtmlAnalyzer = {
                    navController.navigate("html_analyzer")
                }
            )
        }
        
        // Firebase Logs Screen
        composable("firebase_logs") {
            com.skul9x.rssreader.ui.settings.logs.FirebaseLogScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // HTML Analyzer Screen (Debug)
        composable("html_analyzer") {
            com.skul9x.rssreader.ui.settings.HtmlAnalyzerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("feeds") {
            FeedManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Activity Logs screen
        composable("activity_logs") {
            ActivityLogsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // NEW: Shared link screen for processing URLs from other apps
        composable("shared_link/{url}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val decodedUrl = try {
                URLDecoder.decode(encodedUrl, "UTF-8")
            } catch (e: Exception) {
                encodedUrl
            }
            
            SharedLinkScreen(
                url = decodedUrl,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}