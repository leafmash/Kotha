package com.kotha.app.ui.screens.group

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.ContactRepository
import com.kotha.app.data.chat.LookupResult
import com.kotha.app.data.group.GroupRepository
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.user.UserRepository
import com.kotha.app.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PickerMode { Create, Add }

data class PickerForm(
    val mode: PickerMode = PickerMode.Create,
    val chatId: String = "",
    val name: String = "",
    val cover: Uri? = null,
    val picked: List<String> = emptyList(),
    val extras: Map<String, UserProfile> = emptyMap(),
    val query: String = ""
)

sealed interface PickFind {
    data object Idle : PickFind
    data object Invalid : PickFind
    data object Loading : PickFind
    data object Self : PickFind
    data object None : PickFind
    data object Failed : PickFind
    data object Blocked : PickFind
    data object AlreadyIn : PickFind
    data class Found(val profile: UserProfile) : PickFind
}

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupPickerViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val contactRepository: ContactRepository,
    private val chatListRepository: ChatListRepository,
    userRepository: UserRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val uid: String = authRepository.user?.uid.orEmpty()
    private val mutableForm = MutableStateFlow(PickerForm())
    private val mutableBusy = MutableStateFlow(false)
    private val mutableEvents = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)

    val form: StateFlow<PickerForm> = mutableForm.asStateFlow()
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    val events: SharedFlow<UiMessage> = mutableEvents.asSharedFlow()

    val contacts: StateFlow<List<UserProfile>> = combine(
        mutableForm,
        chatListRepository.chats,
        userRepository.users,
        chatListRepository.blocked
    ) { current, chats, users, blocked ->
        val existing = existingMembers(current, chats.firstOrNull { it.id == current.chatId }?.members.orEmpty())
        val ids = LinkedHashSet<String>()
        chats.filter { !it.group }.forEach { ids.add(it.peerId(uid)) }
        ids.addAll(current.extras.keys)
        ids.filter { it.isNotEmpty() && it !in blocked && it !in existing }
            .mapNotNull { users[it] ?: current.extras[it] }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val find: StateFlow<PickFind> = mutableForm
        .map { it.query.trim().lowercase() }
        .distinctUntilChanged()
        .transformLatest { term ->
            when {
                term.isEmpty() -> emit(PickFind.Idle)
                !EMAIL_PATTERN.matches(term) -> emit(PickFind.Invalid)
                else -> {
                    emit(PickFind.Loading)
                    delay(300)
                    emit(resolve(term))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickFind.Idle)

    fun start(mode: PickerMode, chatId: String) {
        mutableForm.value = PickerForm(mode = mode, chatId = chatId)
        mutableBusy.value = false
    }

    fun setName(value: String) {
        mutableForm.update { it.copy(name = value.take(AppConfig.NAME_MAX)) }
    }

    fun setCover(uri: Uri?) {
        mutableForm.update { it.copy(cover = uri) }
    }

    fun setQuery(value: String) {
        mutableForm.update { it.copy(query = value) }
    }

    fun toggle(id: String) {
        mutableForm.update { if (id in it.picked) it.copy(picked = it.picked - id) else it.copy(picked = it.picked + id) }
    }

    fun pickFound(profile: UserProfile) {
        mutableForm.update {
            it.copy(
                extras = it.extras + (profile.uid to profile),
                picked = if (profile.uid in it.picked) it.picked else it.picked + profile.uid,
                query = ""
            )
        }
    }

    fun submit(onCreated: (String) -> Unit, onAdded: () -> Unit) {
        val current = mutableForm.value
        if (mutableBusy.value) return
        if (current.mode == PickerMode.Create) {
            if (current.name.trim().isEmpty() || current.picked.isEmpty()) {
                mutableEvents.tryEmit(UiMessage(R.string.group_need_name))
                return
            }
            mutableBusy.value = true
            viewModelScope.launch {
                try {
                    val id = groupRepository.create(current.name.trim(), current.cover, current.picked)
                    mutableBusy.value = false
                    onCreated(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableBusy.value = false
                    mutableEvents.emit(UiMessage(R.string.group_action_fail))
                }
            }
        } else {
            val chat = chatListRepository.chats.value.firstOrNull { it.id == current.chatId } ?: return
            val ids = current.picked.filter { it !in chat.members }
            if (ids.isEmpty()) return
            if (chat.members.size + ids.size > AppConfig.GROUP_MAX) {
                mutableEvents.tryEmit(UiMessage(R.string.group_full))
                return
            }
            mutableBusy.value = true
            viewModelScope.launch {
                try {
                    val names = ids.map { id -> userName(id, current) }
                    groupRepository.addMembers(chat.id, ids, names)
                    mutableBusy.value = false
                    onAdded()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableBusy.value = false
                    mutableEvents.emit(UiMessage(R.string.group_action_fail))
                }
            }
        }
    }

    private fun userName(id: String, current: PickerForm): String =
        contacts.value.firstOrNull { it.uid == id }?.name ?: current.extras[id]?.name.orEmpty()

    private fun existingMembers(current: PickerForm, members: List<String>): Set<String> =
        if (current.mode == PickerMode.Add) members.toSet() else emptySet()

    private suspend fun resolve(term: String): PickFind =
        when (val result = contactRepository.lookup(term)) {
            is LookupResult.Found -> {
                val id = result.profile.uid
                val members = chatListRepository.chats.value
                    .firstOrNull { it.id == mutableForm.value.chatId }?.members.orEmpty()
                when {
                    id in chatListRepository.blocked.value -> PickFind.Blocked
                    id in existingMembers(mutableForm.value, members) -> PickFind.AlreadyIn
                    else -> PickFind.Found(result.profile)
                }
            }
            LookupResult.Self -> PickFind.Self
            LookupResult.None -> PickFind.None
            LookupResult.Failed -> PickFind.Failed
        }
}
