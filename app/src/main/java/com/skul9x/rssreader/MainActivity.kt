package com.skul9x.rssreader

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skul9x.rssreader.data.local.AppPreferences
import com.skul9x.rssreader.data.local.ScreenMode
import com.skul9x.rssreader.ui.feeds.FeedManagementScreen
import com.skul9x.rssreader.ui.main.MainScreen
import com.skul9x.rssreader.ui.main.MainScreenPortrait
import com.skul9x.rssreader.ui.settings.SettingsScreen
import com.skul9x.rssreader.ui.theme.RSSReaderTheme

/**
 * Main Activity for RSS Reader.
 * Handles navigation, theme setup, and screen orientation management.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Apply screen mode on startup
        applyScreenMode()

        setContent {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            
            RSSReaderTheme(
                darkTheme = true,
                useAMOLEDDark = isPortrait  // True black for AMOLED battery saving in portrait
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RssReaderApp(onOrientationChange = { applyScreenMode() })
                }
            }
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
                    com.skul9x.rssreader.utils.DebugLogger.log("MainActivity", ">>> MEDIA_NEXT detected -> Starting Service with ACTION_NEXT")
                    val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                    intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_NEXT
                    startService(intent)
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    com.skul9x.rssreader.utils.DebugLogger.log("MainActivity", ">>> MEDIA_PREVIOUS detected -> Starting Service with ACTION_PREVIOUS")
                    val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                    intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_PREVIOUS
                    startService(intent)
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    // Logic Gate Error Fix: Don't swallow PLAY if we can't handle it. 
                    // Let the system or other apps handle it if we are not reading.
                    com.skul9x.rssreader.utils.DebugLogger.log("MainActivity", ">>> MEDIA_PLAY detected (passed through)")
                    return super.dispatchKeyEvent(event)
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    com.skul9x.rssreader.utils.DebugLogger.log("MainActivity", ">>> MEDIA_PAUSE/STOP detected -> Starting Service with ACTION_STOP")
                    val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                    intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_STOP
                    startService(intent)
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    // Logic Gate Error Fix: Check if reading before deciding to STOP.
                    val isReading = com.skul9x.rssreader.service.NewsReaderService.serviceState.value.isReading
                    
                    if (isReading) {
                        com.skul9x.rssreader.utils.DebugLogger.log("MainActivity", ">>> MEDIA_PLAY_PAUSE detected (Reading) -> Starting Service with ACTION_STOP")
                        val intent = android.content.Intent(this, com.skul9x.rssreader.service.NewsReaderService::class.java)
                        intent.action = com.skul9x.rssreader.service.NewsReaderService.ACTION_STOP
                        startService(intent)
                        return true
                    } else {
                        com.skul9x.rssreader.utils.DebugLogger.log("MainActivity", ">>> MEDIA_PLAY_PAUSE detected (Not Reading) -> Passed through")
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
 */
@Composable
fun RssReaderApp(onOrientationChange: () -> Unit = {}) {
    val navController = rememberNavController()
    
    // Detect current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                onOrientationSettingChanged = onOrientationChange
            )
        }

        composable("feeds") {
            FeedManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}