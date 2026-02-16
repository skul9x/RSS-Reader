package com.skul9x.rssreader.ui.sharedlink

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skul9x.rssreader.service.NewsReaderService

/**
 * Constants for SharedLinkScreen UI.
 */
private object SharedLinkScreenConstants {
    // Animation durations (ms)
    const val FADE_IN_DURATION = 300
    const val FADE_IN_SLOW_DURATION = 500
    const val PULSE_DURATION = 500
    
    // Opacity values
    const val CARD_ALPHA = 0.1f
    const val CARD_ALPHA_LIGHT = 0.05f
    const val ERROR_CARD_ALPHA = 0.2f
    const val TEXT_SECONDARY_ALPHA = 0.7f
    const val TEXT_PRIMARY_ALPHA = 0.9f
    
    // Colors
    val GRADIENT_START = Color(0xFF1a1a2e)
    val GRADIENT_MIDDLE = Color(0xFF16213e)
    val GRADIENT_END = Color(0xFF0f3460)
    val ACCENT_COLOR = Color(0xFF00d4ff)
    val SUCCESS_COLOR = Color(0xFF00e676)
    val ERROR_COLOR = Color(0xFFff5252)
}

/**
 * Screen to display and handle shared links from other apps.
 * Shows URL, loading state, summary, and TTS controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedLinkScreen(
    url: String,
    onNavigateBack: () -> Unit,
    viewModel: SharedLinkViewModel = viewModel()
) {
    // Process URL when screen loads or URL changes
    // Using 'url' as key ensures re-processing when navigating with different URL
    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            viewModel.processUrl(url)
        }
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val serviceState by NewsReaderService.serviceState.collectAsState()
    
    // Use constants for colors
    val gradientColors = listOf(
        SharedLinkScreenConstants.GRADIENT_START,
        SharedLinkScreenConstants.GRADIENT_MIDDLE,
        SharedLinkScreenConstants.GRADIENT_END
    )
    val accentColor = SharedLinkScreenConstants.ACCENT_COLOR
    val successColor = SharedLinkScreenConstants.SUCCESS_COLOR
    val errorColor = SharedLinkScreenConstants.ERROR_COLOR
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Link được chia sẻ",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradientColors))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // URL Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = SharedLinkScreenConstants.CARD_ALPHA)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "🔗 URL",
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.url.ifBlank { url },
                            color = Color.White.copy(alpha = SharedLinkScreenConstants.TEXT_PRIMARY_ALPHA),
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Title Card (if available)
                uiState.title?.let { title ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = SharedLinkScreenConstants.CARD_ALPHA_LIGHT)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "📰 Tiêu đề",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Loading State
                AnimatedVisibility(
                    visible = uiState.isLoading,
                    enter = fadeIn(animationSpec = tween(SharedLinkScreenConstants.FADE_IN_DURATION)),
                    exit = fadeOut(animationSpec = tween(SharedLinkScreenConstants.FADE_IN_DURATION))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = SharedLinkScreenConstants.CARD_ALPHA)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = accentColor,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Đang xử lý...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Trích xuất nội dung và tóm tắt bằng AI",
                                color = Color.White.copy(alpha = SharedLinkScreenConstants.TEXT_SECONDARY_ALPHA),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                
                // Error State
                uiState.error?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = errorColor.copy(alpha = SharedLinkScreenConstants.ERROR_CARD_ALPHA)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = error,
                                    color = errorColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.processUrl(url) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Thử lại")
                            }
                        }
                    }
                }
                
                // Summary Result
                AnimatedVisibility(
                    visible = uiState.summary != null,
                    enter = fadeIn(animationSpec = tween(500)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Action Buttons (moved to top)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Read Summary Button
                            Button(
                                onClick = { viewModel.readSummary() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !serviceState.isReading
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Đọc tóm tắt",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Stop Button (when reading)
                            if (serviceState.isReading) {
                                Button(
                                    onClick = { viewModel.stopReading() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = errorColor
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dừng",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        
                        // Read Full Content Button
                        if (uiState.originalContent != null) {
                            OutlinedButton(
                                onClick = { viewModel.readFullContent() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !serviceState.isReading
                            ) {
                                Text("📖 Đọc toàn bộ nội dung")
                            }
                        }
                        
                        // Summary Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = successColor.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✨",
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Tóm tắt AI",
                                        color = successColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = uiState.summary ?: "",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                }
                
                // Reading Indicator
                if (serviceState.isReading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = accentColor.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing indicator
                            val alpha by animateFloatAsState(
                                targetValue = if (serviceState.isReading) 1f else 0.3f,
                                animationSpec = tween(500),
                                label = "pulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = alpha))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Đang đọc...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Bottom spacer
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
