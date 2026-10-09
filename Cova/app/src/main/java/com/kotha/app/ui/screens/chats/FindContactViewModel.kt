package com.kotha.app.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.data.chat.ContactRepository
import com.kotha.app.data.chat.LookupResult
import com.kotha.app.data.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

sealed interface FindState {
    data object Idle : FindState
    data object Invalid : FindState
    data object Loading : FindState
    data class Found(val profile: UserProfile, val email: String) : FindState
    data object Self : FindState
    data object None : FindState
    data object Failed : FindState
}

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FindContactViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val input = MutableStateFlow("")

    val state: StateFlow<FindState> = input
        .map { it.trim().lowercase() }
        .distinctUntilChanged()
        .transformLatest { term ->
            when {
                term.isEmpty() -> emit(FindState.Idle)
                !EMAIL_PATTERN.matches(term) -> emit(FindState.Invalid)
                else -> {
                    emit(FindState.Loading)
                    delay(300)
                    emit(
                        when (val result = contactRepository.lookup(term)) {
                            is LookupResult.Found -> FindState.Found(result.profile, term)
                            LookupResult.Self -> FindState.Self
                            LookupResult.None -> FindState.None
                            LookupResult.Failed -> FindState.Failed
                        }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FindState.Idle)

    fun onInput(value: String) {
        input.value = value
    }

    fun open(peerUid: String): String? = contactRepository.openDirect(peerUid)
}
