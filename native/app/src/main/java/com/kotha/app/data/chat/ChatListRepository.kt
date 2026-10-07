package com.kotha.app.data.chat

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.kotha.app.core.AppConfig
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.app.AppForeground
import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.ChatPrefs
import com.kotha.app.data.model.toChat
import com.kotha.app.data.model.toChatPrefs
import com.kotha.app.data.presence.PresenceRepository
import com.kotha.app.data.resilient
import com.kotha.app.data.session.SessionRepository
import com.kotha.app.data.session.SessionState
import com.kotha.app.data.snapshotFlow
import com.kotha.app.data.user.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class ChatListRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val appForeground: AppForeground,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val mutableChats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = mutableChats.asStateFlow()

    private val mutablePrefs = MutableStateFlow(ChatPrefs())
    val prefs: StateFlow<ChatPrefs> = mutablePrefs.asStateFlow()

    private val mutableBlocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = mutableBlocked.asStateFlow()

    private val mutableCleared = MutableStateFlow<Map<String, Long>>(emptyMap())
    val cleared: StateFlow<Map<String, Long>> = mutableCleared.asStateFlow()

    private val mutableReady = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = mutableReady.asStateFlow()

    private val mutableActive = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = mutableActive.asStateFlow()

    private val deliverKeys = HashMap<String, String>()

    init {
        scope.launch {
            sessionRepository.state
                .map { (it as? SessionState.SignedIn)?.uid }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    reset()
                    if (uid != null) coroutineScope { listen(uid) }
                }
        }
    }

    fun setActive(chatId: String?) {
        mutableActive.value = chatId
    }

    private fun reset() {
        mutableChats.value = emptyList()
        mutablePrefs.value = ChatPrefs()
        mutableBlocked.value = emptySet()
        mutableCleared.value = emptyMap()
        mutableReady.value = false
        deliverKeys.clear()
        userRepository.reset()
    }

    private fun CoroutineScope.listen(uid: String) {
        userRepository.watch(uid)
        val userRef = firestore.collection("users").document(uid)
        launch {
            userRef.collection("blocked").snapshotFlow().resilient().collect { snapshot ->
                val ids = snapshot.documents.map { it.id }
                mutableBlocked.value = ids.toSet()
                ids.forEach { userRepository.watch(it) }
            }
        }
        launch {
            userRef.collection("clears").snapshotFlow().resilient().collect { snapshot ->
                mutableCleared.value = snapshot.documents.associate {
                    it.id to (it.getTimestamp("at")?.toDate()?.time ?: 0L)
                }
            }
        }
        launch {
            firestore.collection("pushTokens").document(uid).snapshotFlow().resilient().collect { snapshot ->
                mutablePrefs.value = if (snapshot.exists()) snapshot.toChatPrefs() else ChatPrefs()
            }
        }
        launch {
            firestore.collection("chats").whereArrayContains("members", uid).snapshotFlow().resilient()
                .collect { snapshot -> handleChats(uid, snapshot) }
        }
    }

    private fun handleChats(uid: String, snapshot: QuerySnapshot) {
        val list = snapshot.documents.map { it.toChat() }.filter { it.members.contains(uid) }
        mutableChats.value = list
        mutableReady.value = true
        list.flatMap { it.members }.distinct().forEach { userRepository.watch(it) }
        markDelivered(uid, list)
    }

    private fun markDelivered(uid: String, list: List<Chat>) {
        list.forEach { chat ->
            val count = chat.unread[uid] ?: 0L
            if (count <= 0 || chat.lastFrom == uid) return@forEach
            if (mutableActive.value == chat.id && appForeground.foreground.value) return@forEach
            val key = "${chat.lastAtMs}:$count"
            if (deliverKeys[chat.id] == key) return@forEach
            deliverKeys[chat.id] = key
            scope.launch {
                try {
                    val result = firestore.collection("chats").document(chat.id).collection("messages")
                        .orderBy("at", Query.Direction.DESCENDING)
                        .limit(minOf(count, AppConfig.PAGE.toLong()))
                        .get()
                        .await()
                    val pending = result.documents.filter {
                        it.getString("from") != uid && it.getString("status") == "sent"
                    }
                    if (pending.isEmpty()) return@launch
                    if (mutableActive.value == chat.id && appForeground.foreground.value) return@launch
                    firestore.runBatch { batch ->
                        pending.forEach { batch.update(it.reference, "status", "delivered") }
                    }.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    deliverKeys.remove(chat.id)
                }
            }
        }
    }
}
