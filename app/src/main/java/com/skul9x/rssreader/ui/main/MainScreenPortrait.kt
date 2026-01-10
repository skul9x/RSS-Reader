package com.skul9x.rssreader.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.skul9x.rssreader.ui.components.NewsCard
import com.skul9x.rssreader.ui.components.SkeletonNewsCard
import kotlinx.coroutines.delay

/**
 * Main screen optimized for portrait phone display.
 * Uses LazyColumn for vertical scrolling and bottom control panel for one-handed operation.
 */
@Composable
fun MainScreenPortrait(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
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
                // Custom styled snackbar for portrait display
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = paddingValues.calculateBottomPadding()) // Only apply bottom padding (NavBar), let Header handle Top (StatusBar)
        ) {
            // 1. Top Bar: Title + Settings - Sát status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)  // Chỉ tránh status bar
                    .padding(horizontal = 16.dp, vertical = 8.dp),  // Giảm vertical padding
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📰 Tin Tức",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                // Action Buttons Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logic xác định trạng thái đang đọc 5 tin (dùng isReadAllMode thay vì readingNewsIndex)
                    val isReadingAll = uiState.isReadAllMode
                    
                    // Hiệu ứng nhấp nháy khi đang đọc series (chậm hơn: 1200ms)
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

                    // Nút "Đọc 5 tin" - Toggle behavior
                    IconButton(
                        onClick = { 
                            if (isReadingAll) viewModel.stopSpeaking() 
                            else viewModel.readAllNewsSummaries() 
                        },
                        enabled = uiState.newsItems.isNotEmpty() && !uiState.isSummarizing && !isSpeaking || isReadingAll, // Always enabled when reading
                        modifier = Modifier
                            .size(48.dp)
                            .alpha(alpha) // Áp dụng hiệu ứng nhấp nháy
                            .background(
                                color = if (isReadingAll) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "Đọc 5 tin",
                            tint = if (isReadingAll) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
            }

            // 2. News List Content (LazyColumn for vertical scrolling)
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        // Skeleton loading - shows structure while loading
                        SkeletonNewsListPortrait()
                    }
                    uiState.error != null && uiState.newsItems.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "⚠️", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = uiState.error ?: "Lỗi không xác định",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.refreshNews() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Thử lại")
                                }
                            }
                        }
                    }
                    uiState.newsItems.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "📡", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Chưa có nguồn tin",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Thêm nguồn RSS để bắt đầu",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onNavigateToSettings,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cài đặt")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(uiState.newsItems) { index, news ->
                                // Determine if this card should show pulse effect
                                // Pulse when: (single mode AND selected AND speaking) OR (in read-all mode AND is the current reading item)
                                val isActiveItem = (index == selectedIndex && isSpeaking) || (index == uiState.readingNewsIndex)
                                val isHighlighted = (index == selectedIndex) || (index == uiState.readingNewsIndex)
                                
                                NewsCard(
                                    newsItem = news,
                                    index = index,
                                    isSelected = isHighlighted,
                                    isPlaying = isActiveItem,
                                    useMarquee = (index == selectedIndex),  // Only scroll text on selected card
                                    onClick = { viewModel.selectNews(index) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bottom Control Panel (Fixed at bottom for thumb-friendly operation)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {


                    // Control buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Refresh button (small)
                        IconButton(
                            onClick = { viewModel.refreshNews() },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Làm mới",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Read Full button (medium) - enabled when has selection OR reading
                        Button(
                            onClick = { viewModel.readFullArticle() },
                            enabled = selectedIndex >= 0 || uiState.readingNewsIndex >= 0,  // FIX: Enable when reading too
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Đọc Full", fontSize = 14.sp)
                        }

                        // Stop/Replay button (large - main action)
                        // Show Stop when speaking OR summarizing (waiting for API)
                        if (isSpeaking || uiState.isSummarizing) {
                            Button(
                                onClick = { viewModel.stopSpeaking() },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Dừng", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            // Replay button - enabled only when hasReadHistory AND has summary
                            Button(
                                onClick = {
                                    uiState.currentSummary?.let { viewModel.ttsManager.speak(it) }
                                },
                                enabled = uiState.hasReadHistory && uiState.currentSummary != null,  // FIX: Require read history
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant  // Dim when disabled
                                ),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Đọc lại", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Skeleton loading list for portrait mode showing 5 shimmer cards.
 */
@Composable
private fun SkeletonNewsListPortrait() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            SkeletonNewsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }
    }
}


