package com.kotha.app.call

import android.content.Context
import com.kotha.app.notify.notifyEntryPoint
import kotlinx.coroutines.CancellationException

object CallPushHandler {

    suspend fun handle(context: Context, uid: String, data: Map<String, String>) {
        val entry = context.applicationContext.notifyEntryPoint()
        val manager = entry.callManager()
        val callId = data["callId"].orEmpty()
        if (callId.isNotEmpty()) {
            if (manager.restore(callId)) return
        }
        val chatId = data["chatId"].orEmpty()
        val docs = try {
            entry.callRepository().fetchRinging(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        val doc = docs
            .filter { chatId.isEmpty() || it.chatId == chatId }
            .maxByOrNull { it.createdAtMs } ?: return
        manager.restore(doc.id)
    }
}
