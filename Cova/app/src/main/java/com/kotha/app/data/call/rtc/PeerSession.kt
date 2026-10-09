package com.kotha.app.data.call.rtc

import android.content.Context
import com.kotha.app.data.call.IceServerSpec
import com.kotha.app.data.call.RemoteCandidate
import com.kotha.app.data.call.SdpPayload
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class MediaUnavailableException(message: String) : Exception(message)

interface PeerListener {
    fun onLocalCandidate(candidate: RemoteCandidate)
    fun onIceState(state: PeerConnection.IceConnectionState)
    fun onRemoteVideo(track: VideoTrack)
}

private open class ObserverAdapter : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
    override fun onIceCandidate(candidate: IceCandidate?) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
    override fun onAddStream(stream: MediaStream?) = Unit
    override fun onRemoveStream(stream: MediaStream?) = Unit
    override fun onDataChannel(channel: DataChannel?) = Unit
    override fun onRenegotiationNeeded() = Unit
    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
}

class PeerSession(
    private val context: Context,
    private val resources: RtcResources,
    val video: Boolean,
    private val listener: PeerListener
) {

    private val factory = resources.factory
    private val audioSource: AudioSource = factory.createAudioSource(MediaConstraints())
    private val audioTrack: AudioTrack = factory.createAudioTrack(AUDIO_ID, audioSource)
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var capturing = false
    private var peer: PeerConnection? = null

    @Volatile
    var remoteReady = false
        private set

    var localVideo: VideoTrack? = null
        private set

    var remoteVideo: VideoTrack? = null
        private set

    var frontCamera = true
        private set

    init {
        if (video) startCamera()
    }

    private fun enumerator(): CameraEnumerator =
        if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context) else Camera1Enumerator(false)

    private fun startCamera() {
        val enumerator = enumerator()
        val names = enumerator.deviceNames
        val name = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
            ?: throw MediaUnavailableException("no camera")
        frontCamera = enumerator.isFrontFacing(name)
        val created = enumerator.createCapturer(name, null) ?: throw MediaUnavailableException("camera open")
        val helper = SurfaceTextureHelper.create(CAPTURE_THREAD, resources.egl.eglBaseContext)
        val source = factory.createVideoSource(false)
        created.initialize(helper, context, source.capturerObserver)
        created.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
        capturing = true
        capturer = created
        textureHelper = helper
        videoSource = source
        localVideo = factory.createVideoTrack(VIDEO_ID, source)
    }

    fun connect(servers: List<IceServerSpec>) {
        val ice = servers.map { spec ->
            val builder = PeerConnection.IceServer.builder(spec.urls)
            if (spec.username.isNotEmpty()) builder.setUsername(spec.username)
            if (spec.credential.isNotEmpty()) builder.setPassword(spec.credential)
            builder.createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val connection = factory.createPeerConnection(config, observer)
            ?: throw MediaUnavailableException("peer connection")
        peer = connection
        connection.addTrack(audioTrack, listOf(STREAM_ID))
        localVideo?.let { connection.addTrack(it, listOf(STREAM_ID)) }
    }

    private val observer = object : ObserverAdapter() {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null || candidate.sdp.isBlank()) return
            listener.onLocalCandidate(RemoteCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex))
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state != null) listener.onIceState(state)
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            handleRemote(receiver?.track())
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            handleRemote(transceiver?.receiver?.track())
        }
    }

    private fun handleRemote(track: org.webrtc.MediaStreamTrack?) {
        if (track !is VideoTrack) return
        if (remoteVideo === track) return
        remoteVideo = track
        listener.onRemoteVideo(track)
    }

    private fun requirePeer(): PeerConnection = peer ?: throw IllegalStateException("not connected")

    suspend fun createOffer(): SdpPayload {
        val connection = requirePeer()
        val offer = createSdp { connection.createOffer(it, MediaConstraints()) }
        setSdp { connection.setLocalDescription(it, offer) }
        return SdpPayload(offer.type.canonicalForm(), offer.description)
    }

    suspend fun setRemote(payload: SdpPayload) {
        val connection = requirePeer()
        val description = SessionDescription(SessionDescription.Type.fromCanonicalForm(payload.type), payload.sdp)
        setSdp { connection.setRemoteDescription(it, description) }
        remoteReady = true
    }

    suspend fun createAnswer(): SdpPayload {
        val connection = requirePeer()
        val answer = createSdp { connection.createAnswer(it, MediaConstraints()) }
        setSdp { connection.setLocalDescription(it, answer) }
        return SdpPayload(answer.type.canonicalForm(), answer.description)
    }

    fun addRemoteCandidate(candidate: RemoteCandidate) {
        peer?.addIceCandidate(IceCandidate(candidate.sdpMid ?: "", candidate.sdpMLineIndex, candidate.sdp))
    }

    fun setMuted(muted: Boolean) {
        audioTrack.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        val track = localVideo ?: return
        val active = capturer ?: return
        track.setEnabled(enabled)
        try {
            if (enabled && !capturing) {
                active.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
                capturing = true
            } else if (!enabled && capturing) {
                active.stopCapture()
                capturing = false
            }
        } catch (e: Exception) {
            return
        }
    }

    fun switchCamera(onResult: (Boolean?) -> Unit) {
        val active = capturer
        if (active == null) {
            onResult(null)
            return
        }
        active.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                frontCamera = isFrontCamera
                onResult(isFrontCamera)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                onResult(null)
            }
        })
    }

    fun close() {
        remoteReady = false
        runCatching { peer?.close() }
        runCatching {
            if (capturing) capturer?.stopCapture()
        }
        capturing = false
    }

    fun dispose() {
        runCatching { peer?.dispose() }
        peer = null
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { localVideo?.dispose() }
        localVideo = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { textureHelper?.dispose() }
        textureHelper = null
        runCatching { audioTrack.dispose() }
        runCatching { audioSource.dispose() }
        remoteVideo = null
    }

    private suspend fun createSdp(start: (SdpObserver) -> Unit): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            start(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description != null) {
                        continuation.resume(description)
                    } else {
                        continuation.resumeWithException(IllegalStateException("empty sdp"))
                    }
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException(error ?: "create failed"))
                }

                override fun onSetFailure(error: String?) = Unit
            })
        }

    private suspend fun setSdp(start: (SdpObserver) -> Unit) {
        suspendCancellableCoroutine<Unit> { continuation ->
            start(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) = Unit

                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String?) = Unit

                override fun onSetFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException(error ?: "set failed"))
                }
            })
        }
    }

    private companion object {
        const val AUDIO_ID = "cova_audio"
        const val VIDEO_ID = "cova_video"
        const val STREAM_ID = "cova_stream"
        const val CAPTURE_THREAD = "CovaCapture"
        const val CAPTURE_WIDTH = 640
        const val CAPTURE_HEIGHT = 480
        const val CAPTURE_FPS = 30
    }
}
