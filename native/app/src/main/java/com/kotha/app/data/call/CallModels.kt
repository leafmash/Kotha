package com.kotha.app.data.call

import androidx.annotation.StringRes

enum class CallPhase { Dialing, Ringing, Incoming, Connecting, Connected, Ended }

data class CallState(
    val callId: String,
    val chatId: String,
    val peerUid: String,
    val video: Boolean,
    val incoming: Boolean,
    val phase: CallPhase,
    val weak: Boolean = false,
    val connectedAtElapsed: Long = 0L,
    val muted: Boolean = false,
    val cameraOn: Boolean = false,
    val peerCameraOff: Boolean = false,
    val frontCamera: Boolean = true,
    val hasRemoteVideo: Boolean = false,
    @StringRes val endText: Int = 0
)

data class SdpPayload(val type: String, val sdp: String)

data class CallDoc(
    val id: String,
    val caller: String,
    val callee: String,
    val chatId: String,
    val video: Boolean,
    val status: String,
    val offer: SdpPayload?,
    val answer: SdpPayload?,
    val createdAtMs: Long,
    val endReason: String,
    val secs: Long,
    val cam: Map<String, Boolean>
)

data class RemoteCandidate(val sdp: String, val sdpMid: String?, val sdpMLineIndex: Int)

data class IceServerSpec(val urls: List<String>, val username: String, val credential: String)

enum class CallKind { Done, Declined, Cancelled, Missed, Ringing }

data class CallRecord(
    val id: String,
    val outgoing: Boolean,
    val peerUid: String,
    val chatId: String,
    val video: Boolean,
    val kind: CallKind,
    val secs: Long,
    val atMs: Long
)

object CallRules {
    const val RING_MS = 45_000L
    const val INCOMING_GRACE_MS = 5_000L
    const val STALE_MS = 90_000L
    const val DROP_MS = 12_000L
    const val CANDIDATE_DELAY_MS = 250L
    const val CREATE_TIMEOUT_MS = 15_000L
    const val ENDED_HOLD_MS = 1_400L
    const val DISPOSE_DELAY_MS = 600L
    const val MAX_SECS = 86_399L
}

fun CallDoc.toRecord(uid: String, now: Long): CallRecord {
    val outgoing = caller == uid
    val at = if (createdAtMs > 0) createdAtMs else now
    val kind = when {
        answer != null -> CallKind.Done
        status == "ringing" -> if (now - at < CallRules.STALE_MS) CallKind.Ringing else CallKind.Missed
        status == "declined" -> if (outgoing) CallKind.Declined else if (endReason == "busy") CallKind.Missed else CallKind.Declined
        status == "missed" -> CallKind.Missed
        else -> if (outgoing) CallKind.Cancelled else CallKind.Missed
    }
    return CallRecord(
        id = id,
        outgoing = outgoing,
        peerUid = if (outgoing) callee else caller,
        chatId = chatId,
        video = video,
        kind = kind,
        secs = secs,
        atMs = at
    )
}
