package com.skul9x.rssreader.ui.components

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Structural tests for NewsCard animation optimization.
 *
 * KEY VERIFICATION:
 * 1. Animation target is conditional on isPlaying
 * 2. No unconditional targetValue = 0.3f (the old pattern)
 * 3. The isPlaying parameter still exists in the function signature
 */
class NewsCardAnimationTest {

    private val newsCardSource: String by lazy {
        val possiblePaths = listOf(
            "src/main/java/com/skul9x/rssreader/ui/components/NewsCard.kt",
            "app/src/main/java/com/skul9x/rssreader/ui/components/NewsCard.kt"
        )
        val file = possiblePaths
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?: possiblePaths
                .map { File(System.getProperty("user.dir"), it) }
                .firstOrNull { it.exists() }

        file?.readText() ?: error(
            "NewsCard.kt not found. Searched: ${possiblePaths.joinToString()}"
        )
    }

    @Test
    fun `NewsCard has isPlaying parameter`() {
        assertTrue(
            "NewsCard must accept isPlaying parameter",
            newsCardSource.contains("isPlaying: Boolean")
        )
    }

    @Test
    fun `animation targetValue is conditional on isPlaying`() {
        // The fix should contain a conditional expression like:
        // targetValue = if (isPlaying) 0.3f else 1.0f
        assertTrue(
            "pulseAlpha targetValue must be conditional on isPlaying",
            newsCardSource.contains("if (isPlaying)") &&
            newsCardSource.contains("0.3f") &&
            newsCardSource.contains("else 1.0f")
        )
    }

    @Test
    fun `no unconditional animation targetValue of 0_3f`() {
        // The old pattern was: targetValue = 0.3f (always animating)
        // After fix, 0.3f should only appear inside an if(isPlaying) block
        val lines = newsCardSource.lines()
        val targetValueLines = lines.filter {
            it.contains("targetValue") && it.contains("0.3f")
        }

        targetValueLines.forEach { line ->
            assertTrue(
                "targetValue = 0.3f must be conditional: $line",
                line.contains("if") || line.contains("isPlaying")
            )
        }
    }

    @Test
    fun `animationSpec is conditional on isPlaying`() {
        // Verify that animationSpec also changes based on isPlaying
        assertTrue(
            "animationSpec must be conditional on isPlaying",
            newsCardSource.contains("animationSpec = if (isPlaying)")
        )
    }
}
