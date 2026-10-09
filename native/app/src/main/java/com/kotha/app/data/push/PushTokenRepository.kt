package com.kotha.app.data.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.auth.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class PushTokenRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val prefs = context.getSharedPreferences("cova_push_token", Context.MODE_PRIVATE)

    fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    suspend fun sync() {
        val uid = authRepository.user?.uid ?: return
        if (!notificationsEnabled()) return
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        save(uid, token)
    }

    fun onNewToken(token: String) {
        scope.launch {
            val uid = withTimeoutOrNull(AUTH_WAIT_MS) { authRepository.currentUser.first() }?.uid ?: return@launch
            if (!notificationsEnabled()) return@launch
            save(uid, token)
        }
    }

    suspend fun release() {
        val savedUid = prefs.getString(KEY_UID, null) ?: return
        val savedToken = prefs.getString(KEY_TOKEN, null).orEmpty()
        val uid = authRepository.user?.uid
        if (uid != null && uid == savedUid && savedToken.isNotEmpty()) {
            attempt {
                firestore.collection("pushTokens").document(uid)
                    .set(mapOf("tokens" to FieldValue.arrayRemove(savedToken)), SetOptions.merge())
                    .await()
            }
        }
        attempt { FirebaseMessaging.getInstance().deleteToken().await() }
        prefs.edit().clear().apply()
    }

    private suspend fun save(uid: String, token: String) {
        if (token.isEmpty() || isFresh(uid, token)) return
        val saved = attempt {
            firestore.collection("pushTokens").document(uid)
                .set(mapOf("tokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
                .await()
        }
        if (saved) {
            prefs.edit()
                .putString(KEY_UID, uid)
                .putString(KEY_TOKEN, token)
                .putLong(KEY_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private fun isFresh(uid: String, token: String): Boolean {
        if (prefs.getString(KEY_UID, null) != uid) return false
        if (prefs.getString(KEY_TOKEN, null) != token) return false
        return System.currentTimeMillis() - prefs.getLong(KEY_AT, 0L) < FRESH_MS
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_TOKEN = "token"
        const val KEY_AT = "saved_at"
        const val AUTH_WAIT_MS = 4_000L
        const val FRESH_MS = 3L * 24 * 60 * 60 * 1000
    }
}
