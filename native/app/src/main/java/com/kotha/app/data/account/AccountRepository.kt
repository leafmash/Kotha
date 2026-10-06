package com.kotha.app.data.account

import com.google.firebase.firestore.FirebaseFirestore
import com.kotha.app.BuildConfig
import com.kotha.app.data.auth.AuthRepository
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class DeleteAccountException(val needsReauth: Boolean) : Exception()

@Singleton
class AccountRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
) {

    suspend fun deleteAccount() {
        val token = authRepository.idToken(true)
        withContext(Dispatchers.IO) {
            val connection = URL(BuildConfig.API_BASE + "/api/delete-account").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write("{}".toByteArray()) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw DeleteAccountException(body.contains("\"reauth\""))
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun finishDeletion() {
        authRepository.signOut()
        runCatching {
            firestore.terminate().await()
            firestore.clearPersistence().await()
        }
    }
}
