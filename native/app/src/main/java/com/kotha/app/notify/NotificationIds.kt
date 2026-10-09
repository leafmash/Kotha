package com.kotha.app.notify

object NotificationIds {
    const val MESSAGE_CHANNEL = "kotha_chat_channel_v2"
    const val CALL_CHANNEL = "kotha_call_channel_v2"
    const val CALL_ONGOING_CHANNEL = "kotha_call_ongoing_v1"
    const val CALL_RING_ID = 7101
    const val CALL_ONGOING_ID = 7102
    const val CALL_MISSED_BASE = 7200

    fun chat(chatId: String): Int = chatId.hashCode()

    fun call(chatId: String): Int = "call:$chatId".hashCode()
}
