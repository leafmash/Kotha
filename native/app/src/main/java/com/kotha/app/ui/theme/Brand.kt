package com.kotha.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun brandBrush(): Brush {
    val colors = CovaTheme.colors
    return remember(colors.brandStart, colors.brandEnd) {
        Brush.linearGradient(listOf(colors.brandStart, colors.brandEnd))
    }
}

@Composable
fun bubbleBrush(): Brush {
    val colors = CovaTheme.colors
    return remember(colors.bubbleMine, colors.bubbleMineEnd) {
        Brush.linearGradient(listOf(colors.bubbleMine, colors.bubbleMineEnd))
    }
}

object CallPalette {
    val Night = Color(0xFF0A0B18)
    val Deep = Color(0xFF16134A)
    val Glow = Color(0xFF6C5CFF)
    val Accept = Color(0xFF2FBF76)
    val Reject = Color(0xFFE5484D)
}

@Composable
fun Modifier.coveBackdrop(): Modifier {
    val ripple = CovaTheme.colors.ripple
    return this.drawBehind {
        val origin = Offset(size.width * 0.12f, size.height * 1.04f)
        val step = 64.dp.toPx()
        val stroke = Stroke(width = 1.2.dp.toPx())
        for (i in 1..9) {
            drawCircle(color = ripple, radius = step * i, center = origin, style = stroke)
        }
    }
}
