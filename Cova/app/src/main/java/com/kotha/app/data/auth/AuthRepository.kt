package com.kotha.app.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.kotha.app.util.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val pendingProfile: PendingProfile
) {

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val user: FirebaseUser?
        get() = auth.currentUser

    fun providerIds(): List<String> = auth.currentUser?.providerData.orEmpty().map { it.providerId }

    fun hasPasswordProvider(user: FirebaseUser): Boolean = user.providerData.any { it.providerId == "password" }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUpWithEmail(name: String, email: String, password: String) {
        pendingProfile.name = name
        val created = auth.createUserWithEmailAndPassword(email, password).await().user ?: return
        runCatching {
            created.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        }
        auth.setLanguageCode(AppLanguage.code())
        runCatching { created.sendEmailVerification().await() }
    }

    suspend fun signInWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.setLanguageCode(AppLanguage.code())
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun sendVerificationEmail() {
        val current = auth.currentUser ?: return
        auth.setLanguageCode(AppLanguage.code())
        current.sendEmailVerification().await()
    }

    suspend fun reauthWithPassword(password: String) {
        val current = auth.currentUser ?: return
        val email = current.email.orEmpty()
        current.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    suspend fun reauthWithGoogle(idToken: String) {
        val current = auth.currentUser ?: return
        current.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun idToken(forceRefresh: Boolean): String {
        val current = auth.currentUser ?: error("no user")
        return current.getIdToken(forceRefresh).await().token ?: error("no token")
    }

    fun signOut() = auth.signOut()
}
