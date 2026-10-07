package com.kotha.app.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.core.AppConfig
import com.kotha.app.data.app.AppForeground
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.chat.ContactRepository
import com.kotha.app.data.chat.DraftStore
import com.kotha.app.data.chat.isCleared
import com.kotha.app.data.chat.isMuted
import com.kotha.app.data.chat.unreadOf
import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.ChatPrefs
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.net.NetworkMonitor
import com.kotha.app.data.presence.PresenceRepository
import com.kotha.app.data.presence.RtPresence
import com.kotha.app.data.user.UserRepository
import com.kotha.app.util.ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ChatRowUi(
    val id: String,
    val peerUid: String,
    val name: String,
    val photo: String,
    val group: Boolean,
    val lastMessage: String,
    val lastFromMe: Boolean,
    val senderName: String,
    val hiddenLast: Boolean,
    val draft: String,
    val timeMs: Long,
    val unread: Int,
    val muted: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val online: Boolean
)

data class ListBase(
    val rows: List<ChatRowUi> = emptyList(),
    val totalUnread: Int = 0,
    val archivedUnread: Boolean = false,
    val ready: Boolean = false
)

private data class ListData(
    val chats: List<Chat>,
    val prefs: ChatPrefs,
    val blocked: Set<String>,
    val cleared: Map<String, Long>,
    val users: Map<String, UserProfile>
)

private data class ListExtras(
    val active: String?,
    val foreground: Boolean,
    val drafts: Map<String, String>,
    val rt: Map<String, RtPresence>,
    val now: Long
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatListRepository: ChatListRepository,
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository,
    private val presenceRepository: PresenceRepository,
    private val authRepository: AuthRepository,
    @Suppress("UnusedPrivateProperty") private val chatRepository: ChatRepository,
    appForeground: AppForeground,
    draftStore: DraftStore,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val online: StateFlow<Boolean> = networkMonitor.online

    private val data = combine(
        chatListRepository.chats,
        chatListRepository.prefs,
        chatListRepository.blocked,
        chatListRepository.cleared,
        userRepository.users
    ) { chats, prefs, blocked, cleared, users -> ListData(chats, prefs, blocked, cleared, users) }

    private val extras = combine(
        chatListRepository.activeChatId,
        appForeground.foreground,
        draftStore.drafts,
        presenceRepository.peers,
        ticker(AppConfig.PRESENCE_TICK_MS)
    ) { active, foreground, drafts, rt, now -> ListExtras(active, foreground, drafts, rt, now) }

    val base: StateFlow<ListBase> = combine(data, extras, chatListRepository.ready) { d, e, ready ->
        compute(d, e, ready)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListBase())

    fun openDirect(peerUid: String): String? = contactRepository.openDirect(peerUid)

    private fun compute(d: ListData, e: ListExtras, ready: Boolean): ListBase {
        val uid = authRepository.user?.uid.orEmpty()
        var total = 0
        var archivedUnread = false
        val rows = d.chats
            .filter { it.lastMessage.isNotEmpty() && !isCleared(it, d.cleared) }
            .sortedByDescending { it.lastAtMs }
            .mapNotNull { chat ->
                val unread = unreadOf(chat, uid, e.active, e.foreground, d.blocked)
                val muted = isMuted(d.prefs, chat.id, e.now)
                val archived = d.prefs.archived.containsKey(chat.id)
                if (!muted) total += unread
                if (archived && !muted && unread > 0) archivedUnread = true
                val draft = if (e.active == chat.id) "" else e.drafts[chat.id].orEmpty().trim()
                if (chat.group) {
                    val hiddenLast = d.blocked.contains(chat.lastFrom)
                    val sender = if (chat.lastFrom != uid && chat.lastFrom.isNotEmpty() && !hiddenLast) {
                        d.users[chat.lastFrom]?.name.orEmpty()
                    } else {
                        ""
                    }
                    ChatRowUi(
                        id = chat.id,
                        peerUid = "",
                        name = chat.name,
                        photo = chat.photo,
                        group = true,
                        lastMessage = chat.lastMessage,
                        lastFromMe = chat.lastFrom == uid,
                        senderName = sender,
                        hiddenLast = hiddenLast,
                        draft = draft,
                        timeMs = chat.lastAtMs,
                        unread = unread,
                        muted = muted,
                        pinned = d.prefs.pinned.containsKey(chat.id),
                        archived = archived,
                        online = false
                    )
                } else {
                    val peerId = chat.peerId(uid)
                    val peer = d.users[peerId]
                    if (peer == null || d.blocked.contains(peerId)) {
                        null
                    } else {
                        ChatRowUi(
                            id = chat.id,
                            peerUid = peerId,
                            name = peer.name,
                            photo = peer.photo,
                            group = false,
                            lastMessage = chat.lastMessage,
                            lastFromMe = chat.lastFrom == uid,
                            senderName = "",
                            hiddenLast = false,
                            draft = draft,
                            timeMs = chat.lastAtMs,
                            unread = unread,
                            muted = muted,
                            pinned = d.prefs.pinned.containsKey(chat.id),
                            archived = archived,
                            online = presenceRepository.presenceOf(peer, e.rt[peerId]).online
                        )
                    }
                }
            }
        return ListBase(rows, total, archivedUnread, ready)
    }
}
