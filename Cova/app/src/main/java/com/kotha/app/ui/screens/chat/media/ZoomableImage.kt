package com.kotha.app.ui.screens.chat.media

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class GestureMode { Undecided, Transform, Dismiss, Ignore }

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

private fun clampOffset(offset: Offset, scale: Float, box: IntSize): Offset {
    val maxX = max(0f, box.width * (scale - 1f) / 2f)
    val maxY = max(0f, box.height * (scale - 1f) / 2f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

@Composable
fun ZoomableImage(
    model: Any,
    onZoomedChange: (Boolean) -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissRelease: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val zoomedCallback by rememberUpdatedState(onZoomedChange)
    val dragCallback by rememberUpdatedState(onDismissDrag)
    val releaseCallback by rememberUpdatedState(onDismissRelease)
    val tapCallback by rememberUpdatedState(onTap)
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        snapshotFlow { scale > 1.01f }.distinctUntilChanged().collect { zoomedCallback(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapCallback() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            val startScale = scale
                            val startOffset = offset
                            val targetScale = if (startScale > 1.05f) 1f else DOUBLE_TAP_SCALE
                            val center = Offset(box.width / 2f, box.height / 2f)
                            val targetOffset = if (targetScale == 1f) {
                                Offset.Zero
                            } else {
                                clampOffset((tap - center) * (1f - targetScale), targetScale, box)
                            }
                            animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                                scale = startScale + (targetScale - startScale) * fraction
                                offset = lerp(startOffset, targetOffset, fraction)
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode = GestureMode.Undecided
                    var travelled = Offset.Zero
                    var consumedByOther: Boolean
                    do {
                        val event = awaitPointerEvent()
                        consumedByOther = event.changes.any { it.isConsumed }
                        if (!consumedByOther) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (mode == GestureMode.Undecided) {
                                if (event.changes.count { it.pressed } > 1) {
                                    mode = GestureMode.Transform
                                } else {
                                    travelled += pan
                                    if (travelled.getDistance() > slop) {
                                        mode = when {
                                            scale > 1.01f -> GestureMode.Transform
                                            abs(travelled.y) > abs(travelled.x) -> GestureMode.Dismiss
                                            else -> GestureMode.Ignore
                                        }
                                    }
                                }
                            }
                            when (mode) {
                                GestureMode.Transform -> {
                                    val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                    val applied = newScale / scale
                                    val focus = event.calculateCentroid() - Offset(box.width / 2f, box.height / 2f)
                                    val moved = (offset - focus) * applied + focus + pan
                                    scale = newScale
                                    offset = if (newScale <= 1f) Offset.Zero else clampOffset(moved, newScale, box)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                                GestureMode.Dismiss -> {
                                    dragCallback(pan.y)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                                else -> Unit
                            }
                        }
                    } while (!consumedByOther && event.changes.any { it.pressed })
                    if (mode == GestureMode.Dismiss) releaseCallback()
                    if (scale < 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).size(Size.ORIGINAL).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
