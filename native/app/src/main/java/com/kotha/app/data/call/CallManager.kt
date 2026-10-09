package com.kotha.app.data.call

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentReference
import com.kotha.app.R
import com.kotha.app.call.CallNotifications
import com.kotha.app.call.CallPermissions
import com.kotha.app.call.CallService
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.call.rtc.MediaUnavailableException
import com.kotha.app.data.call.rtc.PeerListener
import com.kotha.app.data.call.rtc.PeerSession
import com.kotha.app.data.call.rtc.RtcEngine
import com.kotha.app.data.call.rtc.RtcResources
import com.kotha.app.data.net.PushTrigger
import com.kotha.app.data.user.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

data class CallMedia(
    val eglContext: EglBase.Context,
    val local: VideoTrack?,
    val remote: VideoTrack?
)

private class ActiveCall(
    val incoming: Boolean,
    val video: Boolean,
    val peerUid: String,
    val chatId: String,
    val myUid: String
) {
    var id = ""
    var ref: DocumentReference? = null
    var data: CallDoc? = null
    var phase = if (incoming) CallPhase.Incoming else CallPhase.Dialing
    var endText = 0
    var created = false
    var accepted = false
    var connected = false
    var weak = false
    var ended = false
    var canSend = false
    var answerApplied = false
    var muted = false
    var cameraOn = video
    var peerCamOff = false
    var front = true
    var remoteVideo: VideoTrack? = null
    var connectedAtElapsed = 0L
    var session: PeerSession? = null
    var resources: RtcResources? = null
    val pending = ArrayList<RemoteCandidate>()
    val outgoing = ArrayList<RemoteCandidate>()
    val job: CompletableJob = SupervisorJob()
    var ringJob: Job? = null
    var dropJob: Job? = null
    var flushJob: Job? = null
}

