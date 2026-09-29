package com.kotha.app

import androidx.core.app.NotificationManagerCompat
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "KothaSession")
class SessionPlugin : Plugin() {

    @PluginMethod
    fun setSession(call: PluginCall) {
        val apiKey = call.getString("apiKey")
        val projectId = call.getString("projectId")
        val uid = call.getString("uid")
        val refreshToken = call.getString("refreshToken")
        if (apiKey.isNullOrBlank() || projectId.isNullOrBlank() || uid.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            call.reject("apiKey, projectId, uid and refreshToken are required")
            return
        }
        SessionStore.save(context, Session(apiKey, projectId, uid, refreshToken))
        call.resolve()
    }

    @PluginMethod
    fun clearSession(call: PluginCall) {
        SessionStore.clear(context)
        ChatConversationStore.clearAll(context)
        NotificationManagerCompat.from(context).cancelAll()
        call.resolve()
    }
}
