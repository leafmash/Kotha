package com.kotha.app.data.net

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class OutboxEntry(val uid: String, val chatId: String, val messageId: String)

@Singleton
class PushOutbox @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("push_outbox", Context.MODE_PRIVATE)
    private val inflight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun markInflight(messageId: String) {
        inflight.add(messageId)
    }

    fun clearInflight(messageId: String) {
        inflight.remove(messageId)
    }

    @Synchronized
    fun add(uid: String, chatId: String, messageId: String) {
        write((read() + "$uid|$chatId|$messageId").toList().takeLast(MAX_ENTRIES).toSet())
    }

    @Synchronized
    fun drop(messageId: String) {
        write(read().filterNot { it.endsWith("|$messageId") }.toSet())
    }

    @Synchronized
    fun takePending(uid: String): List<OutboxEntry> {
        val all = read().mapNotNull { parse(it) }
        val mine = all.filter { it.uid == uid && it.messageId !in inflight }
        write(all.filter { it.uid == uid && it.messageId in inflight }.map { "${it.uid}|${it.chatId}|${it.messageId}" }.toSet())
        return mine
    }

    private fun read(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    private fun write(entries: Set<String>) {
        prefs.edit().putStringSet(KEY, entries).apply()
    }

    private fun parse(raw: String): OutboxEntry? {
        val parts = raw.split("|")
        return if (parts.size == 3) OutboxEntry(parts[0], parts[1], parts[2]) else null
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 200
    }
}
