package com.kotha.app.ui.screens.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoSurface(
    eglContext: EglBase.Context,
    track: VideoTrack?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    overlay: Boolean = false,
    fit: Boolean = false
) {
    val renderer = remember(eglContext) { arrayOfNulls<SurfaceViewRenderer>(1) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).also {
                it.init(eglContext, null)
                it.setEnableHardwareScaler(true)
                it.setZOrderMediaOverlay(overlay)
                renderer[0] = it
            }
        },
        update = { view ->
            view.setMirror(mirror)
            view.setScalingType(
                if (fit) RendererCommon.ScalingType.SCALE_ASPECT_FIT else RendererCommon.ScalingType.SCALE_ASPECT_FILL
            )
        }
    )
    DisposableEffect(track, eglContext) {
        val view = renderer[0]
        if (track != null && view != null) track.addSink(view)
        onDispose {
            val current = renderer[0]
            if (track != null && current != null) {
                try {
                    track.removeSink(current)
                } catch (e: Exception) {
                    return@onDispose
                }
            }
        }
    }
    DisposableEffect(eglContext) {
        onDispose {
            renderer[0]?.release()
            renderer[0] = null
        }
    }
}
