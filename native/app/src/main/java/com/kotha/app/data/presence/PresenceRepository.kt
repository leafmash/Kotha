package com.kotha.app.data.presence

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kotha.app.core.AppConfig
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.app.AppForeground
import com.kotha.app.data.model.UserProfile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class RtPresence(val online: Boolean, val at: Long)

data class PresenceInfo(val online: Boolean, val ms: Long)

@Singleton
class PresenceRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
    private val appForeground: AppForeground,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val mutablePeers = MutableStateFlow<Map<String, RtPresence>>(emptyMap())
    val peers: StateFlow<Map<String, RtPresence>> = mutablePeers.asStateFlow()

    private val listeners = ConcurrentHashMap<String, ValueEventListener>()

    @Volatile
    private var offset = 0L

    @Volatile
    private var rtConnected = false

    @Volatile
    private var ownUid: String? = null

    init {
        database.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                offset = (snapshot.value as? Number)?.toLong() ?: 0L
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })
        database.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                rtConnected = snapshot.value == true
                if (rtConnected) armDisconnect()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })
        scope.launch {
            appForeground.foreground.collect { foreground -> writeOwn(foreground, true) }
        }
        scope.launch {
            while (true) {
                delay(AppConfig.PRESENCE_BEAT_MS)
                if (appForeground.foreground.value && !rtConnected) writeOwn(true, true)
            }
        }
    }

    fun serverNow(): Long = System.currentTimeMillis() + offset

    fun watch(uid: String) {
        if (uid.isBlank() || listeners.containsKey(uid)) return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val at = (snapshot.child("at").value as? Number)?.toLong()
                if (at == null) {
                    mutablePeers.update { it - uid }
                } else {
                    val online = snapshot.child("online").value == true
                    mutablePeers.update { it + (uid to RtPresence(online, at)) }
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        if (listeners.putIfAbsent(uid, listener) == null) {
            database.getReference("presence/$uid").addValueEventListener(listener)
        }
    }

    fun bind(uid: String) {
        ownUid = uid
        armDisconnect()
        writeOwn(appForeground.foreground.value, true)
    }

    fun unbind() {
        val uid = ownUid ?: return
        ownUid = null
        database.getReference("presence/$uid").onDisconnect().cancel()
    }

    suspend fun leave() {
        val uid = ownUid ?: return
        withTimeoutOrNull(3_000) {
            runCatching {
                firestore.collection("users").document(uid)
                    .update(mapOf("online" to false, "lastSeen" to FieldValue.serverTimestamp())).await()
            }
            runCatching {
                database.getReference("presence/$uid")
                    .setValue(mapOf("online" to false, "at" to ServerValue.TIMESTAMP)).await()
            }
        }
        unbind()
    }

    fun presenceOf(user: UserProfile?, rt: RtPresence?): PresenceInfo {
        val now = serverNow()
        val fsMs = user?.lastSeenMs ?: 0L
        if (rt != null && rt.at >= fsMs - 15_000) {
            return PresenceInfo(rt.online || now - rt.at < AppConfig.RT_GRACE_MS, rt.at)
        }
        val online = user != null && user.online && fsMs > 0 && now - fsMs < AppConfig.PRESENCE_STALE_MS
        return PresenceInfo(online, fsMs)
    }

    private fun armDisconnect() {
        val uid = ownUid ?: return
        if (!rtConnected) return
        val reference = database.getReference("presence/$uid")
        reference.onDisconnect()
            .setValue(mapOf("online" to false, "at" to ServerValue.TIMESTAMP))
            .addOnSuccessListener {
                if (ownUid == uid) {
                    reference.setValue(mapOf("online" to appForeground.foreground.value, "at" to ServerValue.TIMESTAMP))
                }
            }
    }

    private fun writeOwn(online: Boolean, withFirestore: Boolean) {
        val uid = ownUid ?: return
        if (rtConnected) {
            database.getReference("presence/$uid")
                .setValue(mapOf("online" to online, "at" to ServerValue.TIMESTAMP))
        }
        if (withFirestore || !rtConnected) {
            firestore.collection("users").document(uid)
                .update(mapOf("online" to online, "lastSeen" to FieldValue.serverTimestamp()))
        }
    }
}
