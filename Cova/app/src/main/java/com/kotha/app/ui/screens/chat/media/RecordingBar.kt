package com.kotha.app.ui.screens.chat.media

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.media.RecordingState
import com.kotha.app.util.Format

@Composable
fun RecordingBar(
    state: RecordingState,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "recording-dot")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recording-dot-alpha"
    )
    val barColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.voice_cancel),
                tint = MaterialTheme.colorScheme.error
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = Format.duration(state.elapsedMs / 1000.0),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(12.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
        ) {
            val slots = 30
            val gap = 3.dp.toPx()
            val barWidth = ((size.width - gap * (slots - 1)) / slots).coerceAtLeast(1f)
            val padded = List(slots - state.levels.size.coerceAtMost(slots)) { 0f } + state.levels.takeLast(slots)
            padded.forEachIndexed { index, level ->
                val barHeight = size.height * level.coerceIn(0.08f, 1f)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onSend, modifier = Modifier.padding(end = 4.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.voice_send)
            )
        }
    }
}
