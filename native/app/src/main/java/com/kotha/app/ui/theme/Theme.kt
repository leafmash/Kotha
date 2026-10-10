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
    val bubbleMineEnd: Color,
    val brandStart: Color,
    val brandEnd: Color,
    val glow: Color,
    val lantern: Color,
    val onLantern: Color,
    val ripple: Color,
    val onBubbleMine: Color,
    val bubbleTheirs: Color,
    val onBubbleTheirs: Color,
    val chatBackground: Color,
    val tick: Color,
    val success: Color,
    val hairline: Color,
    val glassBar: Color,
    val missed: Color,
    val accentSoft: Color
)

private val DarkCovaColors = CovaColors(
    bubbleMine = Color(0xFF6B5CFF),
    bubbleMineEnd = Color(0xFF4A3DE0),
    brandStart = Color(0xFF9A8FFF),
    brandEnd = Color(0xFF5445EA),
    glow = Color(0xFF6C5CFF),
    lantern = Color(0xFFFFB547),
    onLantern = Color(0xFF241500),
    ripple = Color(0x0FB8B0FF),
    onBubbleMine = Color(0xFFFFFFFF),
    bubbleTheirs = Color(0xFF1B1F36),
    onBubbleTheirs = Color(0xFFEEF0FB),
    chatBackground = Color(0xFF080A14),
    tick = Color(0xFF7DF0C4),
    success = Color(0xFF45E0A4),
    hairline = Color(0x14FFFFFF),
    glassBar = Color(0xE6131627),
    missed = Color(0xFFFF6B7F),
    accentSoft = Color(0x299A8FFF)
)

private val LightCovaColors = CovaColors(
    bubbleMine = Color(0xFF6A5BF7),
    bubbleMineEnd = Color(0xFF4F3FE6),
    brandStart = Color(0xFF7B6CFF),
    brandEnd = Color(0xFF4F3FE6),
    glow = Color(0xFF8A7DFF),
    lantern = Color(0xFFFFB547),
    onLantern = Color(0xFF241500),
    ripple = Color(0x145B4BF5),
    onBubbleMine = Color(0xFFFFFFFF),
    bubbleTheirs = Color(0xFFFFFFFF),
    onBubbleTheirs = Color(0xFF14142B),
    chatBackground = Color(0xFFF2F0FA),
    tick = Color(0xFF8DF5CF),
    success = Color(0xFF14A06A),
    hairline = Color(0x14141436),
    glassBar = Color(0xF2FFFFFF),
    missed = Color(0xFFE23B52),
    accentSoft = Color(0x1F5645F0)
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
