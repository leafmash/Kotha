package com.kotha.app.ui.screens.chat.media

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kotha.app.R
import com.kotha.app.data.media.PickedMedia
import com.kotha.app.ui.components.FullScreenDialog
import com.kotha.app.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AttachmentPreview(
    picks: List<PickedMedia>,
    frameOf: (Uri) -> Bitmap?,
    onRemove: (Int) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, (picks.size - 1).coerceAtLeast(0))
    FullScreenDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, stringResource(R.string.viewer_close), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Format.number((index + 1).toLong()) + " / " + Format.number(picks.size.toLong()),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.preview_remove), tint = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (picks.isNotEmpty()) PreviewContent(picks[index], frameOf)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(picks, key = { position, pick -> "$position-${pick.uri}" }) { position, pick ->
                        Thumb(pick, position == index) { selected = position }
                    }
                }
                Spacer(Modifier.size(12.dp))
                FloatingActionButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.preview_send))
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(pick: PickedMedia, frameOf: (Uri) -> Bitmap?) {
    when (pick.kind) {
        "image" -> AsyncImage(
            model = pick.uri,
            contentDescription = pick.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        "video" -> {
            val frame by produceState<ImageBitmap?>(null, pick.uri) {
                value = withContext(Dispatchers.IO) { frameOf(pick.uri)?.asImageBitmap() }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val current = frame
                if (current != null) {
                    Image(
                        bitmap = current,
                        contentDescription = pick.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(40.dp), tint = Color.White)
                }
            }
        }
        else -> Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Color.White
            )
            Text(
                text = pick.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            if (pick.size > 0) {
                Text(
                    text = Format.fileSize(pick.size),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun Thumb(pick: PickedMedia, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (pick.kind) {
            "image" -> AsyncImage(
                model = pick.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            "video" -> Icon(Icons.Filled.Videocam, null, tint = Color.White)
            else -> Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.White)
        }
    }
}
