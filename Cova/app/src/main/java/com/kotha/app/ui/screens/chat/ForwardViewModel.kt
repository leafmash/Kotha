package com.kotha.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.R
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.chat.MessageRules
import com.kotha.app.data.model.Message
import com.kotha.app.data.net.NetworkMonitor
import com.kotha.app.data.user.UserRepository
import com.kotha.app.ui.UiMessage
import com.kotha.app.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ForwardTarget(
    val id: String,
    val members: List<String>,
    val name: String,
    val photo: String
)

@HiltViewModel
class ForwardViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkMonitor,
    authRepository: AuthRepository,
    chatListRepository: ChatListRepository,
    userRepository: UserRepository
) : ViewModel() {

    private val uid = authRepository.user?.uid.orEmpty()

    val targets: StateFlow<List<ForwardTarget>> = combine(
        chatListRepository.chats,
        userRepository.users,
        chatListRepository.blocked
    ) { chats, users, blocked ->
        chats
            .filter { it.members.contains(uid) }
            .sortedByDescending { it.lastAtMs }
            .mapNotNull { chat ->
                if (chat.group) {
                    if (chat.adminOnly && !chat.isAdmin(uid)) null
                    else ForwardTarget(chat.id, chat.members, chat.name, chat.photo)
                } else {
                    val peerId = chat.peerId(uid)
                    val peer = users[peerId]
                    if (peer == null || blocked.contains(peerId)) null
                    else ForwardTarget(chat.id, chat.members, peer.name, peer.photo)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    fun send(messages: List<Message>, ids: Set<String>, onResult: (UiMessage) -> Unit) {
        if (mutableBusy.value || ids.isEmpty() || messages.isEmpty()) return
        if (!networkMonitor.online.value) {
            onResult(UiMessage(R.string.fwd_offline))
            return
        }
        mutableBusy.value = true
        val chosen = targets.value.filter { it.id in ids }.map { it.id to it.members }
        val payloads = messages.filter { MessageRules.canForward(it) }.map { MessageRules.forwardPayload(it) }
        viewModelScope.launch {
            val failed = chatRepository.forward(chosen, payloads)
            mutableBusy.value = false
            onResult(
                if (failed > 0) UiMessage(R.string.fwd_partial, listOf(Format.number(failed))) else UiMessage(R.string.fwd_done)
            )
        }
    }
}
