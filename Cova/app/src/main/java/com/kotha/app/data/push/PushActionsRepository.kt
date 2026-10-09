package com.kotha.app.data.push

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.kotha.app.core.AppConfig
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.model.toChat
import com.kotha.app.data.prefs.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class PushActionsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val preferences: AppPreferences
) {

    suspend fun currentUid(): String? =
        withTimeoutOrNull(AUTH_WAIT_MS) { authRepository.currentUser.first() }?.uid

    suspend fun reportDelivered(uid: String, chatId: String, messageId: String) {
        val ref = firestore.collection("chats").document(chatId).collection("messages").document(messageId)
        for (attempt in 0 until DELIVERY_ATTEMPTS) {
            if (attempt > 0) delay(DELIVERY_RETRY_MS * attempt)
            try {
                val snapshot = ref.get().await()
                if (!snapshot.exists()) return
                if (snapshot.getString("from") == uid) return
                if (snapshot.getString("status") != "sent") return
                ref.update("status", "delivered").await()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
        }
    }

    suspend fun markRead(chatId: String): Boolean {
        val uid = currentUid() ?: return false
        return markRead(uid, chatId)
    }

    suspend fun sendReply(chatId: String, messageId: String, text: String): PushOutcome {
        val uid = currentUid() ?: return PushOutcome.Failed
        return try {
            val snapshot = firestore.collection("chats").document(chatId).get().await()
            if (!snapshot.exists()) return PushOutcome.Failed
            val members = snapshot.toChat().members
            if (!members.contains(uid)) return PushOutcome.Failed
            chatRepository.send(chatId, members, mapOf("type" to "text", "text" to text), null, messageId)
            markRead(uid, chatId)
            PushOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) PushOutcome.Failed else PushOutcome.Retry
        } catch (e: Exception) {
            PushOutcome.Retry
        }
    }

    private suspend fun markRead(uid: String, chatId: String): Boolean = try {
        val chatRef = firestore.collection("chats").document(chatId)
        val snapshot = chatRef.get().await()
        val unread = if (snapshot.exists()) snapshot.toChat().unread[uid] ?: 0L else 0L
        chatRef.set(
            mapOf(
                "unread" to mapOf(uid to 0),
                "readAt" to mapOf(uid to FieldValue.serverTimestamp())
            ),
            SetOptions.merge()
        ).await()
        if (preferences.readReceipts.value) markSeen(uid, chatRef, unread)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun markSeen(uid: String, chatRef: DocumentReference, unread: Long) {
        val limit = unread.coerceIn(1L, AppConfig.PAGE.toLong())
        val result = chatRef.collection("messages")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        val pending = result.documents.filter {
            it.getString("from") != uid &&
                it.getString("status") != "seen" &&
                it.getString("type") != "system"
        }
        if (pending.isEmpty()) return
        firestore.runBatch { batch ->
            pending.forEach { batch.update(it.reference, "status", "seen") }
        }.await()
    }

    private companion object {
        const val AUTH_WAIT_MS = 4_000L
        const val DELIVERY_ATTEMPTS = 3
        const val DELIVERY_RETRY_MS = 1_500L
    }
}
