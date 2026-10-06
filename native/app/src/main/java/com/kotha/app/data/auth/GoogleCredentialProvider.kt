package com.kotha.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kotha.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

class GoogleCancelledException : Exception()

@Singleton
class GoogleCredentialProvider @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    suspend fun getIdToken(activityContext: Context): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(appContext.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw GoogleCancelledException()
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("unsupported credential")
    }
}
