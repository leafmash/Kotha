package com.kotha.app.data.call

import android.content.Context
import com.kotha.app.R
import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.util.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class CallLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    fun log(
        chatId: String,
        members: List<String>,
        callId: String,
        video: Boolean,
        connected: Boolean,
        reason: String,
        talkedSecs: Long
    ) {
        val kind = when {
            connected -> "done"
            reason == "declined" -> "declined"
            reason == "ended" -> "cancelled"
            else -> "missed"
        }
        val secs = if (kind == "done") talkedSecs.coerceIn(0L, CallRules.MAX_SECS) else 0L
        val payload = mapOf(
            "type" to "call",
            "text" to "",
            "callLog" to mapOf("kind" to kind, "video" to video, "secs" to secs, "callId" to callId)
        )
        val preview = previewOf(kind, video, secs)
        scope.launch {
            try {
                chatRepository.send(chatId, members, payload, null, null, preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
        }
    }

    private fun previewOf(kind: String, video: Boolean, secs: Long): String {
        val localized = AppLanguage.localized(context)
        val icon = if (video) "🎥" else "📞"
        val label = localized.getString(if (video) R.string.call_video else R.string.call_voice)
        val text = when (kind) {
            "done" -> "$label · ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
            "declined" -> localized.getString(R.string.call_log_declined, label)
            "cancelled" -> localized.getString(R.string.call_log_cancelled, label)
            else -> localized.getString(R.string.call_log_missed, label)
        }
        return "$icon $text"
    }
}
