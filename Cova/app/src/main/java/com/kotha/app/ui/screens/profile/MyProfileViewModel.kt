package com.kotha.app.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.R
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.net.NetworkMonitor
import com.kotha.app.data.profile.ProfileRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountInfo(
    val email: String,
    val verified: Boolean,
    val passwordSignIn: Boolean,
    val googleSignIn: Boolean,
    val createdMs: Long
)

@HiltViewModel
class MyProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    userRepository: UserRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val uid: String = authRepository.user?.uid.orEmpty()
    val displayName: String = authRepository.user?.displayName.orEmpty()

    val profile: StateFlow<UserProfile?> = userRepository.users
        .map { it[uid] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, userRepository.users.value[uid])

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    private val mutableEvents = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val events: SharedFlow<UiMessage> = mutableEvents.asSharedFlow()

    init {
        userRepository.watch(uid)
    }

    fun account(): AccountInfo {
        val user = authRepository.user
        val providers = authRepository.providerIds()
        return AccountInfo(
            email = user?.email.orEmpty(),
            verified = user?.isEmailVerified == true,
            passwordSignIn = providers.contains("password"),
            googleSignIn = providers.contains("google.com"),
            createdMs = user?.metadata?.creationTimestamp ?: 0L
        )
    }

    fun save(name: String, bio: String, onDone: () -> Unit) {
        if (!networkMonitor.online.value) {
            mutableEvents.tryEmit(UiMessage(R.string.profile_offline))
            return
        }
        viewModelScope.launch {
            mutableBusy.value = true
            try {
                profileRepository.save(name, bio)
                mutableEvents.emit(UiMessage(R.string.profile_saved))
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEvents.emit(UiMessage(R.string.profile_save_fail))
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun changePhoto(uri: Uri) {
        if (!networkMonitor.online.value) {
            mutableEvents.tryEmit(UiMessage(R.string.chat_offline_media))
            return
        }
        viewModelScope.launch {
            mutableBusy.value = true
            mutableEvents.emit(UiMessage(R.string.photo_uploading))
            try {
                profileRepository.changePhoto(uri)
                mutableEvents.emit(UiMessage(R.string.photo_changed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEvents.emit(UiMessage(R.string.chat_upload_fail))
            } finally {
                mutableBusy.value = false
            }
        }
    }
}
