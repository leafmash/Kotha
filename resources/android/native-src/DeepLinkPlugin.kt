package com.kotha.app

import androidx.core.app.NotificationManagerCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "KothaDeepLink")
class DeepLinkPlugin : Plugin() {

    @PluginMethod
    fun getPending(call: PluginCall) {
        val result = JSObject()
        result.put("chatId", MainActivity.consumePendingChat())
        call.resolve(result)
    }

    @PluginMethod
    fun clearChatNotification(call: PluginCall) {
        val chatId = call.getString("chatId")
        if (chatId == null) {
            call.reject("chatId is required")
            return
        }
        ChatConversationStore.clear(context, chatId)
        NotificationManagerCompat.from(context).cancel(chatId.hashCode())
        call.resolve()
    }

    @PluginMethod
    fun setBadge(call: PluginCall) {
        BadgeHelper.apply(context, call.getInt("count") ?: 0)
        call.resolve()
    }

    @PluginMethod
    fun setActiveChat(call: PluginCall) {
        val chatId = call.getString("chatId")
        if (chatId == null) {
            call.reject("chatId is required")
            return
        }
        MainActivity.setActiveChat(chatId)
        call.resolve()
    }

    @PluginMethod
    fun clearActiveChat(call: PluginCall) {
        MainActivity.setActiveChat(null)
        call.resolve()
    }
}
