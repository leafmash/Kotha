package com.kotha.app.data.group

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.model.Chat
import com.kotha.app.data.profile.PhotoUploader
import com.kotha.app.data.user.UserRepository
import com.kotha.app.util.StoredText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class GroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val photoUploader: PhotoUploader
) {

    suspend fun create(name: String, photo: Uri?, picked: List<String>): String {
        val uid = authRepository.user?.uid ?: error("signed out")
        val photoUrl = if (photo != null) photoUploader.upload(photo) else ""
        val data = mutableMapOf<String, Any>(
            "group" to true,
            "name" to name,
            "admin" to uid,
            "admins" to listOf(uid),
            "members" to listOf(uid) + picked,
            "lastMessage" to StoredText.token("groupCreated"),
            "lastFrom" to uid,
            "lastAt" to FieldValue.serverTimestamp()
        )
        if (photoUrl.isNotEmpty()) data["photo"] = photoUrl
        return firestore.collection("chats").add(data).await().id
    }

    suspend fun addMembers(chatId: String, ids: List<String>, names: List<String>) {
        val uid = authRepository.user?.uid ?: error("signed out")
        batch(
            chatId,
            mapOf("kind" to "added", "target" to ids, "names" to names),
            mapOf(
                "members" to FieldValue.arrayUnion(*ids.toTypedArray()),
                "lastMessage" to StoredText.token("membersAdded"),
                "lastFrom" to uid,
                "lastAt" to FieldValue.serverTimestamp()
            )
        )
    }

    suspend fun removeMember(chat: Chat, id: String, name: String) {
        val patch = mutableMapOf<String, Any>(
            "members" to FieldValue.arrayRemove(id),
            "unread.$id" to FieldValue.delete(),
            "typing.$id" to FieldValue.delete()
        )
        if (chat.admins.contains(id)) patch["admins"] = FieldValue.arrayRemove(id)
        batch(chat.id, mapOf("kind" to "removed", "target" to listOf(id), "names" to listOf(name)), patch)
    }

    suspend fun setAdmin(chatId: String, id: String, name: String, make: Boolean) {
        batch(
            chatId,
            mapOf("kind" to if (make) "promoted" else "demoted", "target" to listOf(id), "names" to listOf(name)),
            mapOf("admins" to if (make) FieldValue.arrayUnion(id) else FieldValue.arrayRemove(id))
        )
    }

    suspend fun rename(chatId: String, name: String) {
        batch(chatId, mapOf("kind" to "renamed", "name" to name), mapOf("name" to name))
    }

    suspend fun setDescription(chatId: String, text: String) {
        batch(chatId, mapOf("kind" to "desc"), mapOf("description" to text))
    }

    suspend fun setPhoto(chatId: String, photo: Uri) {
        val url = photoUploader.upload(photo)
        batch(chatId, mapOf("kind" to "photo"), mapOf("photo" to url))
    }

    suspend fun setAdminOnly(chatId: String, enabled: Boolean) {
        firestore.collection("chats").document(chatId).update(mapOf("adminOnly" to enabled)).await()
    }

    suspend fun leave(chat: Chat) {
        val uid = authRepository.user?.uid ?: error("signed out")
        val rest = chat.members.filter { it != uid }
        val admins = chat.adminIds()
        val patch = mutableMapOf<String, Any>("members" to FieldValue.arrayRemove(uid))
        if (admins.contains(uid) && rest.isNotEmpty()) {
            val others = admins.filter { it != uid }
            val successor = others.firstOrNull() ?: rest.first()
            if (chat.admin == uid) patch["admin"] = successor
            if (others.isEmpty()) {
                patch["admins"] = listOf(successor)
            } else if (chat.admins.contains(uid)) {
                patch["admins"] = chat.admins.filter { it != uid }
            }
        }
        batch(chat.id, mapOf("kind" to "left"), patch)
    }

    private suspend fun batch(chatId: String, sys: Map<String, Any>, patch: Map<String, Any>) {
        val uid = authRepository.user?.uid ?: error("signed out")
        val chat = firestore.collection("chats").document(chatId)
        val write = firestore.batch()
        write.set(
            chat.collection("messages").document(),
            mapOf(
                "from" to uid,
                "type" to "system",
                "text" to "",
                "at" to FieldValue.serverTimestamp(),
                "sys" to (mapOf<String, Any>("by" to userRepository.users.value[uid]?.name.orEmpty()) + sys)
            )
        )
        write.update(chat, patch)
        write.commit().await()
    }
}
