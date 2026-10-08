package com.kotha.app.ui.screens.chat.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kotha.app.R
import com.kotha.app.data.model.Message
import com.kotha.app.data.model.UploadStage
import com.kotha.app.util.Format
import java.io.File

internal val MediaWidth = 240.dp

private fun ratioOf(message: Message): Float =
    if (message.width > 0 && message.height > 0) {
        (message.width.toFloat() / message.height).coerceIn(0.6f, 1.7f)
    } else {
        4f / 3f
    }

private fun showsOverlay(message: Message): Boolean {
    val upload = message.upload ?: return false
    return message.url.isEmpty() || upload.stage == UploadStage.Failed
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VisualBody(
    message: Message,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    callbacks: MediaCallbacks
) {
    val upload = message.upload
    val video = message.type == "video"
    val source: Any? = when {
        video -> upload?.thumbPath?.takeIf { it.isNotEmpty() }?.let { File(it) }
            ?: message.thumb.takeIf { it.isNotEmpty() }
        upload != null && upload.localPath.isNotEmpty() -> File(upload.localPath)
        else -> message.url.takeIf { it.isNotEmpty() }
    }
    Box(
        modifier = Modifier
            .width(MediaWidth)
            .aspectRatio(ratioOf(message))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        if (source != null) {
            AsyncImage(
                model = source,
                contentDescription = stringResource(if (video) R.string.common_video else R.string.common_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (video) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (video && !showsOverlay(message)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.viewer_play),
                    modifier = Modifier.size(30.dp),
                    tint = Color.White
                )
            }
        }
        if (video && message.duration > 0) {
            Text(
                text = Format.duration(message.duration),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        if (upload != null && showsOverlay(message)) {
            UploadOverlay(
                upload = upload,
                onCancel = { callbacks.onCancel(message.id) },
                onRetry = { callbacks.onRetry(message.id) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBody(
    message: Message,
    content: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    callbacks: MediaCallbacks
) {
    val upload = message.upload
    Row(
        modifier = Modifier
            .width(MediaWidth)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (upload != null && showsOverlay(message)) {
            UploadButton(
                upload = upload,
                onCancel = { callbacks.onCancel(message.id) },
                onRetry = { callbacks.onRetry(message.id) },
                tint = content,
                background = content.copy(alpha = 0.12f),
                size = 44.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = content
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = message.name.ifEmpty { stringResource(R.string.common_file) },
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (message.size > 0) {
                Text(
                    text = Format.fileSize(message.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f)
                )
            }
        }
    }
}
