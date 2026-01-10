package com.skul9x.rssreader.ui.theme

import android.app.Activity
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// High contrast colors for daylight readability in car (Landscape mode)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),           // Light blue
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004D63),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF81C784),          // Light green
    onSecondary = Color(0xFF0A3A16),
    secondaryContainer = Color(0xFF1F5128),
    onSecondaryContainer = Color(0xFFA6F5A8),
    tertiary = Color(0xFFFFB74D),           // Amber
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF643F00),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF0D1B2A),         // Deep navy
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1B263B),            // Dark blue-gray
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF2C3E50),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// TRUE AMOLED Dark Mode - Pure black for maximum battery saving (Portrait mode)
private val OLEDDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),           // Light blue
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF1A3A4A),  // Darker container
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF81C784),          // Light green
    onSecondary = Color(0xFF0A3A16),
    secondaryContainer = Color(0xFF0F2A14),
    onSecondaryContainer = Color(0xFFA6F5A8),
    tertiary = Color(0xFFFFB74D),           // Amber
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF3A2500),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color.Black,               // TRUE BLACK for AMOLED
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF0A0A0A),            // Near-black surface
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF1A1A1A),     // Very dark gray
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C0000),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0277BD),            // Strong blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF388E3C),          // Green
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF002105),
    tertiary = Color(0xFFF57C00),           // Orange
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF2D1600),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

/**
 * Typography optimized for car displays - large, readable text.
 */
object CarTypography {
    val headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp
    )
    val headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp
    )
    val titleLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 32.sp
    )
    val titleMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp
    )
    val bodyLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 26.sp
    )
    val bodyMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    )
    val labelLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp
    )
}

@Composable
fun RSSReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useAMOLEDDark: Boolean = false,  // TRUE for portrait mode (AMOLED battery saving)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme && useAMOLEDDark -> OLEDDarkColorScheme  // Portrait: True AMOLED black
        darkTheme -> DarkColorScheme                      // Landscape: Deep navy
        else -> LightColorScheme
    }
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val typography = androidx.compose.material3.Typography(
        headlineLarge = CarTypography.headlineLarge,
        headlineMedium = CarTypography.headlineMedium,
        titleLarge = CarTypography.titleLarge,
        titleMedium = CarTypography.titleMedium,
        bodyLarge = CarTypography.bodyLarge,
        bodyMedium = CarTypography.bodyMedium,
        labelLarge = CarTypography.labelLarge
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

/**
 * Shimmer effect modifier for skeleton loading.
 * Creates a shimmering gradient animation effect.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        ),
        label = "ShimmerOffset"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFB8B5B5), // Light gray
                Color(0xFF8F8B8B), // Darker gray (highlight)
                Color(0xFFB8B5B5),
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    )
        .onGloballyPositioned {
            size = it.size
        }
}

