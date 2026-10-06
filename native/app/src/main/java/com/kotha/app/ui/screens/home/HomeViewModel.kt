package com.kotha.app.ui.screens.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseTooManyRequestsException
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.session.SessionRepository
import com.kotha.app.data.session.SessionState
import com.kotha.app.data.session.VerifyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val needsTerms: StateFlow<Boolean> = sessionRepository.state
        .map { (it as? SessionState.SignedIn)?.needsTerms == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val unverifiedEmail: StateFlow<String?> = sessionRepository.unverifiedEmail

    private val mutableResendLocked = MutableStateFlow(false)
    val resendLocked: StateFlow<Boolean> = mutableResendLocked.asStateFlow()

    private val mutableMessages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = mutableMessages.asSharedFlow()

    fun acceptTerms() = sessionRepository.acceptTerms()

    fun declineTerms() = sessionRepository.declineTerms()

    fun signOut() = authRepository.signOut()

    fun resendVerification() {
        if (mutableResendLocked.value) return
        mutableResendLocked.value = true
        viewModelScope.launch {
            try {
                authRepository.sendVerificationEmail()
                mutableMessages.emit(R.string.verify_sent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableMessages.emit(if (e is FirebaseTooManyRequestsException) R.string.verify_busy else R.string.verify_fail)
            }
            delay(AppConfig.RESEND_LOCK_MS)
            mutableResendLocked.value = false
        }
    }

    fun checkVerified(announce: Boolean) {
        viewModelScope.launch {
            when (sessionRepository.refreshVerification()) {
                VerifyResult.Verified -> mutableMessages.emit(R.string.verify_verified)
                VerifyResult.NotYet -> if (announce) mutableMessages.emit(R.string.verify_not_yet)
                VerifyResult.Failed -> if (announce) mutableMessages.emit(R.string.verify_fail)
                VerifyResult.Unchanged -> Unit
            }
        }
    }
}
