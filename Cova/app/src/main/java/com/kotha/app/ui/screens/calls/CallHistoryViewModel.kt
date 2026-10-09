package com.kotha.app.ui.screens.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.call.CallKind
import com.kotha.app.data.call.CallRecord
import com.kotha.app.data.call.CallRepository
import com.kotha.app.data.call.toRecord
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class CallRowUi(
    val record: CallRecord,
    val name: String,
    val photo: String
)

private const val MAX_ROWS = 100

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val repository: CallRepository,
    private val userRepository: UserRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val records = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.observeHistory(user.uid).map { docs ->
                    val now = System.currentTimeMillis()
                    docs.map { it.toRecord(user.uid, now) }
                        .filter { it.kind != CallKind.Ringing || now - it.atMs < 60_000L }
                        .sortedByDescending { it.atMs }
                        .take(MAX_ROWS)
                }
            }
        }
        .onEach { list -> list.forEach { userRepository.watch(it.peerUid) } }

    val rows: StateFlow<List<CallRowUi>> = combine(records, userRepository.users) { list, users ->
        list.map { record ->
            val profile: UserProfile? = users[record.peerUid]
            CallRowUi(record, profile?.name.orEmpty(), profile?.photo.orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
