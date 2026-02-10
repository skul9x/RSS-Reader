package com.skul9x.rssreader.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

import com.skul9x.rssreader.ui.components.NewsCard
import com.skul9x.rssreader.ui.components.SkeletonNewsCard
import kotlinx.coroutines.delay

/**
 * Main screen optimized for landscape car display.
 * Shows 5 news cards with TTS controls.
 * Supports media buttons from car steering wheel (Next/Previous).
 */
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedIndex by viewModel.selectedNewsIndex.collectAsState()
    val isSpeaking by viewModel.ttsManager.isSpeaking.collectAsState()

    // Use rememberUpdatedState to fix stale closure issue
    // These ensure callbacks always reference the latest values
    val currentOnPlayPause by rememberUpdatedState {
        if (isSpeaking) {
            viewModel.stopSpeaking()
        } else {
            uiState.currentSummary?.let { viewModel.ttsManager.speak(it) }
        }
    }

    // Media Session is now fully managed by NewsReaderService.
    // UI simply reacts to ViewModel state which mirrors the Service state.

    // Snackbar host state for error display
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle error with indefinite Snackbar (safer for drivers)
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMsg ->
            if (uiState.newsItems.isNotEmpty()) {
                // Show indefinite snackbar - stays until user dismisses
                val result = snackbarHostState.showSnackbar(
                    message = errorMsg,
                    actionLabel = "ĐÓNG",
                    duration = SnackbarDuration.Indefinite,
                    withDismissAction = false
                )
                // When user taps "ĐÓNG", clear the error
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.clearError()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                // Custom styled snackbar for car display
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // News cards area at the TOP
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                when {
                    uiState.isLoading -> {
                        // Skeleton loading - shows structure while loading
                        SkeletonNewsList()
                    }
                    uiState.error != null && uiState.newsItems.isEmpty() -> {
                        ErrorMessage(
                            message = uiState.error!!,
                            onRetry = { viewModel.refreshNews(force = true) }
                        )
                    }
                    uiState.newsItems.isEmpty() -> {
                        EmptyState(onSettings = onNavigateToSettings)
                    }
                    else -> {
                        NewsList(
                            newsItems = uiState.newsItems,
                            selectedIndex = selectedIndex,
                            readingIndex = uiState.readingNewsIndex,
                            isSpeaking = isSpeaking,
                            isSummarizing = uiState.isSummarizing,  // FIX: Pass service reading state
                            readingProgress = uiState.readingProgress,
                            hasResumableContent = uiState.hasResumableContent,
                            resumableNewsIndex = uiState.resumableNewsIndex,
                            onSelectNews = { index -> viewModel.selectNews(index) },
                            onResume = { viewModel.resumeReading() }
                        )
                    }
                }
            }



            Spacer(modifier = Modifier.height(6.dp))

            // Bottom controls - Refresh + 5 tin on left, TTS buttons in center, WiFi + Settings on right
            BottomControls(
                isLoading = uiState.isLoading,
                isSpeaking = isSpeaking,
                isSummarizing = uiState.isSummarizing,
                isEnabled = uiState.currentSummary != null,
                hasSelection = selectedIndex >= 0,
                hasNews = uiState.newsItems.isNotEmpty(),
                isReadingAll = uiState.isReadAllMode, // Use proper mode flag instead of readingNewsIndex
                hasReadHistory = uiState.hasReadHistory,  // NEW: Pass hasReadHistory for Replay button
                hasResumableContent = uiState.hasResumableContent,  // NEW: Resume support
                resumableNewsTitle = uiState.resumableNewsTitle,     // NEW: Resume support
                onRefresh = { viewModel.refreshNews(force = true) },
                onReadAll5 = { viewModel.readAllNewsSummaries() },
                onWifi = {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    context.startActivity(intent)
                },
                onStop = { viewModel.stopSpeaking() },
                onSpeak = {
                    uiState.currentSummary?.let { viewModel.ttsManager.speak(it) }
                },
                onReadFull = { viewModel.readFullArticle() },
                onSettings = onNavigateToSettings,
                onResume = { viewModel.resumeReading() }  // NEW: Resume callback
            )
        }
    }
}

