package com.kotha.app.data.chat

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.model.toUserProfile
import com.kotha.app.data.user.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

sealed interface LookupResult {
    data class Found(val profile: UserProfile) : LookupResult
    data object Self : LookupResult
    data object None : LookupResult
    data object Failed : LookupResult
}

@Singleton
class ContactRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val chatListRepository: ChatListRepository
) {

    suspend fun lookup(email: String): LookupResult {
        val uid = authRepository.user?.uid ?: return LookupResult.Failed
        return try {
            val hit = firestore.collection("emailLookup").document(email).get().await()
            if (!hit.exists()) return LookupResult.None
            val peerUid = hit.getString("uid").orEmpty()
            if (peerUid == uid) return LookupResult.Self
            val profile = firestore.collection("users").document(peerUid).get().await()
            if (!profile.exists()) return LookupResult.None
            val user = profile.toUserProfile()
            userRepository.put(user)
            userRepository.watch(peerUid)
            LookupResult.Found(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LookupResult.Failed
        }
    }

    fun directChatId(uid: String, peerUid: String): String = listOf(uid, peerUid).sorted().joinToString("_")

    fun openDirect(peerUid: String): String? {
        val uid = authRepository.user?.uid ?: return null
        val id = directChatId(uid, peerUid)
        if (chatListRepository.chats.value.none { it.id == id }) {
            firestore.collection("chats").document(id)
                .set(mapOf("members" to listOf(uid, peerUid)), SetOptions.merge())
        }
        return id
    }
}
