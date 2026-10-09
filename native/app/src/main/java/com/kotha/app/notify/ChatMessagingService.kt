package com.kotha.app.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class ChatMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        applicationContext.notifyEntryPoint().pushTokens().onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) return
        val entry = applicationContext.notifyEntryPoint()
        if (entry.appForeground().foreground.value) return
        runBlocking {
            withTimeoutOrNull(HANDLE_TIMEOUT_MS) { handle(data) }
        }
    }

    private suspend fun handle(data: Map<String, String>) {
        val context = applicationContext
        val actions = context.notifyEntryPoint().pushActions()
        val uid = actions.currentUid() ?: return
        if (data["type"] == "call") {
            MessageNotifier.showCall(context, data)
            return
        }
        val chatId = data["chatId"]?.takeIf { it.isNotBlank() } ?: return
        data["badge"]?.toIntOrNull()?.let { BadgeHelper.apply(context, it) }
        if (data["muted"] != "1") MessageNotifier.showMessage(context, data)
        val messageId = data["messageId"]?.takeIf { it.isNotBlank() } ?: return
        if (data["senderUid"] == uid) return
        actions.reportDelivered(uid, chatId, messageId)
    }

    private companion object {
        const val HANDLE_TIMEOUT_MS = 18_000L
    }
}
