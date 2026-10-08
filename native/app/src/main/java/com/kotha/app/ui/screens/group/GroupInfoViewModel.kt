package com.kotha.app.ui.screens.group

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.R
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.chat.ContactRepository
import com.kotha.app.data.group.GroupRepository
import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.report.ReportRepository
import com.kotha.app.data.report.ReportTarget
import com.kotha.app.data.user.UserRepository
import com.kotha.app.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    chatRepository: ChatRepository,
    private val chatListRepository: ChatListRepository,
    private val groupRepository: GroupRepository,
    private val contactRepository: ContactRepository,
    private val reportRepository: ReportRepository,
    private val userRepository: UserRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val uid: String = authRepository.user?.uid.orEmpty()

    val chat: StateFlow<Chat?> = chatRepository.observeChat(chatId).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        chatListRepository.chats.value.firstOrNull { it.id == chatId }
    )

    val users: StateFlow<Map<String, UserProfile>> = userRepository.users
    val blocked: StateFlow<Set<String>> = chatListRepository.blocked

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    private val mutableEvents = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<UiMessage> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            chat.filterNotNull().collect { current -> current.members.forEach { userRepository.watch(it) } }
        }
    }

    fun nameOf(id: String): String = userRepository.users.value[id]?.name.orEmpty()

    fun rename(name: String) = work { groupRepository.rename(chatId, name) }

    fun describe(text: String) = work { groupRepository.setDescription(chatId, text) }

    fun toggleAdminOnly(enabled: Boolean) = work { groupRepository.setAdminOnly(chatId, enabled) }

    fun setAdmin(id: String, make: Boolean) = work { groupRepository.setAdmin(chatId, id, nameOf(id), make) }

    fun remove(id: String) {
        val current = chat.value ?: return
        work { groupRepository.removeMember(current, id, nameOf(id)) }
    }

    fun changePhoto(uri: Uri) = work(success = R.string.ginfo_photo_changed) {
        groupRepository.setPhoto(chatId, uri)
    }

    fun leave(onDone: () -> Unit) {
        val current = chat.value ?: return
        viewModelScope.launch {
            try {
                groupRepository.leave(current)
                mutableEvents.emit(UiMessage(R.string.group_left))
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEvents.emit(UiMessage(R.string.group_leave_fail))
            }
        }
    }

    fun openDirect(peerUid: String): String? = contactRepository.openDirect(peerUid)

    fun report(reason: String, note: String, alsoBlock: Boolean) {
        viewModelScope.launch {
            val sent = reportRepository.submit(ReportTarget(type = "group", chatId = chatId), reason, note, alsoBlock)
            mutableEvents.emit(UiMessage(if (sent) R.string.report_sent else R.string.report_fail))
        }
    }

    private fun work(success: Int? = null, action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableBusy.value = true
            try {
                action()
                if (success != null) mutableEvents.emit(UiMessage(success))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEvents.emit(UiMessage(R.string.group_action_fail))
            } finally {
                mutableBusy.value = false
            }
        }
    }
}
