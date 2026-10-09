package com.kotha.app.data.chat

import com.kotha.app.core.AppConfig
import com.kotha.app.data.model.Message

object MessageRules {

    private fun plain(message: Message): Boolean =
        !message.deleted && message.callLog == null && message.sys == null &&
            message.type != "system" && message.type != "call" && message.upload == null

    fun canForward(message: Message): Boolean =
        plain(message) && (if (message.isText) message.text.isNotEmpty() else message.url.isNotEmpty())

    fun editLeftMs(message: Message, now: Long): Long =
        if (message.atMs > 0) AppConfig.EDIT_WINDOW_MS - (now - message.atMs) else 0L

    fun canEdit(message: Message, now: Long): Boolean =
        plain(message) && message.isText && !message.pending &&
            message.text.length <= AppConfig.EDIT_MAX && editLeftMs(message, now) > 0

    fun forwardPayload(message: Message): Map<String, Any> {
        if (message.isText) return mapOf("type" to "text", "text" to message.text, "forwarded" to true)
        return buildMap<String, Any> {
            put("type", message.type)
            put("url", message.url)
            put("forwarded", true)
            if (message.name.isNotEmpty()) put("name", message.name)
            if (message.size > 0) put("size", message.size)
            if (message.thumb.isNotEmpty()) put("thumb", message.thumb)
            if (message.width > 0 && message.height > 0) {
                put("w", message.width.toLong())
                put("h", message.height.toLong())
            }
            if (message.duration > 0) put("duration", message.duration)
            if (message.wave.isNotEmpty()) put("wave", message.wave.map { it.toLong() })
        }
    }
}
