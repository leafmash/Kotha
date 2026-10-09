package com.kotha.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChatDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        val appContext = context.applicationContext
        if (ConversationStore.getUnreadCount(appContext, chatId) == 0) {
            ConversationStore.clear(appContext, chatId)
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "kotha_dismiss_chat_id"
    }
}
