package com.kotha.app.ui.screens.chat

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.app.AppForeground
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.chat.DraftStore
import com.kotha.app.data.chat.ReplyRef
import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.Message
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.net.NetworkMonitor
import com.kotha.app.data.presence.PresenceInfo
import com.kotha.app.data.presence.PresenceRepository
import com.kotha.app.data.user.UserRepository
import com.kotha.app.util.ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HeaderStatus {
    data object None : HeaderStatus
    data class Typing(val names: List<String>) : HeaderStatus
    data class Active(val info: PresenceInfo, val now: Long) : HeaderStatus
    data class Members(val count: Int) : HeaderStatus
}

sealed interface ComposerMode {
    data object Enabled : ComposerMode
    data class Blocked(val peerUid: String, val name: String) : ComposerMode
    data object ReadOnly : ComposerMode
}

data class HeaderUi(
    val name: String,
    val photo: String,
    val online: Boolean,
    val status: HeaderStatus
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val chatListRepository: ChatListRepository,
    private val presenceRepository: PresenceRepository,
    private val draftStore: DraftStore,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    appForeground: AppForeground,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val uid: String = authRepository.user?.uid.orEmpty()

    private val directPeerId: String =
        if (chatId.contains("_")) chatId.split("_").firstOrNull { it != uid }.orEmpty() else ""

    private val limit = MutableStateFlow(AppConfig.PAGE)

    val chat: StateFlow<Chat?> = chatRepository.observeChat(chatId).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        chatListRepository.chats.value.firstOrNull { it.id == chatId }
    )

    private val messages: StateFlow<List<Message>> = limit
        .flatMapLatest { chatRepository.observeMessages(chatId, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val users: StateFlow<Map<String, UserProfile>> = userRepository.users

    val items: StateFlow<List<ChatItem>> = combine(
        messages,
        chatListRepository.blocked,
        chatListRepository.cleared,
        chat
    ) { list, blocked, cleared, current ->
        buildChatItems(list, uid, current?.group == true, blocked, cleared[chatId] ?: 0L)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasMore: StateFlow<Boolean> = combine(messages, limit) { list, max -> list.size >= max }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val header: StateFlow<HeaderUi> = combine(
        chat,
        userRepository.users,
        presenceRepository.peers,
        ticker(AppConfig.PRESENCE_TICK_MS)
    ) { current, all, rt, now ->
        if (current?.group == true) {
            val typers = current.typing
                .filter { it.value && it.key != uid }
                .keys
                .mapNotNull { all[it]?.name }
            HeaderUi(
                name = current.name,
                photo = current.photo,
                online = false,
                status = if (typers.isNotEmpty()) HeaderStatus.Typing(typers) else HeaderStatus.Members(current.members.size)
            )
        } else {
            val peerId = current?.peerId(uid)?.ifEmpty { directPeerId } ?: directPeerId
            val peer = all[peerId]
            val info = presenceRepository.presenceOf(peer, rt[peerId])
            val typing = current?.typing?.get(peerId) == true
            HeaderUi(
                name = peer?.name.orEmpty(),
                photo = peer?.photo.orEmpty(),
                online = info.online,
                status = when {
                    peer == null -> HeaderStatus.None
                    typing -> HeaderStatus.Typing(emptyList())
                    else -> HeaderStatus.Active(info, presenceRepository.serverNow())
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HeaderUi("", "", false, HeaderStatus.None))

    val composerMode: StateFlow<ComposerMode> = combine(
        chat,
        chatListRepository.blocked,
        userRepository.users
    ) { current, blocked, all ->
        when {
            current?.group == true ->
                if (current.adminOnly && !current.isAdmin(uid)) ComposerMode.ReadOnly else ComposerMode.Enabled
            else -> {
                val peerId = current?.peerId(uid)?.ifEmpty { directPeerId } ?: directPeerId
                if (peerId.isNotEmpty() && blocked.contains(peerId)) {
                    ComposerMode.Blocked(peerId, all[peerId]?.name.orEmpty())
                } else {
                    ComposerMode.Enabled
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ComposerMode.Enabled)

    val online: StateFlow<Boolean> = networkMonitor.online

    val replyTo = MutableStateFlow<ReplyRef?>(null)

    private val mutableEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val events: SharedFlow<Int> = mutableEvents.asSharedFlow()

    private var typing = false
    private var typingJob: Job? = null
    private var lastReadKey = ""

    init {
        chatListRepository.setActive(chatId)
        viewModelScope.launch {
            combine(chat, appForeground.foreground) { current, foreground -> current to foreground }
                .collect { (current, foreground) ->
                    val pending = current?.unread?.get(uid) ?: 0L
                    if (foreground && current != null && pending > 0) {
                        val key = "$pending:${current.lastAtMs}"
                        if (key != lastReadKey) {
                            lastReadKey = key
                            chatRepository.markRead(chatId)
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(messages, appForeground.foreground) { list, foreground ->
                if (foreground) {
                    list.filter { it.from != uid && it.type != "system" && it.status != "seen" }.map { it.id }
                } else {
                    emptyList()
                }
            }
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { ids -> chatRepository.markSeen(chatId, ids) }
        }
    }

    fun initialDraft(): String = draftStore.get(chatId)

    fun loadOlder() {
        if (hasMore.value) limit.update { it + AppConfig.PAGE }
    }

    fun onInput(text: String) {
        draftStore.set(chatId, text)
        if (text.isBlank()) {
            stopTyping()
            return
        }
        if (!typing) {
            typing = true
            chatRepository.setTyping(chatId, true)
        }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(AppConfig.TYPING_IDLE_MS)
            stopTyping()
        }
    }

    fun setReply(message: Message, previewText: String) {
        replyTo.value = ReplyRef(message.id, previewText)
    }

    fun clearReply() {
        replyTo.value = null
    }

    fun send(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val members = chat.value?.members ?: listOf(uid, directPeerId)
        val reply = replyTo.value
        replyTo.value = null
        stopTyping()
        draftStore.set(chatId, "")
        viewModelScope.launch {
            try {
                chatRepository.send(chatId, members, mapOf("type" to "text", "text" to value), reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val denied = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                mutableEvents.emit(if (denied) R.string.chat_cannot_send else R.string.chat_send_fail)
            }
        }
    }

    fun react(message: Message, emoji: String) {
        chatRepository.react(chatId, message.id, emoji, message.reactions[uid])
    }

    fun unblock(peerUid: String) {
        viewModelScope.launch {
            try {
                chatRepository.unblock(peerUid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEvents.emit(R.string.block_unblock_fail)
            }
        }
    }

    fun notifyCopied() {
        mutableEvents.tryEmit(R.string.chat_copied)
    }

    override fun onCleared() {
        if (chatListRepository.activeChatId.value == chatId) chatListRepository.setActive(null)
        stopTyping()
        super.onCleared()
    }

    private fun stopTyping() {
        typingJob?.cancel()
        if (typing) {
            typing = false
            chatRepository.setTyping(chatId, false)
        }
    }
}
