package com.skul9x.rssreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skul9x.rssreader.data.model.NewsItem
import com.skul9x.rssreader.ui.theme.GlassBackground
import com.skul9x.rssreader.ui.theme.GlassBorder
import com.skul9x.rssreader.ui.theme.NeonGradient
import com.skul9x.rssreader.ui.theme.shimmerEffect

/**
 * Large, touch-friendly news card for car display.
 * Designed for minimal distraction while driving (High Glanceability).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsCard(
    newsItem: NewsItem,
    index: Int,
    isSelected: Boolean,
    isPlaying: Boolean,
    readingProgress: Float = 0f, // NEW: 0.0 to 1.0
    maxLines: Int = 3,
    useMarquee: Boolean = false,
    isGlassMode: Boolean = false, // NEW: Enable for Portrait Neon UI
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Tạo hiệu ứng "Thở" (Pulse) cho Alpha (độ mờ)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // 2. Logic Styling (Border & Background)
    // Tách biệt logic cho GlassMode (Portrait) và Standard (Landscape/Car)
    
    val targetElevation by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 4.dp,
        animationSpec = tween(300), 
        label = "elevation"
    )

    val cardModifier = if (isGlassMode) {
        // --- GLASS MODE LOGIC ---
        // Viền Neon Gradient khi select
        val borderModifier = if (isSelected) {
            Modifier.border(width = 2.dp, brush = NeonGradient, shape = RoundedCornerShape(16.dp))
        } else {
            Modifier.border(width = 1.dp, color = GlassBorder, shape = RoundedCornerShape(16.dp))
        }
        
        modifier
            .then(borderModifier)
            .clickable(onClick = onClick)
    } else {
        // --- STANDARD MODE LOGIC (Old) ---
        val baseBorderColor = if (isSelected) Color(0xFFFFD600) else Color.Transparent
        val borderWidth = if (isSelected) 4.dp else 0.dp
        
        // Pulse effect on border
        val finalBorderColor = if (isSelected && isPlaying) {
            baseBorderColor.copy(alpha = pulseAlpha)
        } else {
            baseBorderColor
        }
        
        modifier
            .border(
                width = borderWidth,
                color = finalBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    }

    val containerColor = if (isGlassMode) {
        GlassBackground // Semi-transparent black from ThemeExtensions
    } else {
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isGlassMode) 0.dp else targetElevation), // No elevation in Glass mode (flat look)
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp) // Tăng padding nội bộ lên 16dp cho thoáng
        ) {
            // Source name
            if (newsItem.feedName.isNotBlank()) {
                Text(
                    text = newsItem.feedName.uppercase(), // Viết hoa để dễ phân biệt với tiêu đề
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp // Tăng khoảng cách chữ
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Title text
            Box(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = newsItem.translatedTitle ?: newsItem.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = if (useMarquee) 1 else maxLines,  // Single line for marquee
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (useMarquee) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,  // Loop forever when selected
                            velocity = 30.dp  // Scrolling speed
                        )
                    } else {
                        Modifier
                    }
                )
            }
            
            // Status text - ALWAYS reserve space to prevent layout shift
            Box(modifier = Modifier.height(20.dp)) {
                if (newsItem.isTranslating) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Đang dịch...", 
                            fontSize = 12.sp, 
                            color = MaterialTheme.colorScheme.secondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPlaying,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        // Replace text with Progress Bar
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { readingProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton loading card that mimics the NewsCard layout.
 * Shows shimmer effect while news is loading.
 * Reduces cognitive load for drivers by indicating content structure.
 */
@Composable
fun SkeletonNewsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Simulate source name
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Simulate title - line 1
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Simulate title - line 2
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Simulate title - line 3
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Simulate additional content lines
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}

