package com.kotha.app

import android.content.Context

data class Session(val apiKey: String, val projectId: String, val uid: String, val refreshToken: String)

object SessionStore {

    private const val PREFS_NAME = "kotha_session"

    fun save(context: Context, session: Session) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("apiKey", session.apiKey)
            .putString("projectId", session.projectId)
            .putString("uid", session.uid)
            .putString("refreshToken", session.refreshToken)
            .apply()
    }

    fun read(context: Context): Session? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString("apiKey", null) ?: return null
        val projectId = prefs.getString("projectId", null) ?: return null
        val uid = prefs.getString("uid", null) ?: return null
        val refreshToken = prefs.getString("refreshToken", null) ?: return null
        return Session(apiKey, projectId, uid, refreshToken)
    }

    fun updateRefreshToken(context: Context, refreshToken: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("refreshToken", refreshToken)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
