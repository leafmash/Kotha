package com.kotha.app.data.call

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.kotha.app.data.resilient
import com.kotha.app.data.snapshotFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private fun sdpOf(raw: Any?): SdpPayload? {
    val map = raw as? Map<*, *> ?: return null
    val type = map["type"] as? String ?: return null
    val sdp = map["sdp"] as? String ?: return null
    return SdpPayload(type, sdp)
}

private fun boolMapOf(raw: Any?): Map<String, Boolean> {
    val map = raw as? Map<*, *> ?: return emptyMap()
    val out = HashMap<String, Boolean>()
    for (entry in map.entries) {
        val key = entry.key as? String ?: continue
        val value = entry.value as? Boolean ?: continue
        out[key] = value
    }
    return out
}

fun DocumentSnapshot.toCallDoc(): CallDoc = CallDoc(
    id = id,
    caller = getString("caller").orEmpty(),
    callee = getString("callee").orEmpty(),
    chatId = getString("chatId").orEmpty(),
    video = getBoolean("video") == true,
    status = getString("status").orEmpty(),
    offer = sdpOf(get("offer")),
    answer = sdpOf(get("answer")),
    createdAtMs = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
    endReason = getString("endReason").orEmpty(),
    secs = getLong("secs") ?: 0L,
    cam = boolMapOf(get("cam"))
)

private fun candidatesOf(doc: DocumentSnapshot): List<RemoteCandidate> {
    val list = doc.get("cs") as? List<*> ?: return emptyList()
    val out = ArrayList<RemoteCandidate>(list.size)
    for (raw in list) {
        val map = raw as? Map<*, *> ?: continue
        val sdp = map["candidate"] as? String ?: continue
        if (sdp.isBlank()) continue
        out.add(RemoteCandidate(sdp, map["sdpMid"] as? String, (map["sdpMLineIndex"] as? Number)?.toInt() ?: 0))
    }
    return out
}

@Singleton
class CallRepository @Inject constructor(private val firestore: FirebaseFirestore) {

    private val calls get() = firestore.collection("calls")

    fun newRef(): DocumentReference = calls.document()

    fun ref(callId: String): DocumentReference = calls.document(callId)

    suspend fun create(
        ref: DocumentReference,
        caller: String,
        callee: String,
        chatId: String,
        video: Boolean,
        offer: SdpPayload
    ) {
        ref.set(
            mapOf(
                "caller" to caller,
                "callee" to callee,
                "chatId" to chatId,
                "video" to video,
                "status" to "ringing",
                "offer" to mapOf("type" to offer.type, "sdp" to offer.sdp),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun answer(ref: DocumentReference, answer: SdpPayload) {
        ref.update(
            mapOf(
                "answer" to mapOf("type" to answer.type, "sdp" to answer.sdp),
                "status" to "active"
            )
        ).await()
    }

    suspend fun end(ref: DocumentReference, status: String, reason: String) {
        ref.update(
            mapOf(
                "status" to status,
                "endedAt" to FieldValue.serverTimestamp(),
                "endReason" to reason
            )
        ).await()
    }

    suspend fun writeSecs(ref: DocumentReference, secs: Long) {
        ref.update("secs", secs.coerceIn(0L, CallRules.MAX_SECS)).await()
    }

    suspend fun setCamera(ref: DocumentReference, uid: String, on: Boolean) {
        ref.update(FieldPath.of("cam", uid), on).await()
    }

    suspend fun fetch(callId: String): CallDoc? {
        val snapshot = ref(callId).get(Source.SERVER).await()
        return if (snapshot.exists()) snapshot.toCallDoc() else null
    }

    suspend fun fetchRinging(uid: String): List<CallDoc> =
        calls.whereEqualTo("callee", uid)
            .whereEqualTo("status", "ringing")
            .get(Source.SERVER)
            .await()
            .documents
            .map { it.toCallDoc() }

    suspend fun sendCandidates(callId: String, from: String, list: List<RemoteCandidate>) {
        val cs = list.map {
            mapOf("candidate" to it.sdp, "sdpMid" to it.sdpMid, "sdpMLineIndex" to it.sdpMLineIndex)
        }
        ref(callId).collection("candidates").add(mapOf("from" to from, "cs" to cs)).await()
    }

    fun observeCall(ref: DocumentReference): Flow<CallDoc?> =
        ref.snapshotFlow().map { if (it.exists()) it.toCallDoc() else null }.resilient()

    fun observeCandidates(callId: String, myUid: String): Flow<List<RemoteCandidate>> =
        ref(callId).collection("candidates").snapshotFlow()
            .map { snapshot ->
                snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .map { it.document }
                    .filter { it.getString("from") != myUid }
                    .flatMap { candidatesOf(it) }
            }
            .filter { it.isNotEmpty() }
            .resilient()

    fun observeIncoming(uid: String): Flow<List<CallDoc>> =
        calls.whereEqualTo("callee", uid)
            .whereEqualTo("status", "ringing")
            .snapshotFlow()
            .filter { !it.metadata.isFromCache }
            .map { snapshot ->
                snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .map { it.document.toCallDoc() }
            }
            .filter { it.isNotEmpty() }
            .resilient()

    fun observeHistory(uid: String): Flow<List<CallDoc>> {
        val outgoing = calls.whereEqualTo("caller", uid).snapshotFlow().map { s -> s.documents.map { it.toCallDoc() } }
        val incoming = calls.whereEqualTo("callee", uid).snapshotFlow().map { s -> s.documents.map { it.toCallDoc() } }
        return combine(outgoing, incoming) { a, b -> (a + b).distinctBy { it.id } }.resilient()
    }
}
