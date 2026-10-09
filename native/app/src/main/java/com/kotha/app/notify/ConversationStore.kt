package com.kotha.app.notify

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredMessage(
    val text: String,
    val fromMe: Boolean,
    val senderName: String,
    val timestamp: Long
)

object ConversationStore {

    private const val PREFS_NAME = "kotha_conversations"
    private const val MAX_MESSAGES = 30
    private const val MAX_IDS = 12

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addMessage(
        context: Context,
        chatId: String,
        text: String,
        fromMe: Boolean,
        senderName: String,
        timestamp: Long
    ) {
        val store = prefs(context)
        val array = readArray(store.getString(chatId, null))
        array.put(
            JSONObject()
                .put("text", text)
                .put("fromMe", fromMe)
                .put("senderName", senderName)
                .put("timestamp", timestamp)
        )
        val editor = store.edit().putString(chatId, tail(array, MAX_MESSAGES).toString())
        if (!fromMe) editor.putLong("$chatId:posted", timestamp)
        editor.apply()
    }

    @Synchronized
    fun getMessages(context: Context, chatId: String): List<StoredMessage> {
        val array = readArray(prefs(context).getString(chatId, null))
        val messages = ArrayList<StoredMessage>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            messages.add(
                StoredMessage(
                    text = item.optString("text"),
                    fromMe = item.optBoolean("fromMe"),
                    senderName = item.optString("senderName"),
                    timestamp = item.optLong("timestamp")
                )
            )
        }
        return messages
    }

    @Synchronized
    fun registerMessage(context: Context, chatId: String, messageId: String): Boolean {
        val store = prefs(context)
        val ids = readArray(store.getString("$chatId:ids", null))
        for (index in 0 until ids.length()) {
            if (ids.optString(index) == messageId) return false
        }
        ids.put(messageId)
        store.edit().putString("$chatId:ids", tail(ids, MAX_IDS).toString()).apply()
        return true
    }

    fun lastPostedAt(context: Context, chatId: String): Long = prefs(context).getLong("$chatId:posted", 0L)

    fun setSenderPhotoUrl(context: Context, chatId: String, url: String) {
        prefs(context).edit().putString("$chatId:photo", url).apply()
    }

    fun getSenderPhotoUrl(context: Context, chatId: String): String =
        prefs(context).getString("$chatId:photo", "").orEmpty()

    fun setConversationTitle(context: Context, chatId: String, title: String) {
        prefs(context).edit().putString("$chatId:title", title).apply()
    }

    @Synchronized
    fun incrementUnread(context: Context, chatId: String): Int {
        val store = prefs(context)
        val next = store.getInt("$chatId:unread", 0) + 1
        store.edit().putInt("$chatId:unread", next).apply()
        return next
    }

    fun getUnreadCount(context: Context, chatId: String): Int = prefs(context).getInt("$chatId:unread", 0)

    fun resetUnread(context: Context, chatId: String) {
        prefs(context).edit().remove("$chatId:unread").apply()
    }

    @Synchronized
    fun clear(context: Context, chatId: String) {
        prefs(context).edit()
            .remove(chatId)
            .remove("$chatId:photo")
            .remove("$chatId:title")
            .remove("$chatId:unread")
            .remove("$chatId:posted")
            .apply()
    }

    @Synchronized
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun readArray(raw: String?): JSONArray = try {
        JSONArray(raw ?: "[]")
    } catch (e: org.json.JSONException) {
        JSONArray()
    }

    private fun tail(source: JSONArray, max: Int): JSONArray {
        val start = maxOf(0, source.length() - max)
        val trimmed = JSONArray()
        for (index in start until source.length()) trimmed.put(source.get(index))
        return trimmed
    }
}
