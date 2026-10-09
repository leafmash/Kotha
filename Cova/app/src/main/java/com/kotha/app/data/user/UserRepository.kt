package com.kotha.app.data.user

import com.google.firebase.firestore.FirebaseFirestore
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.model.toUserProfile
import com.kotha.app.data.presence.PresenceRepository
import com.kotha.app.data.resilient
import com.kotha.app.data.snapshotFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val presenceRepository: PresenceRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val watchers = ConcurrentHashMap<String, Job>()
    private val mutableUsers = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val users: StateFlow<Map<String, UserProfile>> = mutableUsers.asStateFlow()

    fun watch(uid: String) {
        if (uid.isBlank() || watchers.containsKey(uid)) return
        presenceRepository.watch(uid)
        watchers.computeIfAbsent(uid) {
            scope.launch {
                firestore.collection("users").document(uid).snapshotFlow().resilient().collect { snapshot ->
                    mutableUsers.update { current ->
                        if (snapshot.exists()) current + (uid to snapshot.toUserProfile()) else current - uid
                    }
                }
            }
        }
    }

    fun put(profile: UserProfile) {
        mutableUsers.update { it + (profile.uid to profile) }
    }

    fun reset() {
        watchers.values.forEach { it.cancel() }
        watchers.clear()
        mutableUsers.value = emptyMap()
    }
}
