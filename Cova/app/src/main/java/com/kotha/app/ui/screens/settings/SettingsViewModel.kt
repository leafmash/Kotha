package com.kotha.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.prefs.AppPreferences
import com.kotha.app.data.prefs.ThemeMode
import com.kotha.app.data.session.SessionRepository
import com.kotha.app.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val sessionRepository: SessionRepository,
    userRepository: UserRepository,
    chatListRepository: ChatListRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val uid: String = authRepository.user?.uid.orEmpty()
    val email: String = authRepository.user?.email.orEmpty()

    val profile: StateFlow<UserProfile?> = userRepository.users
        .map { it[uid] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, userRepository.users.value[uid])

    val blockedCount: StateFlow<Int> = chatListRepository.blocked
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, chatListRepository.blocked.value.size)

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
    val fontScale: StateFlow<Float> = preferences.fontScale
    val readReceipts: StateFlow<Boolean> = preferences.readReceipts

    init {
        userRepository.watch(uid)
    }

    fun setTheme(mode: ThemeMode) = preferences.setTheme(mode)

    fun setFontScale(scale: Float) = preferences.setFontScale(scale)

    fun setReadReceipts(enabled: Boolean) = preferences.setReadReceipts(enabled)

    fun signOut() = sessionRepository.signOut()
}
