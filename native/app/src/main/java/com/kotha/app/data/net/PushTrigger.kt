package com.kotha.app.data.net

import com.kotha.app.BuildConfig
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.auth.AuthRepository
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class PushTrigger @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    fun notifyMessage(chatId: String, messageId: String) {
        val payload = JSONObject()
            .put("type", "message")
            .put("chatId", chatId)
            .put("messageId", messageId)
        scope.launch { run(payload.toString()) }
    }

    fun notifyCall(callId: String) {
        val payload = JSONObject()
            .put("type", "call")
            .put("callId", callId)
        scope.launch { run(payload.toString()) }
    }

    private suspend fun run(body: String) {
        for (wait in RETRY_DELAYS) {
            if (wait > 0) delay(wait)
            try {
                if (attempt(body)) return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
        }
    }

    private suspend fun attempt(body: String): Boolean {
        if (authRepository.user == null) return true
        val token = authRepository.idToken(false)
        return withContext(Dispatchers.IO) {
            val connection = URL(BuildConfig.API_BASE + "/api/notify").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                code in 200..299 || (code in 400..499 && code != 408 && code != 429)
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        val RETRY_DELAYS = listOf(0L, 1_500L, 5_000L)
        const val TIMEOUT_MS = 8_000
    }
}
