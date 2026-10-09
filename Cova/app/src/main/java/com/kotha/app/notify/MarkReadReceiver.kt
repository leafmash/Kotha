package com.kotha.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MarkReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        MessageNotifier.clearChat(appContext, chatId, false)
        scope.launch {
            try {
                withTimeoutOrNull(WORK_TIMEOUT_MS) {
                    appContext.notifyEntryPoint().pushActions().markRead(chatId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "kotha_markread_chat_id"
        private const val WORK_TIMEOUT_MS = 8_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
