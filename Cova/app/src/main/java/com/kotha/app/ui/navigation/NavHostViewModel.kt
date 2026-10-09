package com.kotha.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.data.call.CallManager
import com.kotha.app.data.call.CallState
import com.kotha.app.data.push.PushTokenRepository
import com.kotha.app.data.session.SessionRepository
import com.kotha.app.data.session.SessionState
import com.kotha.app.notify.DeepLink
import com.kotha.app.notify.DeepLinkRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NavHostViewModel @Inject constructor(
    private val router: DeepLinkRouter,
    private val pushTokens: PushTokenRepository,
    callManager: CallManager,
    sessionRepository: SessionRepository
) : ViewModel() {

    val call: StateFlow<CallState?> = callManager.state

    val pending: StateFlow<DeepLink?> = router.pending

    val ready: StateFlow<Boolean> = sessionRepository.state
        .map { state -> state is SessionState.SignedIn && !state.needsTerms }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun consume(link: DeepLink) {
        router.consume(link)
    }

    fun syncPush() {
        viewModelScope.launch { pushTokens.sync() }
    }
}
