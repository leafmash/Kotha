package com.kotha.app.notify

object NotificationIds {
    const val MESSAGE_CHANNEL = "kotha_chat_channel_v2"
    const val CALL_CHANNEL = "kotha_call_channel_v2"

    fun chat(chatId: String): Int = chatId.hashCode()

    fun call(chatId: String): Int = "call:$chatId".hashCode()
}
