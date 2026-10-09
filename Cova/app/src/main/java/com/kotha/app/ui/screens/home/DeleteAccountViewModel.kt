package com.kotha.app.ui.screens.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.account.AccountRepository
import com.kotha.app.data.account.DeleteAccountException
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.auth.GoogleCancelledException
import com.kotha.app.data.auth.GoogleCredentialProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeleteMethod { Password, Google }

data class DeleteUiState(
    val method: DeleteMethod = DeleteMethod.Password,
    val canUseGoogle: Boolean = false,
    val password: String = "",
    val lockLeft: Int = 0,
    val busy: Boolean = false,
    @StringRes val error: Int? = null
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val googleProvider: GoogleCredentialProvider
) : ViewModel() {

    private val mutableState = MutableStateFlow(DeleteUiState())
    val state: StateFlow<DeleteUiState> = mutableState.asStateFlow()

    private var lockJob: Job? = null

    fun start() {
        val ids = authRepository.providerIds()
        val usesPassword = "password" in ids
        val usesGoogle = "google.com" in ids
        val method = if (usesPassword || !usesGoogle) DeleteMethod.Password else DeleteMethod.Google
        mutableState.value = DeleteUiState(
            method = method,
            canUseGoogle = method == DeleteMethod.Password && usesGoogle,
            lockLeft = AppConfig.DELETE_LOCK_SECONDS
        )
        lockJob?.cancel()
        lockJob = viewModelScope.launch {
            for (left in AppConfig.DELETE_LOCK_SECONDS downTo 1) {
                mutableState.update { it.copy(lockLeft = left) }
                delay(1_000)
            }
            mutableState.update { it.copy(lockLeft = 0) }
        }
    }

    fun onPassword(value: String) = mutableState.update { it.copy(password = value) }

    fun confirm(method: DeleteMethod, context: Context, onDeleted: suspend () -> Unit) {
        val current = mutableState.value
        if (current.busy || current.lockLeft > 0) return
        if (method == DeleteMethod.Password && current.password.isEmpty()) {
            mutableState.update { it.copy(error = R.string.delete_need_password) }
            return
        }
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                reauthenticate(method, current.password, context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoogleCancelledException) {
                mutableState.update { it.copy(busy = false) }
                return@launch
            } catch (e: Exception) {
                val wrong = e is FirebaseAuthException &&
                    (e.errorCode == "ERROR_WRONG_PASSWORD" || e.errorCode == "ERROR_INVALID_CREDENTIAL")
                mutableState.update {
                    it.copy(busy = false, error = if (wrong) R.string.delete_wrong_password else R.string.delete_failed)
                }
                return@launch
            }
            try {
                accountRepository.deleteAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reauth = e is DeleteAccountException && e.needsReauth
                mutableState.update {
                    it.copy(busy = false, error = if (reauth) R.string.delete_reauth else R.string.delete_failed)
                }
                return@launch
            }
            accountRepository.finishDeletion()
            onDeleted()
        }
    }

    private suspend fun reauthenticate(method: DeleteMethod, password: String, context: Context) {
        if (method == DeleteMethod.Password) {
            authRepository.reauthWithPassword(password)
        } else {
            authRepository.reauthWithGoogle(googleProvider.getIdToken(context))
        }
    }
}
