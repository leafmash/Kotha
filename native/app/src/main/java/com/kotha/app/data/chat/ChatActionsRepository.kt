package com.kotha.app.data.chat

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Singleton
class ChatActionsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val chatListRepository: ChatListRepository
) {

    private fun prefsRef(uid: String) = firestore.collection("pushTokens").document(uid)

    suspend fun togglePin(chatId: String): Int {
        val uid = authRepository.user?.uid ?: return R.string.pin_fail
        val prefs = chatListRepository.prefs.value
        val pinned = prefs.pinned.containsKey(chatId)
        if (!pinned && prefs.pinned.size >= AppConfig.MAX_PINNED) return R.string.pin_max
        val patch: Map<String, Any> = if (pinned) {
            mapOf("pinned" to mapOf(chatId to FieldValue.delete()))
        } else {
            mapOf(
                "pinned" to mapOf(chatId to System.currentTimeMillis()),
                "archived" to mapOf(chatId to FieldValue.delete())
            )
        }
        return write(uid, patch, if (pinned) R.string.pin_undone else R.string.pin_done, R.string.pin_fail)
    }

    suspend fun toggleArchive(chatId: String): Int {
        val uid = authRepository.user?.uid ?: return R.string.archive_fail
        val archived = chatListRepository.prefs.value.archived.containsKey(chatId)
        val patch: Map<String, Any> = if (archived) {
            mapOf("archived" to mapOf(chatId to FieldValue.delete()))
        } else {
            mapOf(
                "archived" to mapOf(chatId to System.currentTimeMillis()),
                "pinned" to mapOf(chatId to FieldValue.delete())
            )
        }
        return write(uid, patch, if (archived) R.string.archive_undone else R.string.archive_done, R.string.archive_fail)
    }

    suspend fun mute(chatId: String, until: Long): Int {
        val uid = authRepository.user?.uid ?: return R.string.mute_fail
        val value: Any = if (until > 0) until else FieldValue.delete()
        return write(
            uid,
            mapOf("muted" to mapOf(chatId to value)),
            if (until > 0) R.string.mute_done else R.string.mute_undone,
            R.string.mute_fail
        )
    }

    suspend fun clearChats(ids: List<String>): Int {
        val uid = authRepository.user?.uid ?: return R.string.chat_delete_fail
        var failed = 0
        ids.forEach { id ->
            try {
                firestore.collection("users").document(uid).collection("clears").document(id)
                    .set(mapOf("at" to FieldValue.serverTimestamp())).await()
                val unread = chatListRepository.chats.value.firstOrNull { it.id == id }?.unread?.get(uid) ?: 0L
                if (unread > 0) {
                    firestore.collection("chats").document(id).set(
                        mapOf(
                            "unread" to mapOf(uid to 0),
                            "readAt" to mapOf(uid to FieldValue.serverTimestamp())
                        ),
                        SetOptions.merge()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        return if (failed == 0) R.string.chat_conv_deleted else R.string.chat_delete_fail
    }

    fun block(peerUid: String): Task<Void>? {
        val uid = authRepository.user?.uid ?: return null
        return firestore.collection("users").document(uid).collection("blocked").document(peerUid)
            .set(mapOf("at" to FieldValue.serverTimestamp()))
    }

    private suspend fun write(uid: String, patch: Map<String, Any>, ok: Int, fail: Int): Int = try {
        prefsRef(uid).set(patch, SetOptions.merge()).await()
        ok
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fail
    }
}
