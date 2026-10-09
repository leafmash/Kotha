package com.kotha.app.ui.screens.auth

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseTooManyRequestsException
import com.kotha.app.R
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.auth.GoogleCancelledException
import com.kotha.app.data.auth.GoogleCredentialProvider
import com.kotha.app.data.auth.authMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val signUp: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    @StringRes val info: Int? = null,
    val infoIsError: Boolean = false
)

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleProvider: GoogleCredentialProvider
) : ViewModel() {

    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    fun onName(value: String) = mutableState.update { it.copy(name = value) }

    fun onEmail(value: String) = mutableState.update { it.copy(email = value) }

    fun onPassword(value: String) = mutableState.update { it.copy(password = value) }

    fun toggleMode() = mutableState.update { it.copy(signUp = !it.signUp, error = null, info = null) }

    fun submit() {
        val current = mutableState.value
        if (current.busy) return
        val email = current.email.trim()
        val problem: Int? = when {
            email.isEmpty() -> R.string.auth_err_missing_email
            !EMAIL_REGEX.matches(email) -> R.string.auth_err_invalid_email
            current.password.isEmpty() -> R.string.auth_err_missing_password
            current.signUp && current.password.length < 6 -> R.string.auth_err_weak_password
            else -> null
        }
        if (problem != null) {
            mutableState.update { it.copy(error = problem, info = null) }
            return
        }
        mutableState.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                if (current.signUp) {
                    val name = current.name.trim().ifEmpty { email.substringBefore('@') }
                    authRepository.signUpWithEmail(name, email, current.password)
                } else {
                    authRepository.signInWithEmail(email, current.password)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.authMessage()) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun forgotPassword() {
        val current = mutableState.value
        if (current.busy) return
        val email = current.email.trim()
        if (!EMAIL_REGEX.matches(email)) {
            mutableState.update { it.copy(info = R.string.auth_reset_need_email, infoIsError = true, error = null) }
            return
        }
        mutableState.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                mutableState.update { it.copy(info = R.string.auth_reset_sent, infoIsError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val key = if (e is FirebaseTooManyRequestsException) R.string.auth_reset_busy else R.string.auth_reset_fail
                mutableState.update { it.copy(info = key, infoIsError = true) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val token = googleProvider.getIdToken(context)
                authRepository.signInWithGoogle(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoogleCancelledException) {
                return@launch
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.authMessage(R.string.auth_google_fail)) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
