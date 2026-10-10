package com.kotha.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kotha.app.ui.theme.rememberReducedMotion

@Composable
fun PulseRings(
    active: Boolean,
    color: Color,
    core: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val outer = core + 120.dp
    Box(modifier = modifier.size(outer), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(outer)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreRadius = core.toPx() / 2f
            val reach = (size.minDimension / 2f) - coreRadius
            val glow = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.32f), Color.Transparent),
                center = center,
                radius = coreRadius + reach
            )
            drawCircle(brush = glow, radius = coreRadius + reach, center = center)
            if (active && !reduced) {
                for (i in 0 until 3) {
                    val p = (phase + i / 3f) % 1f
                    drawCircle(
                        color = color.copy(alpha = (1f - p) * 0.55f),
                        radius = coreRadius + reach * p,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            } else {
                drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = coreRadius + 8.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        content()
    }
}
