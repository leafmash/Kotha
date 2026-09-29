package com.kotha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim()
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (replyText.isNullOrEmpty() || chatId == null) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleReply(appContext, chatId, notificationId, replyText)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleReply(context: Context, chatId: String, notificationId: Int, replyText: String) {
        ChatConversationStore.addMessage(
            context,
            chatId,
            replyText,
            fromMe = true,
            senderName = "",
            timestamp = System.currentTimeMillis()
        )
        ChatConversationStore.resetUnread(context, chatId)
        NotificationManagerCompat.from(context).cancel(notificationId)
        enqueueSendWorker(context, chatId, replyText)
    }

    private fun enqueueSendWorker(context: Context, chatId: String, replyText: String) {
        val inputData = Data.Builder()
            .putString(ChatReplyWorker.KEY_TEXT, replyText)
            .putString(ChatReplyWorker.KEY_CHAT_ID, chatId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ChatReplyWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    companion object {
        const val KEY_REPLY_TEXT = "kotha_reply_text"
        const val EXTRA_CHAT_ID = "kotha_reply_chat_id"
        const val EXTRA_NOTIFICATION_ID = "kotha_reply_notification_id"
    }
}
