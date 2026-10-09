package com.kotha.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class CovaColors(
    val bubbleMine: Color,
    val onBubbleMine: Color,
    val bubbleTheirs: Color,
    val onBubbleTheirs: Color,
    val chatBackground: Color,
    val tick: Color,
    val success: Color
)

private val DarkCovaColors = CovaColors(
    bubbleMine = Color(0xFF4A3FD6),
    onBubbleMine = Color(0xFFFFFFFF),
    bubbleTheirs = Color(0xFF1D2138),
    onBubbleTheirs = Color(0xFFEEF0FB),
    chatBackground = Color(0xFF0A0C17),
    tick = Color(0xFF7C8CFF),
    success = Color(0xFF3DDC97)
)

private val LightCovaColors = CovaColors(
    bubbleMine = Color(0xFFE4E1FF),
    onBubbleMine = Color(0xFF17162B),
    bubbleTheirs = Color(0xFFFFFFFF),
    onBubbleTheirs = Color(0xFF17162B),
    chatBackground = Color(0xFFF4F2EC),
    tick = Color(0xFF5B4BF5),
    success = Color(0xFF1FA971)
)

private val LocalCovaColors = staticCompositionLocalOf { DarkCovaColors }

object CovaTheme {
    val colors: CovaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCovaColors.current
}

@Composable
fun CovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    CompositionLocalProvider(LocalCovaColors provides if (darkTheme) DarkCovaColors else LightCovaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CovaTypography,
            shapes = CovaShapes,
            content = content
        )
    }
}
