package com.kotha.app.data.chat

import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.ChatPrefs

fun isMuted(prefs: ChatPrefs, id: String, now: Long): Boolean = (prefs.muted[id] ?: 0L) > now

fun isCleared(chat: Chat, cleared: Map<String, Long>): Boolean {
    val at = cleared[chat.id] ?: 0L
    return at > 0 && chat.lastAtMs > 0 && chat.lastAtMs <= at
}

fun unreadOf(
    chat: Chat,
    uid: String,
    activeId: String?,
    foreground: Boolean,
    blocked: Set<String>
): Int {
    if (activeId == chat.id && foreground) return 0
    if (!chat.group && blocked.contains(chat.peerId(uid))) return 0
    val count = chat.unread[uid] ?: 0L
    if (count <= 0 || chat.lastFrom == uid) return 0
    val readAt = chat.readAt[uid] ?: 0L
    if (readAt > 0 && chat.lastAtMs > 0 && readAt >= chat.lastAtMs) return 0
    return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

fun previewStored(type: String, text: String, name: String): String = when (type) {
    "image" -> "[[image]]"
    "video" -> "[[video]]"
    "audio" -> "[[audio]]"
    "file" -> name.ifEmpty { "[[file]]" }
    else -> text
}
