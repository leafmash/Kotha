package com.kotha.app.ui.screens.chat.media

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.media.MediaFiles
import com.kotha.app.data.media.VoicePlayer
import com.kotha.app.ui.components.FullScreenDialog
import com.kotha.app.util.Format
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch

data class ViewerItem(
    val id: String,
    val type: String,
    val url: String,
    val localPath: String,
    val name: String,
    val sender: String,
    val atMs: Long
) {
    val playable: String
        get() = url.ifEmpty { if (localPath.isEmpty()) "" else Uri.fromFile(File(localPath)).toString() }

    val shareable: Boolean get() = url.isNotEmpty()
}

@Composable
fun MediaViewer(
    items: List<ViewerItem>,
    startId: String,
    voice: VoicePlayer,
    onDismiss: () -> Unit
) {
    val start = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { items.size }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ViewerItem?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    val current = items[pager.currentPage.coerceIn(0, items.size - 1)]

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { target ->
        val item = pendingSave
        pendingSave = null
        if (target != null && item != null) {
            scope.launch {
                busy = true
                val saved = MediaFiles.copyToUri(context, item.url, target)
                busy = false
                val message = if (saved) R.string.viewer_saved else R.string.viewer_save_fail
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    FullScreenDialog(
        onDismiss = onDismiss,
        backgroundAlpha = 1f - (abs(dragY) / (threshold * 3f)).coerceIn(0f, 0.7f)
    ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            key = { items[it].id },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dragY }
        ) { page ->
            val item = items[page]
            if (item.type == "video") {
                VideoPage(
                    source = item.playable,
                    active = pager.currentPage == page,
                    chromeVisible = chrome,
                    onToggleChrome = { chrome = !chrome },
                    voice = voice
                )
            } else {
                ZoomableImage(
                    model = if (item.url.isNotEmpty()) item.url else File(item.localPath),
                    onZoomedChange = { if (pager.currentPage == page) zoomed = it },
                    onDismissDrag = { delta -> dragY += delta },
                    onDismissRelease = {
                        if (abs(dragY) > threshold) {
                            onDismiss()
                        } else {
                            scope.launch { animate(dragY, 0f) { value, _ -> dragY = value } }
                        }
                    },
                    onTap = { chrome = !chrome }
                )
            }
        }
        AnimatedVisibility(
            visible = chrome && dragY == 0f,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, stringResource(R.string.viewer_close), tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.sender,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = Format.number(pager.currentPage + 1) + " / " + Format.number(items.size) +
                            if (current.atMs > 0) "  ·  " + Format.shortDate(current.atMs, "d MMM") + " " + Format.clock(current.atMs) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
                if (current.shareable) {
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            val item = current
                            scope.launch {
                                busy = true
                                val name = MediaFiles.displayName(item.name, item.url, item.id, item.type)
                                val file = MediaFiles.fetch(context, item.url, name)
                                busy = false
                                val shared = file != null && MediaFiles.share(context, file, MediaFiles.mimeOf(name, item.type))
                                if (!shared) {
                                    Toast.makeText(context, R.string.viewer_share_fail, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Share, stringResource(R.string.viewer_share), tint = Color.White)
                    }
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            pendingSave = current
                            saveLauncher.launch(MediaFiles.displayName(current.name, current.url, current.id, current.type))
                        }
                    ) {
                        Icon(Icons.Filled.Download, stringResource(R.string.viewer_save), tint = Color.White)
                    }
                }
            }
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}
