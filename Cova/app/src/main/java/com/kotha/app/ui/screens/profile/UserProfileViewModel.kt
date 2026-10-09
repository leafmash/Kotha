package com.kotha.app.ui.screens.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.R
import com.kotha.app.data.block.BlockRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.ContactRepository
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.presence.PresenceInfo
import com.kotha.app.data.presence.PresenceRepository
import com.kotha.app.data.report.ReportRepository
import com.kotha.app.data.report.ReportTarget
import com.kotha.app.data.user.UserRepository
import com.kotha.app.ui.UiMessage
import com.kotha.app.util.ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PresenceUi(val info: PresenceInfo, val now: Long)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val presenceRepository: PresenceRepository,
    private val blockRepository: BlockRepository,
    private val reportRepository: ReportRepository,
    private val contactRepository: ContactRepository,
    chatListRepository: ChatListRepository
) : ViewModel() {

    val uid: String = checkNotNull(savedStateHandle["uid"])
    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    val profile: StateFlow<UserProfile?> = userRepository.users
        .map { it[uid] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, userRepository.users.value[uid])

    val presence: StateFlow<PresenceUi?> = combine(
        userRepository.users,
        presenceRepository.peers,
        ticker(30_000)
    ) { users, peers, _ ->
        PresenceUi(presenceRepository.presenceOf(users[uid], peers[uid]), presenceRepository.serverNow())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val blocked: StateFlow<Boolean> = chatListRepository.blocked
        .map { it.contains(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, chatListRepository.blocked.value.contains(uid))

    private val mutableEvents = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val events: SharedFlow<UiMessage> = mutableEvents.asSharedFlow()

    init {
        userRepository.watch(uid)
    }

    fun openDirect(): String? = contactRepository.openDirect(uid)

    fun block() {
        val name = profile.value?.name.orEmpty()
        viewModelScope.launch {
            if (blockRepository.block(uid)) {
                mutableEvents.emit(UiMessage(R.string.block_done, listOf(name)))
            } else {
                mutableEvents.emit(UiMessage(R.string.block_fail))
            }
        }
    }

    fun unblock() {
        val name = profile.value?.name.orEmpty()
        viewModelScope.launch {
            if (blockRepository.unblock(uid)) {
                mutableEvents.emit(UiMessage(R.string.block_unblocked, listOf(name)))
            } else {
                mutableEvents.emit(UiMessage(R.string.block_unblock_fail))
            }
        }
    }

    fun report(reason: String, note: String, alsoBlock: Boolean) {
        viewModelScope.launch {
            val sent = reportRepository.submit(
                ReportTarget(type = "user", chatId = chatId, reportedUid = uid),
                reason,
                note,
                alsoBlock
            )
            mutableEvents.emit(UiMessage(if (sent) R.string.report_sent else R.string.report_fail))
        }
    }
}
