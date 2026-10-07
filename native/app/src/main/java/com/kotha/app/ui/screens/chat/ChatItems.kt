package com.kotha.app.ui.screens.chat

import com.kotha.app.data.model.Message
import com.kotha.app.util.Format

sealed interface ChatItem {
    val key: String

    data class Day(val ms: Long) : ChatItem {
        override val key: String = "day-${Format.dayStart(ms)}"
    }

    data class Bubble(
        val message: Message,
        val mine: Boolean,
        val first: Boolean,
        val quoteHidden: Boolean
    ) : ChatItem {
        override val key: String = message.id
    }

    data class System(val message: Message) : ChatItem {
        override val key: String = message.id
    }

    data class Call(val message: Message, val mine: Boolean) : ChatItem {
        override val key: String = message.id
    }

    data class HiddenNote(val anchorId: String) : ChatItem {
        override val key: String = "hidden-$anchorId"
    }
}

fun buildChatItems(
    messages: List<Message>,
    uid: String,
    group: Boolean,
    blocked: Set<String>,
    clearedAt: Long
): List<ChatItem> {
    val byId = messages.associateBy { it.id }
    val visible = messages
        .filter { !it.hiddenFor.contains(uid) && !(clearedAt > 0 && it.atMs > 0 && it.atMs <= clearedAt) }
        .asReversed()
    val out = ArrayList<ChatItem>(visible.size + 8)
    var lastDay = -1L
    var previousFrom: String? = null
    var hiddenRun = false
    for (message in visible) {
        val mine = message.from == uid
        if (message.atMs > 0) {
            val day = Format.dayStart(message.atMs)
            if (day != lastDay) {
                out.add(ChatItem.Day(message.atMs))
                lastDay = day
                previousFrom = null
            }
        }
        when {
            message.type == "system" && message.sys != null && !message.deleted -> {
                out.add(ChatItem.System(message))
                previousFrom = null
            }
            group && !mine && blocked.contains(message.from) -> {
                if (!hiddenRun) out.add(ChatItem.HiddenNote(message.id))
                hiddenRun = true
                previousFrom = null
            }
            message.callLog != null && !message.deleted -> {
                hiddenRun = false
                out.add(ChatItem.Call(message, mine))
                previousFrom = null
            }
            else -> {
                hiddenRun = false
                val quoteHidden = message.replyId?.let { byId[it]?.deleted == true } == true
                out.add(ChatItem.Bubble(message, mine, previousFrom != message.from, quoteHidden))
                previousFrom = message.from
            }
        }
    }
    return out.reversed()
}
