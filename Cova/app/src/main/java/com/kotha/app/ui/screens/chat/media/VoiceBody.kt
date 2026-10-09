package com.kotha.app.ui.screens.chat.media

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.data.media.VoiceState
import com.kotha.app.data.model.Message
import com.kotha.app.data.model.UploadStage
import com.kotha.app.util.Format
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceBody(
    message: Message,
    content: Color,
    selecting: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    callbacks: MediaCallbacks
) {
    val upload = message.upload
    val uploading = upload != null && (message.url.isEmpty() || upload.stage == UploadStage.Failed)
    val source = message.url.ifEmpty {
        upload?.localPath?.takeIf { it.isNotEmpty() }?.let { Uri.fromFile(File(it)).toString() }.orEmpty()
    }
    val live: VoiceState? by remember(message.id) { callbacks.voice.observe(message.id) }
        .collectAsStateWithLifecycle(initialValue = null)
    val state = live
    val playing = state?.playing == true
    val positionMs = state?.positionMs ?: 0L
    val totalMs = if (state != null && state.durationMs > 0) state.durationMs else (message.duration * 1000).toLong()
    val progress = if (totalMs > 0) (positionMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    val bars = remember(message.id, message.wave, message.url) {
        VoiceWave.resolve(message.wave, message.url.ifEmpty { message.id })
    }
    val shownSeconds = if (positionMs > 0) positionMs / 1000.0 else totalMs / 1000.0
    Row(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (upload != null && uploading) {
            UploadButton(
                upload = upload,
                onCancel = { callbacks.onCancel(message.id) },
                onRetry = { callbacks.onRetry(message.id) },
                tint = content,
                background = content.copy(alpha = 0.12f),
                size = 40.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.14f))
                    .clickable(role = Role.Button) {
                        if (selecting) onTap() else callbacks.voice.toggle(message.id, source)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.voice_pause else R.string.voice_play),
                    modifier = Modifier.size(26.dp),
                    tint = content
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WaveBars(
                bars = bars,
                progress = progress,
                color = content,
                enabled = !uploading && !selecting && source.isNotEmpty(),
                onSeek = { callbacks.voice.seek(message.id, source, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Format.duration(shownSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f)
                )
                if (state != null && (playing || positionMs > 0)) {
                    Text(
                        text = speedLabel(state.speed),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(content.copy(alpha = 0.14f))
                            .clickable(role = Role.Button) {
                                if (selecting) onTap() else callbacks.voice.cycleSpeed()
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = content
                    )
                }
            }
        }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

@Composable
private fun WaveBars(
    bars: List<Float>,
    progress: Float,
    color: Color,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableIntStateOf(1) }
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier = modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { offset -> seek((offset.x / width).coerceIn(0f, 1f)) }
                }
            }
    ) {
        val count = bars.size
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val filled = progress * count
        bars.forEachIndexed { index, value ->
            val barHeight = size.height * (value.coerceIn(14f, 100f) / 100f)
            drawRoundRect(
                color = if (index < filled) color else color.copy(alpha = 0.35f),
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
