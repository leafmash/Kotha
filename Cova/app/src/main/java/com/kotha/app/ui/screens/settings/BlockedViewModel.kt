package com.kotha.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.R
import com.kotha.app.data.block.BlockRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.user.UserRepository
import com.kotha.app.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockedEntry(val uid: String, val profile: UserProfile?)

@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val blockRepository: BlockRepository,
    private val userRepository: UserRepository,
    chatListRepository: ChatListRepository
) : ViewModel() {

    val entries: StateFlow<List<BlockedEntry>> = combine(
        chatListRepository.blocked,
        userRepository.users
    ) { blocked, users ->
        blocked.map { BlockedEntry(it, users[it]) }.sortedBy { it.profile?.name.orEmpty().lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val mutableEvents = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val events: SharedFlow<UiMessage> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            chatListRepository.blocked.collect { ids -> ids.forEach { userRepository.watch(it) } }
        }
    }

    fun unblock(entry: BlockedEntry) {
        viewModelScope.launch {
            if (blockRepository.unblock(entry.uid)) {
                mutableEvents.emit(UiMessage(R.string.block_unblocked, listOf(entry.profile?.name.orEmpty())))
            } else {
                mutableEvents.emit(UiMessage(R.string.block_unblock_fail))
            }
        }
    }
}
