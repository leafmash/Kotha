package com.kotha.app.notify

import android.content.Context
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.app.AppForeground
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.isCleared
import com.kotha.app.data.chat.isMuted
import com.kotha.app.data.chat.unreadOf
import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.ChatPrefs
import com.kotha.app.data.session.SessionRepository
import com.kotha.app.data.session.SessionState
import com.kotha.app.util.ticker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class UnreadInput(
    val chats: List<Chat>,
    val prefs: ChatPrefs,
    val blocked: Set<String>,
    val cleared: Map<String, Long>,
    val ready: Boolean
)

@Singleton
class NotificationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val chatListRepository: ChatListRepository,
    private val appForeground: AppForeground,
    @ApplicationScope private val scope: CoroutineScope
) {

    private var started = false
    private var lastBadge = -1

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch { watchSession() }
        scope.launch { watchActiveChat() }
        scope.launch { watchUnread() }
    }

    private suspend fun watchSession() {
        sessionRepository.state
            .filter { it !is SessionState.Loading }
            .map { (it as? SessionState.SignedIn)?.uid }
            .distinctUntilChanged()
            .collect { uid ->
                if (uid == null) {
                    lastBadge = -1
                    MessageNotifier.reset(context)
                }
            }
    }

    private suspend fun watchActiveChat() {
        combine(chatListRepository.activeChatId, appForeground.foreground) { id, foreground ->
            if (foreground) id else null
        }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { MessageNotifier.clearChat(context, it, false) }
    }

    private suspend fun watchUnread() {
        val input = combine(
            chatListRepository.chats,
            chatListRepository.prefs,
            chatListRepository.blocked,
            chatListRepository.cleared,
            chatListRepository.ready
        ) { chats, prefs, blocked, cleared, ready -> UnreadInput(chats, prefs, blocked, cleared, ready) }
        val uids = sessionRepository.state.map { (it as? SessionState.SignedIn)?.uid }
        combine(
            input,
            chatListRepository.activeChatId,
            appForeground.foreground,
            uids,
            ticker(TICK_MS)
        ) { data, active, foreground, uid, now -> Snapshot(data, active, foreground, uid, now) }
            .collect { process(it) }
    }

    private fun process(snapshot: Snapshot) {
        val uid = snapshot.uid ?: return
        val data = snapshot.input
        if (!data.ready) return
        var total = 0
        for (chat in data.chats) {
            dismissHandled(chat, uid)
            if (chat.lastMessage.isEmpty() || isCleared(chat, data.cleared)) continue
            if (isMuted(data.prefs, chat.id, snapshot.now)) continue
            total += unreadOf(chat, uid, snapshot.active, snapshot.foreground, data.blocked)
        }
        if (total != lastBadge) {
            lastBadge = total
            BadgeHelper.apply(context, total)
        }
    }

    private fun dismissHandled(chat: Chat, uid: String) {
        val posted = ConversationStore.lastPostedAt(context, chat.id)
        if (posted == 0L || ConversationStore.getUnreadCount(context, chat.id) == 0) return
        val read = (chat.unread[uid] ?: 0L) == 0L && (chat.readAt[uid] ?: 0L) > posted
        val answered = chat.lastFrom == uid && chat.lastAtMs > posted
        if (read || answered) MessageNotifier.clearChat(context, chat.id, false)
    }

    private data class Snapshot(
        val input: UnreadInput,
        val active: String?,
        val foreground: Boolean,
        val uid: String?,
        val now: Long
    )

    private companion object {
        const val TICK_MS = 60_000L
    }
}
