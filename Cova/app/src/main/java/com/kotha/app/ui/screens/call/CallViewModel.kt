package com.kotha.app.ui.screens.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.data.call.AudioRoute
import com.kotha.app.data.call.AudioRoutes
import com.kotha.app.data.call.CallAudioRouter
import com.kotha.app.data.call.CallManager
import com.kotha.app.data.call.CallMedia
import com.kotha.app.data.call.CallState
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CallViewModel @Inject constructor(
    private val manager: CallManager,
    private val router: CallAudioRouter,
    userRepository: UserRepository
) : ViewModel() {

    val state: StateFlow<CallState?> = manager.state
    val media: StateFlow<CallMedia?> = manager.media
    val routes: StateFlow<AudioRoutes> = router.routes

    val peer: StateFlow<UserProfile?> = combine(manager.state, userRepository.users) { call, users ->
        call?.let { users[it.peerUid] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun accept() = manager.accept()

    fun decline() = manager.hangUp()

    fun hangUp() = manager.hangUp()

    fun toggleMute() = manager.toggleMute()

    fun toggleCamera() = manager.toggleCamera()

    fun flipCamera() = manager.flipCamera()

    fun selectRoute(route: AudioRoute) = router.select(route)

    fun toggleSpeaker() = router.toggleSpeaker()
}