@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CallRepository,
    private val iceProvider: IceServerProvider,
    private val engine: RtcEngine,
    private val audio: CallAudioRouter,
    private val tones: CallTones,
    private val proximity: ProximityLock,
    private val logger: CallLogger,
    private val pushTrigger: PushTrigger,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<CallState?>(null)
    private val mutableMedia = MutableStateFlow<CallMedia?>(null)
    private val recent = LinkedHashSet<String>()
    private var current: ActiveCall? = null

    val state: StateFlow<CallState?> = mutableState.asStateFlow()
    val media: StateFlow<CallMedia?> = mutableMedia.asStateFlow()

    init {
        scope.launch { audio.routes.collect { updateProximity() } }
    }

    fun hasActiveCall(): Boolean = current?.ended == false

    @MainThread
    fun startOutgoing(chatId: String, peerUid: String, video: Boolean): Boolean {
        val existing = current
        if (existing != null && !existing.ended) return false
        val uid = authRepository.user?.uid ?: return false
        if (peerUid.isBlank() || peerUid == uid || chatId.isBlank()) return false
        val call = ActiveCall(false, video, peerUid, chatId, uid)
        current = call
        userRepository.watch(peerUid)
        if (CallPermissions.missing(context, video).isNotEmpty()) {
            finish(call, "ended", R.string.call_mic_cam_permission, false)
            return true
        }
        publish(call)
        startService(call)
        audio.start(video)
        tones.startRingback()
        scope.launch(call.job) { runOutgoing(call) }
        return true
    }

    fun onIncoming(doc: CallDoc) {
        scope.launch { handleIncoming(doc) }
    }

    suspend fun restore(callId: String): Boolean {
        val existing = current
        if (existing != null && !existing.ended) return callId.isEmpty() || existing.id == callId
        if (callId.isEmpty()) return false
        val doc = try {
            repository.fetch(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return false
        handleIncoming(doc)
        return current?.id == doc.id
    }

    fun accept() {
        scope.launch { doAccept() }
    }

    fun hangUp() {
        scope.launch {
            val call = current ?: return@launch
            finish(call, "ended", 0, false)
        }
    }

    fun decline(callId: String): Job = scope.launch {
        val call = current
        if (call != null && !call.ended && (callId.isEmpty() || call.id == callId)) {
            finish(call, "declined", 0, false)?.join()
            return@launch
        }
        if (callId.isEmpty()) return@launch
        attempt { repository.end(repository.ref(callId), "declined", "declined") }
    }

    fun reset() {
        scope.launch {
            val call = current ?: return@launch
            finish(call, "ended", 0, false)
        }
    }

    fun toggleMute() {
        scope.launch {
            val call = current ?: return@launch
            val session = call.session ?: return@launch
            call.muted = !call.muted
            session.setMuted(call.muted)
            publish(call)
        }
    }

    fun toggleCamera() {
        scope.launch {
            val call = current ?: return@launch
            val session = call.session ?: return@launch
            if (!call.video || call.ended) return@launch
            call.cameraOn = !call.cameraOn
            session.setCameraEnabled(call.cameraOn)
            val ref = call.ref
            if (call.created && ref != null) {
                val on = call.cameraOn
                scope.launch { attempt { repository.setCamera(ref, call.myUid, on) } }
            }
            publish(call)
        }
    }

    fun flipCamera() {
        scope.launch {
            val call = current ?: return@launch
            val session = call.session ?: return@launch
            if (!call.video || call.ended) return@launch
            session.switchCamera { front ->
                scope.launch {
                    if (front != null) {
                        call.front = front
                        publish(call)
                    }
                }
            }
        }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
    }

    private fun updateProximity() {
        val call = current
        val route = audio.routes.value.selected
        val on = call != null && !call.ended && !call.video && call.phase != CallPhase.Incoming && route == AudioRoute.Earpiece
        proximity.update(on)
    }

    private fun publish(call: ActiveCall) {
        if (current !== call) return
        mutableState.value = CallState(
            callId = call.id,
            chatId = call.chatId,
            peerUid = call.peerUid,
            video = call.video,
            incoming = call.incoming,
            phase = call.phase,
            weak = call.weak,
            connectedAtElapsed = call.connectedAtElapsed,
            muted = call.muted,
            cameraOn = call.cameraOn,
            peerCameraOff = call.peerCamOff,
            frontCamera = call.front,
            hasRemoteVideo = call.remoteVideo != null,
            endText = call.endText
        )
        updateProximity()
    }

    private fun publishMedia(call: ActiveCall) {
        if (current !== call) return
        val resources = call.resources
        mutableMedia.value = if (resources == null || call.ended) {
            null
        } else {
            CallMedia(resources.egl.eglBaseContext, call.session?.localVideo, call.remoteVideo)
        }
    }

    private fun startService(call: ActiveCall) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
        } catch (e: Exception) {
            if (call.incoming) postFallbackNotification()
        }
    }

    private fun postFallbackNotification() {
        val snapshot = mutableState.value ?: return
        val name = userRepository.users.value[snapshot.peerUid]?.name.orEmpty()
        try {
            NotificationManagerCompat.from(context).notify(
                com.kotha.app.notify.NotificationIds.CALL_ONGOING_ID,
                CallNotifications.buildIncoming(context, snapshot, name, null, true)
            )
        } catch (e: SecurityException) {
            return
        }
    }

    private fun listenerFor(call: ActiveCall): PeerListener = object : PeerListener {
        override fun onLocalCandidate(candidate: RemoteCandidate) {
            scope.launch { handleLocalCandidate(call, candidate) }
        }

        override fun onIceState(state: PeerConnection.IceConnectionState) {
            scope.launch { handleIceState(call, state) }
        }

        override fun onRemoteVideo(track: VideoTrack) {
            scope.launch {
                if (call.ended) return@launch
                call.remoteVideo = track
                publishMedia(call)
                publish(call)
            }
        }
    }

    private suspend fun runOutgoing(call: ActiveCall) {
        try {
            val resources = engine.acquire()
            call.resources = resources
            val ice = scope.async(call.job) { iceProvider.load() }
            val session = PeerSession(context, resources, call.video, listenerFor(call))
            call.session = session
            publishMedia(call)
            session.connect(ice.await())
            val offer = session.createOffer()
            if (call.ended) return
            val ref = repository.newRef()
            call.ref = ref
            call.id = ref.id
            val sent = withContext(NonCancellable) {
                val ok = withTimeoutOrNull(CallRules.CREATE_TIMEOUT_MS) {
                    repository.create(ref, call.myUid, call.peerUid, call.chatId, call.video, offer)
                    true
                } ?: false
                if (ok) {
                    call.created = true
                    if (call.ended) attempt { repository.end(ref, "ended", "ended") }
                }
                ok
            }
            if (!sent) {
                finish(call, "ended", R.string.call_start_fail, false)
                return
            }
            if (call.ended) return
            pushTrigger.notifyCall(call.id)
            call.phase = CallPhase.Ringing
            publish(call)
            watchCall(call)
            watchCandidates(call)
            call.canSend = true
            flushOutgoing(call)
            call.ringJob = scope.launch(call.job) {
                delay(CallRules.RING_MS)
                if (!call.connected && !call.ended) finish(call, "missed", R.string.call_no_answer, false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaUnavailableException) {
            finish(call, "ended", R.string.call_mic_cam_missing, false)
        } catch (e: Exception) {
            finish(call, "ended", R.string.call_start_fail, false)
        }
    }

    private fun handleIncoming(doc: CallDoc) {
        val uid = authRepository.user?.uid ?: return
        if (doc.callee != uid || doc.status != "ringing" || doc.offer == null) return
        if (doc.createdAtMs <= 0 || System.currentTimeMillis() - doc.createdAtMs > CallRules.STALE_MS) return
        if (recent.contains(doc.id)) return
        val existing = current
        if (existing != null && !existing.ended) {
            if (existing.id != doc.id) {
                scope.launch { attempt { repository.end(repository.ref(doc.id), "declined", "busy") } }
            }
            return
        }
        val call = ActiveCall(true, doc.video, doc.caller, doc.chatId, uid)
        call.id = doc.id
        call.ref = repository.ref(doc.id)
        call.data = doc
        call.created = true
        current = call
        userRepository.watch(doc.caller)
        publish(call)
        startService(call)
        watchCall(call)
        call.ringJob = scope.launch(call.job) {
            delay(CallRules.RING_MS + CallRules.INCOMING_GRACE_MS)
            if (!call.accepted && !call.ended) finish(call, "missed", R.string.call_missed, true)
        }
        scope.launch(call.job) { iceProvider.load() }
    }

    private fun doAccept() {
        val call = current ?: return
        if (!call.incoming || call.accepted || call.ended) return
        if (CallPermissions.missing(context, call.video).isNotEmpty()) {
            finish(call, "declined", R.string.call_mic_cam_permission, false)
            return
        }
        call.accepted = true
        call.ringJob?.cancel()
        call.phase = CallPhase.Connecting
        publish(call)
        audio.start(call.video)
        scope.launch(call.job) { runAccept(call) }
    }

    private suspend fun runAccept(call: ActiveCall) {
        try {
            val offer = call.data?.offer ?: throw IllegalStateException("offer")
            val resources = engine.acquire()
            call.resources = resources
            val ice = scope.async(call.job) { iceProvider.load() }
            val session = PeerSession(context, resources, call.video, listenerFor(call))
            call.session = session
            publishMedia(call)
            session.connect(ice.await())
            session.setRemote(offer)
            watchCandidates(call)
            val answer = session.createAnswer()
            if (call.ended) return
            val ref = call.ref ?: return
            val fresh = repository.fetch(call.id)
            if (fresh == null || fresh.status != "ringing") {
                finish(call, "missed", R.string.call_already_ended, true)
                return
            }
            repository.answer(ref, answer)
            call.canSend = true
            flushOutgoing(call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaUnavailableException) {
            finish(call, "declined", R.string.call_mic_cam_missing, false)
        } catch (e: Exception) {
            finish(call, "ended", R.string.call_answer_fail, false)
        }
    }

    private fun watchCall(call: ActiveCall) {
        val ref = call.ref ?: return
        scope.launch(call.job) {
            repository.observeCall(ref).collect { doc -> if (doc != null) handleCallDoc(call, doc) }
        }
    }

    private fun watchCandidates(call: ActiveCall) {
        scope.launch(call.job) {
            repository.observeCandidates(call.id, call.myUid).collect { list ->
                list.forEach { applyRemoteCandidate(call, it) }
            }
        }
    }

    private fun handleCallDoc(call: ActiveCall, doc: CallDoc) {
        if (call.ended) return
        call.data = doc
        val answer = doc.answer
        val session = call.session
        if (!call.incoming && answer != null && session != null && !call.answerApplied) {
            call.answerApplied = true
            tones.stop()
            call.phase = CallPhase.Connecting
            publish(call)
            scope.launch(call.job) {
                try {
                    session.setRemote(answer)
                    flushPending(call)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finish(call, "ended", R.string.call_setup_fail, false)
                }
            }
        }
        val off = doc.cam[call.peerUid] == false
        if (off != call.peerCamOff) {
            call.peerCamOff = off
            publish(call)
        }
        when (doc.status) {
            "ended", "declined", "missed" -> {
                val text = when {
                    doc.endReason == "busy" -> R.string.call_busy
                    !call.incoming && doc.status == "missed" -> 0
                    else -> endTextFor(doc.status)
                }
                finish(call, doc.status, text, true)
            }
            "active" -> if (call.incoming && !call.accepted) {
                finish(call, "missed", R.string.call_answered_elsewhere, true)
            }
        }
    }

    @StringRes
    private fun endTextFor(status: String): Int = when (status) {
        "ended" -> R.string.call_ended
        "declined" -> R.string.call_declined
        "missed" -> R.string.call_missed
        else -> 0
    }

    private fun applyRemoteCandidate(call: ActiveCall, candidate: RemoteCandidate) {
        if (call.ended) return
        val session = call.session
        if (session == null || !session.remoteReady) {
            call.pending.add(candidate)
            return
        }
        try {
            session.addRemoteCandidate(candidate)
        } catch (e: Exception) {
            return
        }
    }

    private fun flushPending(call: ActiveCall) {
        val list = ArrayList(call.pending)
        call.pending.clear()
        list.forEach { applyRemoteCandidate(call, it) }
    }

    private fun handleLocalCandidate(call: ActiveCall, candidate: RemoteCandidate) {
        if (call.ended) return
        call.outgoing.add(candidate)
        if (!call.canSend) return
        call.flushJob?.cancel()
        call.flushJob = scope.launch(call.job) {
            delay(CallRules.CANDIDATE_DELAY_MS)
            flushOutgoing(call)
        }
    }

    private fun flushOutgoing(call: ActiveCall) {
        if (!call.canSend || call.ended || call.outgoing.isEmpty() || call.id.isEmpty()) return
        val batch = ArrayList(call.outgoing)
        call.outgoing.clear()
        scope.launch(call.job) { attempt { repository.sendCandidates(call.id, call.myUid, batch) } }
    }

    private fun handleIceState(call: ActiveCall, state: PeerConnection.IceConnectionState) {
        if (call.ended) return
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> onConnected(call)
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                call.weak = true
                publish(call)
                call.dropJob?.cancel()
                call.dropJob = scope.launch(call.job) {
                    delay(CallRules.DROP_MS)
                    if (!call.ended) finish(call, "ended", R.string.call_dropped, false)
                }
            }
            PeerConnection.IceConnectionState.FAILED -> finish(call, "ended", R.string.call_connect_fail, false)
            else -> Unit
        }
    }

    private fun onConnected(call: ActiveCall) {
        call.weak = false
        call.dropJob?.cancel()
        if (call.connected) {
            publish(call)
            return
        }
        call.connected = true
        call.connectedAtElapsed = SystemClock.elapsedRealtime()
        call.ringJob?.cancel()
        tones.stop()
        call.phase = CallPhase.Connected
        publish(call)
    }

    private fun finish(call: ActiveCall, reason: String, @StringRes text: Int, remote: Boolean): Job? {
        if (call.ended) return null
        call.ended = true
        call.job.cancel()
        val talked = if (call.connected) (SystemClock.elapsedRealtime() - call.connectedAtElapsed) / 1000L else 0L
        tones.stop()
        audio.stop()
        proximity.update(false)
        val session = call.session
        val resources = call.resources
        call.session = null
        call.resources = null
        call.remoteVideo = null
        session?.close()
        if (current === call) mutableMedia.value = null
        if (session != null || resources != null) {
            scope.launch {
                delay(CallRules.DISPOSE_DELAY_MS)
                session?.dispose()
                if (resources != null) engine.release()
            }
        }
        val ref = call.ref
        var writes: Job? = null
        if (call.created && ref != null) {
            writes = scope.launch {
                if (!remote) {
                    val status = if (reason == "declined" || reason == "missed") reason else "ended"
                    attempt { repository.end(ref, status, reason) }
                }
                if (call.connected) attempt { repository.writeSecs(ref, talked) }
            }
        }
        if (!call.incoming && call.created) {
            logger.log(call.chatId, listOf(call.myUid, call.peerUid), call.id, call.video, call.connected, reason, talked)
        }
        val missedForMe = call.incoming && !call.accepted &&
            (reason == "missed" || (remote && reason == "ended")) &&
            text != R.string.call_answered_elsewhere && text != R.string.call_already_ended
        if (missedForMe) {
            CallNotifications.showMissed(context, call.chatId, call.peerUid, call.video)
        }
        if (call.id.isNotEmpty()) {
            recent.add(call.id)
            while (recent.size > RECENT_MAX) recent.remove(recent.first())
        }
        call.phase = CallPhase.Ended
        call.endText = text
        publish(call)
        CallNotifications.cancelAll(context)
        scope.launch {
            delay(CallRules.ENDED_HOLD_MS)
            if (current === call) {
                current = null
                mutableState.value = null
            }
        }
        return writes
    }

    private companion object {
        const val RECENT_MAX = 20
    }
}
