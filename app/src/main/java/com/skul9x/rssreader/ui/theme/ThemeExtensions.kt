package com.skul9x.rssreader.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// --- Colors ---
val NeonCyan = Color(0xFF00FFFF)
val NeonPurple = Color(0xFF9D00FF)

val GlassBackground = Color(0xFF202020).copy(alpha = 0.7f) // Dark Gray for better contrast
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.3f) // More visible border

// --- Brushes ---
val NeonGradient = Brush.linearGradient(
    colors = listOf(NeonCyan, NeonPurple)
)

val BottomPanelGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.8f), // Fade to dark
        Color.Black.copy(alpha = 0.95f) // Almost solid at bottom
    )
)

// --- Modifiers ---

/**
 * Applies a Neon Gradient Border.
 */
fun Modifier.neonBorder(
    width: Dp = 2.dp,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    brush: Brush = NeonGradient
): Modifier = this.border(width, brush, shape)

// --- Composables ---

/**
 * A "Living" background with slowly moving gradient blobs.
 */
@Composable
fun LivingBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "living_bg")

    // Animate offsets for a dynamic feel
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "offset1"
    )
    
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 1000f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse),
        label = "offset2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050505)) // Deep black base
            .drawBehind {
                // Draw a subtle moving radial gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1A103C), Color.Transparent),
                        center = center + Offset(
                            x = cos(offset1 / 500) * 300,
                            y = sin(offset1 / 500) * 300
                        ),
                        radius = size.width * 0.8f
                    )
                )
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF002233), Color.Transparent),
                        center = center + Offset(
                            x = sin(offset2 / 600) * 400,
                            y = cos(offset2 / 600) * 400
                        ),
                        radius = size.width * 0.7f
                    )
                )
            }
    )
}
