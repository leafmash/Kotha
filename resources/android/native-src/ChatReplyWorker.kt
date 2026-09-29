package com.kotha.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatReplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val text = inputData.getString(KEY_TEXT) ?: return@withContext Result.failure()
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return@withContext Result.failure()
        val session = SessionStore.read(applicationContext) ?: return@withContext Result.failure()

        when (FirestoreRest.sendMessage(applicationContext, session, chatId, text)) {
            FirestoreRest.Outcome.SUCCESS -> Result.success()
            FirestoreRest.Outcome.RETRY -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            FirestoreRest.Outcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val KEY_TEXT = "kotha_reply_text"
        const val KEY_CHAT_ID = "kotha_reply_chat"
        private const val MAX_ATTEMPTS = 5
    }
}
