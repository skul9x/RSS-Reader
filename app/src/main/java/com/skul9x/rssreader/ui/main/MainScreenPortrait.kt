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
import com.skul9x.rssreader.ui.theme.BottomPanelGradient
import com.skul9x.rssreader.ui.theme.GlassBackground
import com.skul9x.rssreader.ui.theme.LivingBackground
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

    // Root container with Living Background
    Box(modifier = Modifier.fillMaxSize()) {
        LivingBackground()
        
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
            // Main Overlay Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                // 1. Content Layer (TopBar + List)
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            val isReadingAll = uiState.isReadAllMode
                            val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
                            val alpha by if (isReadingAll) {
                                infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.5f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "btnAlpha"
                                )
                            } else {
                                remember { mutableFloatStateOf(1f) }
                            }

                            IconButton(
                                onClick = { 
                                    if (isReadingAll) viewModel.stopSpeaking() 
                                    else viewModel.readAllNewsSummaries() 
                                },
                                enabled = uiState.newsItems.isNotEmpty() && !uiState.isSummarizing && !isSpeaking || isReadingAll,
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(alpha)
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

                    // News List Content
                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            uiState.isLoading -> {
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
                                            onClick = { viewModel.refreshNews(force = true) },
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
                                    contentPadding = PaddingValues(
                                        top = 8.dp, 
                                        start = 16.dp, 
                                        end = 16.dp, 
                                        bottom = 140.dp // Added padding for floating panel
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(uiState.newsItems) { index, news ->
                                        val isActiveItem = (index == selectedIndex && isSpeaking) || (index == uiState.readingNewsIndex)
                                        val isHighlighted = (index == selectedIndex) || (index == uiState.readingNewsIndex)
                                        
                                        // Check if this card is the resumable one
                                        // FIX: Check BOTH local isSpeaking AND service's isSummarizing
                                        // When resume starts, service sets isReading=true (maps to isSummarizing)
                                        val isCurrentlyReading = isSpeaking || uiState.isSummarizing
                                        val isResumableCard = uiState.hasResumableContent && !isCurrentlyReading && 
                                            index == uiState.resumableNewsIndex
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                        ) {
                                            NewsCard(
                                                newsItem = news,
                                                index = index,
                                                isSelected = isHighlighted,
                                                isPlaying = isActiveItem,
                                                readingProgress = if (index == uiState.readingNewsIndex) uiState.readingProgress else 0f,
                                                useMarquee = (index == selectedIndex),
                                                isGlassMode = true,
                                                onClick = { viewModel.selectNews(index) },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            
                                            // Resume overlay on resumable card
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = isResumableCard,
                                                enter = fadeIn(),
                                                exit = fadeOut(),
                                                modifier = Modifier.align(Alignment.TopCenter)
                                            ) {
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                    tonalElevation = 4.dp,
                                                    shadowElevation = 8.dp
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { viewModel.resumeReading() },
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.primary
                                                            ),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Tiếp tục",
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Tiếp tục",
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                        Text(
                                                            text = uiState.resumableNewsTitle?.take(20)?.plus("...") ?: "",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Bottom Control Panel (Floating Overlay)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(BottomPanelGradient) // Transparent Gradient
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
                            // Refresh button
                            IconButton(
                                onClick = { viewModel.refreshNews(force = true) },
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

                            // Read Full button
                            Button(
                                onClick = { viewModel.readFullArticle() },
                                enabled = selectedIndex >= 0 || uiState.readingNewsIndex >= 0,
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

                            // Stop/Replay button
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
                                Button(
                                    onClick = {
                                        uiState.currentSummary?.let { viewModel.ttsManager.speak(it) }
                                    },
                                    enabled = uiState.hasReadHistory && uiState.currentSummary != null,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
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


