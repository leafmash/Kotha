package com.kotha.app.data.block

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kotha.app.data.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Singleton
class BlockRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {

    suspend fun block(peerUid: String): Boolean {
        val uid = authRepository.user?.uid ?: return false
        if (peerUid.isEmpty() || peerUid == uid) return false
        return attempt {
            blockedRef(uid, peerUid).set(mapOf("at" to FieldValue.serverTimestamp())).await()
        }
    }

    suspend fun unblock(peerUid: String): Boolean {
        val uid = authRepository.user?.uid ?: return false
        return attempt { blockedRef(uid, peerUid).delete().await() }
    }

    private fun blockedRef(uid: String, peerUid: String) =
        firestore.collection("users").document(uid).collection("blocked").document(peerUid)

    private suspend fun attempt(action: suspend () -> Unit): Boolean = try {
        action()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
