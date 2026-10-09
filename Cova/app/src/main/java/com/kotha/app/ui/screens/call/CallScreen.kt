package com.kotha.app.ui.screens.call

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectDragGestures
import com.kotha.app.R
import com.kotha.app.data.call.AudioRoute
import com.kotha.app.data.call.CallPhase
import com.kotha.app.data.call.CallState
import com.kotha.app.ui.components.Avatar
import com.kotha.app.util.Format
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val HIDE_DELAY_MS = 4_000L

@Composable
fun CallScreen(onAnswer: () -> Unit, viewModel: CallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val peer by viewModel.peer.collectAsStateWithLifecycle()
    val call = state
    var controlsVisible by remember { mutableStateOf(true) }
    var tick by remember { mutableLongStateOf(0L) }
    var touch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(call?.phase) {
        while (call?.phase == CallPhase.Connected) {
            tick = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val videoActive = call != null && call.video && call.phase != CallPhase.Incoming
    LaunchedEffect(touch, videoActive, call?.phase) {
        controlsVisible = true
        if (videoActive && call?.phase == CallPhase.Connected) {
            delay(HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                if (videoActive && controlsVisible) controlsVisible = false else touch += 1
            }
    ) {
        if (call == null) return@Box
        val egl = media?.eglContext
        val remote = media?.remote
        if (call.video && egl != null && remote != null && !call.peerCameraOff && call.phase == CallPhase.Connected) {
            VideoSurface(eglContext = egl, track = remote, modifier = Modifier.fillMaxSize())
        } else {
            Backdrop(call, peer?.name.orEmpty(), peer?.photo.orEmpty())
        }

        AnimatedVisibility(
            visible = controlsVisible || !videoActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Header(call, peer?.name.orEmpty(), tick, videoActive && remote != null)
        }

        if (call.video && egl != null && call.phase != CallPhase.Incoming && call.phase != CallPhase.Ended) {
            LocalPreview(
                eglContext = egl,
                track = media?.local,
                visible = call.cameraOn,
                mirror = call.frontCamera,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || !videoActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (call.phase == CallPhase.Incoming) {
                IncomingControls(onAnswer = onAnswer, onDecline = viewModel::decline)
            } else if (call.phase != CallPhase.Ended) {
                ActiveControls(
                    call = call,
                    route = routes.selected,
                    available = routes.available,
                    onMute = viewModel::toggleMute,
                    onCamera = viewModel::toggleCamera,
                    onFlip = viewModel::flipCamera,
                    onRoute = viewModel::selectRoute,
                    onSpeaker = viewModel::toggleSpeaker,
                    onEnd = viewModel::hangUp
                )
            }
        }
    }
}

@Composable
private fun Backdrop(call: CallState, name: String, photo: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(name = name, photo = photo, size = 144.dp)
            if (call.peerCameraOff && call.video) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.call_camera_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun Header(call: CallState, name: String, tick: Long, overVideo: Boolean) {
    val status = statusText(call, tick)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (overVideo) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name.ifEmpty { stringResource(R.string.common_user) },
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun statusText(call: CallState, tick: Long): String = when (call.phase) {
    CallPhase.Dialing -> stringResource(R.string.call_calling)
    CallPhase.Ringing -> stringResource(R.string.call_ringing)
    CallPhase.Incoming -> stringResource(if (call.video) R.string.call_incoming_video else R.string.call_incoming_voice)
    CallPhase.Connecting -> stringResource(R.string.call_connecting)
    CallPhase.Connected -> if (call.weak) {
        stringResource(R.string.call_weak)
    } else {
        val secs = ((tick - call.connectedAtElapsed) / 1000L).coerceAtLeast(0L)
        Format.duration(secs.toDouble())
    }
    CallPhase.Ended -> if (call.endText != 0) stringResource(call.endText) else stringResource(R.string.call_ended)
}

@Composable
private fun LocalPreview(
    eglContext: org.webrtc.EglBase.Context,
    track: org.webrtc.VideoTrack?,
    visible: Boolean,
    mirror: Boolean,
    modifier: Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 72.dp, end = 16.dp)
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(width = 108.dp, height = 156.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            }
    ) {
        if (visible) {
            VideoSurface(
                eglContext = eglContext,
                track = track,
                modifier = Modifier.fillMaxSize(),
                mirror = mirror,
                overlay = true
            )
        } else {
            Icon(
                imageVector = Icons.Filled.VideocamOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Int = 60
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size((size / 2.4f).dp))
        }
    }
}

@Composable
private fun IncomingControls(onAnswer: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 48.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabeledButton(
            icon = Icons.Filled.CallEnd,
            label = stringResource(R.string.call_decline),
            container = Color(0xFFE53935),
            onClick = onDecline
        )
        LabeledButton(
            icon = Icons.Filled.Call,
            label = stringResource(R.string.call_answer),
            container = Color(0xFF2E9E5B),
            onClick = onAnswer
        )
    }
}

@Composable
private fun LabeledButton(icon: ImageVector, label: String, container: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(icon, label, onClick, container, Color.White, size = 72)
        Spacer(Modifier.height(8.dp))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ActiveControls(
    call: CallState,
    route: AudioRoute,
    available: List<AudioRoute>,
    onMute: () -> Unit,
    onCamera: () -> Unit,
    onFlip: () -> Unit,
    onRoute: (AudioRoute) -> Unit,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit
) {
    val idle = Color.White.copy(alpha = 0.16f)
    val active = Color.White
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                RoundButton(
                    icon = routeIcon(route),
                    label = stringResource(R.string.call_speaker),
                    onClick = { if (available.size > 2) menu = true else onSpeaker() },
                    container = if (route == AudioRoute.Earpiece) idle else active,
                    content = if (route == AudioRoute.Earpiece) Color.White else Color.Black
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    available.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(routeLabel(item)) },
                            leadingIcon = { Icon(routeIcon(item), contentDescription = null) },
                            onClick = {
                                menu = false
                                onRoute(item)
                            }
                        )
                    }
                }
            }
            if (call.video) {
                RoundButton(
                    icon = if (call.cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    label = stringResource(R.string.call_camera),
                    onClick = onCamera,
                    container = if (call.cameraOn) idle else active,
                    content = if (call.cameraOn) Color.White else Color.Black
                )
                RoundButton(
                    icon = Icons.Filled.FlipCameraAndroid,
                    label = stringResource(R.string.call_flip),
                    onClick = onFlip,
                    container = idle,
                    content = Color.White
                )
            }
            RoundButton(
                icon = if (call.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = stringResource(R.string.call_mic),
                onClick = onMute,
                container = if (call.muted) active else idle,
                content = if (call.muted) Color.Black else Color.White
            )
        }
        Spacer(Modifier.height(28.dp))
        RoundButton(
            icon = Icons.Filled.CallEnd,
            label = stringResource(R.string.call_end),
            onClick = onEnd,
            container = Color(0xFFE53935),
            content = Color.White,
            size = 72
        )
    }
}

private fun routeIcon(route: AudioRoute): ImageVector = when (route) {
    AudioRoute.Speaker -> Icons.Filled.VolumeUp
    AudioRoute.Bluetooth -> Icons.Filled.Bluetooth
    AudioRoute.Wired -> Icons.Filled.Headset
    AudioRoute.Earpiece -> Icons.Filled.PhoneInTalk
}

@Composable
private fun routeLabel(route: AudioRoute): String = stringResource(
    when (route) {
        AudioRoute.Speaker -> R.string.call_route_speaker
        AudioRoute.Bluetooth -> R.string.call_route_bluetooth
        AudioRoute.Wired -> R.string.call_route_headphones
        AudioRoute.Earpiece -> R.string.call_route_earpiece
    }
)
