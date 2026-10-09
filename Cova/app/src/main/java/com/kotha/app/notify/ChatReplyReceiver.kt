package com.kotha.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import java.util.UUID

class ChatReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (replyText.isNullOrEmpty() || chatId == null) return

        val appContext = context.applicationContext
        ConversationStore.addMessage(
            appContext,
            chatId,
            replyText,
            fromMe = true,
            senderName = "",
            timestamp = System.currentTimeMillis()
        )
        ConversationStore.resetUnread(appContext, chatId)
        NotificationManagerCompat.from(appContext).cancel(notificationId)
        ChatReplyWorker.enqueue(appContext, chatId, UUID.randomUUID().toString().replace("-", "").take(20), replyText)
    }

    companion object {
        const val KEY_REPLY_TEXT = "kotha_reply_text"
        const val EXTRA_CHAT_ID = "kotha_reply_chat_id"
        const val EXTRA_NOTIFICATION_ID = "kotha_reply_notification_id"
    }
}
