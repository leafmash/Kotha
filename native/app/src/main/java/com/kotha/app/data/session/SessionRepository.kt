package com.kotha.app.data.session

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.auth.PendingProfile
import com.kotha.app.data.presence.PresenceRepository
import com.kotha.app.util.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val pendingProfile: PendingProfile,
    private val presenceRepository: PresenceRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableUnverifiedEmail = MutableStateFlow<String?>(null)
    val unverifiedEmail: StateFlow<String?> = mutableUnverifiedEmail.asStateFlow()

    init {
        scope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user == null) {
                    presenceRepository.unbind()
                    mutableUnverifiedEmail.value = null
                    mutableState.value = SessionState.SignedOut
                } else {
                    presenceRepository.bind(user.uid)
                    onSignedIn(user)
                }
            }
        }
    }

    fun acceptTerms() {
        val uid = (mutableState.value as? SessionState.SignedIn)?.uid ?: return
        mutableState.value = SessionState.SignedIn(uid, false)
        scope.launch {
            try {
                firestore.collection("users").document(uid).set(
                    mapOf(
                        "termsVersion" to AppConfig.TERMS_VERSION,
                        "termsAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = SessionState.SignedIn(uid, true)
            }
        }
    }

    fun declineTerms() {
        signOut()
    }

    fun signOut() {
        scope.launch {
            presenceRepository.leave()
            authRepository.signOut()
        }
    }

    suspend fun refreshVerification(): VerifyResult {
        val user = authRepository.user ?: return VerifyResult.Unchanged
        if (user.isEmailVerified) return VerifyResult.Unchanged
        try {
            user.reload().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return VerifyResult.Failed
        }
        val fresh = authRepository.user ?: return VerifyResult.Failed
        if (!fresh.isEmailVerified) return VerifyResult.NotYet
        try {
            fresh.getIdToken(true).await()
            registerLookup(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return VerifyResult.Failed
        }
        mutableUnverifiedEmail.value = null
        return VerifyResult.Verified
    }

    private suspend fun onSignedIn(user: FirebaseUser) {
        mutableState.value = SessionState.SignedIn(user.uid, false)
        mutableUnverifiedEmail.value =
            if (authRepository.hasPasswordProvider(user) && !user.isEmailVerified) user.email.orEmpty() else null
        val ref = firestore.collection("users").document(user.uid)
        try {
            val snap = ref.get().await()
            val needsTerms = !snap.exists() || snap.getString("termsVersion") != AppConfig.TERMS_VERSION
            mutableState.value = SessionState.SignedIn(user.uid, needsTerms)
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "uid" to user.uid,
                        "name" to initialName(user),
                        "photo" to (user.photoUrl?.toString() ?: "")
                    ),
                    SetOptions.merge()
                ).await()
                pendingProfile.name = null
            } else if (snap.contains("email")) {
                ref.update("email", FieldValue.delete()).await()
            }
            val lang = AppLanguage.code()
            if (!snap.exists() || snap.getString("lang") != lang) {
                ref.set(mapOf("lang" to lang), SetOptions.merge()).await()
            }
            registerLookup(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
    }

    private fun initialName(user: FirebaseUser): String {
        val pending = pendingProfile.name?.takeIf { it.isNotBlank() }
        val display = user.displayName?.takeIf { it.isNotBlank() }
        val prefix = user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        return pending ?: display ?: prefix ?: context.getString(R.string.common_user)
    }

    private suspend fun registerLookup(user: FirebaseUser) {
        val mail = user.email.orEmpty().lowercase()
        if (mail.isEmpty() || !user.isEmailVerified) return
        val ref = firestore.collection("emailLookup").document(mail)
        val snap = ref.get().await()
        if (!snap.exists() || snap.getString("uid") != user.uid) {
            ref.set(mapOf("uid" to user.uid)).await()
        }
    }
}