@Composable
private fun TopBar(
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title
        Text(
            text = "📰 Tin Tức",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Settings button only
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Cài đặt nguồn RSS",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NewsList(
    newsItems: List<com.skul9x.rssreader.data.model.NewsItem>,
    selectedIndex: Int,
    readingIndex: Int,
    isSpeaking: Boolean,
    isSummarizing: Boolean,  // FIX: Service reading state for proper resume button hiding
    readingProgress: Float,
    hasResumableContent: Boolean,
    resumableNewsIndex: Int,
    onSelectNews: (Int) -> Unit,
    onResume: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to reading item
    LaunchedEffect(readingIndex) {
        if (readingIndex >= 0) {
            listState.animateScrollToItem(readingIndex)
        }
    }

    LazyRow(
        state = listState, // Attach state
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        itemsIndexed(newsItems) { index, news ->
            val isCardPlaying = (index == selectedIndex) && isSpeaking
            val isHighlighted = (index == selectedIndex) || (index == readingIndex)
            val showPulse = isCardPlaying || (index == readingIndex)
            
            // Check if this card is the resumable one
            // FIX: Check BOTH local isSpeaking AND service's isSummarizing
            val isCurrentlyReading = isSpeaking || isSummarizing
            val isResumableCard = hasResumableContent && !isCurrentlyReading && 
                index == resumableNewsIndex

            Box(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            ) {
                NewsCard(
                    newsItem = news,
                    index = index,
                    isSelected = isHighlighted,
                    isPlaying = showPulse,
                    readingProgress = if (index == readingIndex) readingProgress else 0f,
                    maxLines = 6,
                    onClick = { onSelectNews(index) },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Resume overlay on resumable card
                AnimatedVisibility(
                    visible = isResumableCard,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    // Glass overlay with resume button and title
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Resume button
                            Button(
                                onClick = onResume,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Tiếp tục",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tiếp tục",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // Title text (optional - show abbreviated title or omit)
                            // Removed: resumableNewsTitle reference since we now use index
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun BottomControls(
    isLoading: Boolean,
    isSpeaking: Boolean,
    isSummarizing: Boolean,
    isEnabled: Boolean,
    hasSelection: Boolean,
    hasNews: Boolean,
    isReadingAll: Boolean, // New parameter
    hasReadHistory: Boolean, // NEW: Track if user has read anything
    hasResumableContent: Boolean, // NEW: Resume support
    resumableNewsTitle: String?,   // NEW: Resume support
    onRefresh: () -> Unit,
    onReadAll5: () -> Unit,
    onWifi: () -> Unit,
    onStop: () -> Unit,
    onSpeak: () -> Unit,
    onReadFull: () -> Unit,
    onSettings: () -> Unit,
    onResume: () -> Unit  // NEW: Resume callback
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Refresh + "Tóm tắt 5 tin" buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Refresh button
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Làm mới",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            // NEW: "Tóm tắt 5 tin" button (replaces WiFi position)
            // Blinking effect when reading all (slower blink: 1200ms)
            val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
            val alpha by if (isReadingAll) {
                infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200), // Slower blink (was 500ms)
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "btnAlpha"
                )
            } else {
                remember { mutableFloatStateOf(1f) }
            }

            Button(
                // Toggle behavior: if reading, stop; otherwise start
                onClick = { if (isReadingAll) onStop() else onReadAll5() },
                enabled = hasNews && !isSummarizing && !isSpeaking || isReadingAll, // Always enabled when reading
                modifier = Modifier
                    .height(56.dp)
                    .alpha(alpha), // Apply blinking alpha
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReadingAll) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    disabledContainerColor = if (isReadingAll) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = if (isReadingAll) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = "5 tin",
                    tint = if (isReadingAll) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // TTS controls in CENTER - Responsive using Weight
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Read Full button
            Button(
                onClick = onReadFull,
                enabled = hasSelection || isReadingAll, // Enable if selected OR reading all
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f, fill = false)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Đọc full",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Stop button - enabled when speaking OR summarizing
            Button(
                onClick = onStop,
                enabled = isSpeaking || isSummarizing,  // FIX: Also enable when summarizing (waiting for API)
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f, fill = false)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Dừng",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Replay button - enabled when NOT speaking/summarizing AND has read history
            Button(
                onClick = onSpeak,
                enabled = !isSpeaking && !isSummarizing && hasReadHistory && isEnabled,  // FIX: Proper conditions
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f, fill = false)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Đọc lại",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Right side: WiFi + Settings buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // WiFi Settings button (moved from left)
            IconButton(
                onClick = onWifi,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Cài đặt WiFi",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Settings button
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Skeleton loading list showing 5 shimmer cards.
 * Simulates the news list structure during loading.
 */
@Composable
private fun SkeletonNewsList() {
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        items(5) {
            SkeletonNewsCard(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun EmptyState(onSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📡",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chưa có nguồn tin",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Thêm nguồn RSS để bắt đầu",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSettings,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cài đặt nguồn RSS", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠️",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Thử lại", fontSize = 16.sp)
        }
    }
}


