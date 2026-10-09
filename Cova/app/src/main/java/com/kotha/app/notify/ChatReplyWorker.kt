package com.kotha.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kotha.app.data.push.PushOutcome

class ChatReplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val outcome = applicationContext.notifyEntryPoint().pushActions().sendReply(chatId, messageId, text)
        return when (outcome) {
            PushOutcome.Done -> Result.success()
            PushOutcome.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            PushOutcome.Failed -> Result.failure()
        }
    }

    companion object {
        private const val KEY_TEXT = "kotha_reply_text"
        private const val KEY_CHAT_ID = "kotha_reply_chat"
        private const val KEY_MESSAGE_ID = "kotha_reply_message"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context, chatId: String, messageId: String, text: String) {
            val input = Data.Builder()
                .putString(KEY_TEXT, text)
                .putString(KEY_CHAT_ID, chatId)
                .putString(KEY_MESSAGE_ID, messageId)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<ChatReplyWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
