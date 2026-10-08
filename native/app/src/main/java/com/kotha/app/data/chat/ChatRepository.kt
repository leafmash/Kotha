package com.kotha.app.data.chat

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.Message
import com.kotha.app.data.model.toChat
import com.kotha.app.data.model.toMessage
import com.kotha.app.data.net.NetworkMonitor
import com.kotha.app.data.net.PushOutbox
import com.kotha.app.data.net.PushTrigger
import com.kotha.app.data.resilient
import com.kotha.app.data.snapshotFlow
import com.kotha.app.util.StoredText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ReplyRef(val id: String, val text: String)

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val pushTrigger: PushTrigger,
    private val pushOutbox: PushOutbox,
    networkMonitor: NetworkMonitor,
    @ApplicationScope private val scope: CoroutineScope
) {

    init {
        scope.launch {
            combine(authRepository.currentUser.map { it?.uid }, networkMonitor.online) { uid, online -> uid to online }
                .filter { (uid, online) -> uid != null && online }
                .distinctUntilChanged()
                .collect { (uid, _) -> flushOutbox(uid.orEmpty()) }
        }
    }

    private fun chatRef(chatId: String) = firestore.collection("chats").document(chatId)

    private fun messagesRef(chatId: String) = chatRef(chatId).collection("messages")

    private fun messageRef(chatId: String, messageId: String) = messagesRef(chatId).document(messageId)

    private suspend fun existsInCache(ref: DocumentReference): Boolean = try {
        ref.get(Source.CACHE).await().exists()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    fun observeChat(chatId: String): Flow<Chat?> =
        chatRef(chatId).snapshotFlow().map { if (it.exists()) it.toChat() else null }.resilient()

    fun observeMessages(chatId: String, limit: Int): Flow<List<Message>> =
        chatRef(chatId).collection("messages")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshotFlow(includeMetadata = true)
            .map { snapshot -> snapshot.documents.map { it.toMessage() } }
            .resilient()

    fun newMessageId(chatId: String): String = messagesRef(chatId).document().id

    suspend fun send(
        chatId: String,
        members: List<String>,
        payload: Map<String, Any>,
        reply: ReplyRef?,
        messageId: String? = null
    ) {
        val uid = authRepository.user?.uid ?: return
        scope.async { performSend(chatId, members, uid, payload, reply, messageId) }.await()
    }

    private suspend fun performSend(
        chatId: String,
        members: List<String>,
        uid: String,
        payload: Map<String, Any>,
        reply: ReplyRef?,
        messageId: String?
    ) {
        val ref = if (messageId != null) messagesRef(chatId).document(messageId) else messagesRef(chatId).document()
        if (messageId != null && existsInCache(ref)) return
        val message = mutableMapOf<String, Any>(
            "from" to uid,
            "type" to "text",
            "text" to "",
            "at" to FieldValue.serverTimestamp(),
            "status" to "sent"
        )
        message.putAll(payload)
        if (reply != null) message["replyTo"] = mapOf("text" to reply.text, "id" to reply.id)
        val type = message["type"] as? String ?: "text"
        val text = message["text"] as? String ?: ""
        val name = message["name"] as? String ?: ""
        val unread = members.filter { it != uid }.associateWith { FieldValue.increment(1) }
        val chatPatch = mapOf(
            "lastMessage" to previewStored(type, text, name),
            "lastFrom" to uid,
            "lastAt" to FieldValue.serverTimestamp(),
            "typing" to mapOf(uid to false),
            "unread" to unread
        )
        val batch = firestore.batch()
        batch.set(ref, message)
        batch.set(chatRef(chatId), chatPatch, SetOptions.merge())
        pushOutbox.markInflight(ref.id)
        pushOutbox.add(uid, chatId, ref.id)
        try {
            batch.commit().await()
        } catch (e: Exception) {
            pushOutbox.clearInflight(ref.id)
            pushOutbox.drop(ref.id)
            throw e
        }
        pushTrigger.notifyMessage(chatId, ref.id)
        pushOutbox.clearInflight(ref.id)
        pushOutbox.drop(ref.id)
    }

    fun setTyping(chatId: String, value: Boolean) {
        val uid = authRepository.user?.uid ?: return
        chatRef(chatId).set(mapOf("typing" to mapOf(uid to value)), SetOptions.merge())
    }

    fun markRead(chatId: String) {
        val uid = authRepository.user?.uid ?: return
        chatRef(chatId).set(
            mapOf(
                "unread" to mapOf(uid to 0),
                "readAt" to mapOf(uid to FieldValue.serverTimestamp())
            ),
            SetOptions.merge()
        )
    }

    fun markSeen(chatId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        firestore.runBatch { batch ->
            messageIds.forEach { batch.update(messageRef(chatId, it), "status", "seen") }
        }
    }

    fun react(chatId: String, messageId: String, emoji: String, current: String?) {
        val uid = authRepository.user?.uid ?: return
        val value: Any = if (current == emoji) FieldValue.delete() else emoji
        messageRef(chatId, messageId).update("reactions.$uid", value)
    }

    suspend fun deleteMessages(chatId: String, targets: List<Message>, forAll: Boolean, newestId: String?) {
        val uid = authRepository.user?.uid ?: return
        val batch = firestore.batch()
        targets.forEach { message ->
            val ref = messageRef(chatId, message.id)
            if (forAll && !message.deleted) {
                batch.update(ref, mapOf("deleted" to true, "text" to "", "url" to ""))
            } else {
                batch.update(ref, "hiddenFor", FieldValue.arrayUnion(uid))
            }
        }
        batch.commit().await()
        if (forAll && targets.any { it.id == newestId }) {
            chatRef(chatId).set(mapOf("lastMessage" to StoredText.token("deleted")), SetOptions.merge()).await()
        }
    }

    suspend fun editMessage(chatId: String, messageId: String, text: String, wasLast: Boolean) {
        messageRef(chatId, messageId).update(
            mapOf("text" to text, "edited" to true, "editedAt" to FieldValue.serverTimestamp())
        ).await()
        if (wasLast) chatRef(chatId).set(mapOf("lastMessage" to text), SetOptions.merge()).await()
    }

    suspend fun forward(targets: List<Pair<String, List<String>>>, payloads: List<Map<String, Any>>): Int =
        coroutineScope {
            targets.map { (chatId, members) ->
                async {
                    runCatching { payloads.forEach { send(chatId, members, it, null) } }
                }
            }.awaitAll().count { it.isFailure }
        }

    suspend fun unblock(peerUid: String) {
        val uid = authRepository.user?.uid ?: return
        firestore.collection("users").document(uid).collection("blocked").document(peerUid).delete().await()
    }

    private suspend fun flushOutbox(uid: String) {
        try {
            firestore.waitForPendingWrites().await()
        } catch (e: Exception) {
            return
        }
        pushOutbox.takePending(uid).forEach { pushTrigger.notifyMessage(it.chatId, it.messageId) }
    }
}
