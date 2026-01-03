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
    maxLines: Int = 3,
    useMarquee: Boolean = false,  // Enable scrolling text for portrait mode
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Tạo hiệu ứng "Thở" (Pulse) cho Alpha (độ mờ)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f, // Sáng rõ
        targetValue = 0.3f,  // Mờ đi chút
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing), // 1 giây mỗi nhịp
            repeatMode = RepeatMode.Reverse // Lặp lại kiểu gương (sáng -> mờ -> sáng)
        ),
        label = "pulseAlpha"
    )

    // 2. Quyết định màu viền và độ dày
    // Nếu đang chọn (Selected) -> Màu vàng rực
    // Nếu đang không chọn -> Trong suốt
    val baseBorderColor = if (isSelected) Color(0xFFFFD600) else Color.Transparent
    val borderWidth = if (isSelected) 4.dp else 0.dp
    
    // Nếu đang phát (isPlaying), ta áp dụng hiệu ứng pulseAlpha vào màu viền
    // Lưu ý: Chỉ pulse khi đang Selected VÀ đang Playing
    val finalBorderColor = if (isSelected && isPlaying) {
        baseBorderColor.copy(alpha = pulseAlpha)
    } else {
        baseBorderColor
    }

    // Các animation khác (Elevation, v.v.) giữ nguyên như cũ
    val targetElevation by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 4.dp,
        animationSpec = tween(300), 
        label = "elevation"
    )

    Card(
        modifier = modifier
            .border(
                width = borderWidth,
                color = finalBorderColor, // Dùng màu đã xử lý hiệu ứng
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = targetElevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                // Làm nền sáng hơn một chút so với cũ để tăng tương phản nội dung
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant // Dùng surfaceVariant để tách biệt với nền đen của app
            }
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
                    text = newsItem.title,
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPlaying,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Text("🔊 Đang đọc...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
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

