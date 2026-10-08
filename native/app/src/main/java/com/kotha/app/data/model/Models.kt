package com.kotha.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class UserProfile(
    val uid: String,
    val name: String,
    val photo: String,
    val online: Boolean,
    val lastSeenMs: Long,
    val lang: String
)

data class Chat(
    val id: String,
    val members: List<String>,
    val group: Boolean,
    val name: String,
    val photo: String,
    val description: String,
    val admin: String,
    val admins: List<String>,
    val adminOnly: Boolean,
    val lastMessage: String,
    val lastFrom: String,
    val lastAtMs: Long,
    val typing: Map<String, Boolean>,
    val unread: Map<String, Long>,
    val readAt: Map<String, Long>
) {
    fun peerId(uid: String): String = members.firstOrNull { it != uid }.orEmpty()

    fun isAdmin(uid: String): Boolean = admin == uid || admins.contains(uid)
}

data class CallLog(val kind: String, val video: Boolean, val secs: Long)

data class SysEvent(
    val kind: String,
    val by: String,
    val names: List<String>,
    val target: List<String>,
    val name: String
)

enum class UploadStage { Queued, Uploading, Failed }

data class UploadUi(
    val stage: UploadStage,
    val progress: Float,
    val localPath: String,
    val thumbPath: String
)

data class Message(
    val id: String,
    val from: String,
    val type: String,
    val text: String,
    val atMs: Long,
    val pending: Boolean,
    val status: String,
    val replyText: String?,
    val replyId: String?,
    val reactions: Map<String, String>,
    val hiddenFor: List<String>,
    val deleted: Boolean,
    val edited: Boolean,
    val forwarded: Boolean,
    val url: String,
    val name: String,
    val size: Long,
    val duration: Double,
    val wave: List<Float>,
    val callLog: CallLog?,
    val sys: SysEvent?,
    val thumb: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val upload: UploadUi? = null
) {
    val isText: Boolean get() = type == "text"

    val isMedia: Boolean get() = type == "image" || type == "video" || type == "audio" || type == "file"
}

data class ChatPrefs(
    val muted: Map<String, Long> = emptyMap(),
    val pinned: Map<String, Long> = emptyMap(),
    val archived: Map<String, Long> = emptyMap()
)

private fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

private fun DocumentSnapshot.numberMap(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)?.entries
        ?.mapNotNull { entry ->
            val key = entry.key as? String
            val value = (entry.value as? Number)?.toLong()
            if (key != null && value != null) key to value else null
        }
        ?.toMap()
        .orEmpty()

private fun DocumentSnapshot.timeMap(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)?.entries
        ?.mapNotNull { entry ->
            val key = entry.key as? String
            val value = (entry.value as? Timestamp)?.toDate()?.time
            if (key != null && value != null) key to value else null
        }
        ?.toMap()
        .orEmpty()

private fun DocumentSnapshot.boolMap(field: String): Map<String, Boolean> =
    (get(field) as? Map<*, *>)?.entries
        ?.mapNotNull { entry ->
            val key = entry.key as? String
            if (key != null) key to (entry.value == true) else null
        }
        ?.toMap()
        .orEmpty()

private fun DocumentSnapshot.stringMap(field: String): Map<String, String> =
    (get(field) as? Map<*, *>)?.entries
        ?.mapNotNull { entry ->
            val key = entry.key as? String
            val value = entry.value as? String
            if (key != null && value != null) key to value else null
        }
        ?.toMap()
        .orEmpty()

fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    uid = id,
    name = getString("name").orEmpty(),
    photo = getString("photo").orEmpty(),
    online = getBoolean("online") == true,
    lastSeenMs = getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
    lang = getString("lang").orEmpty()
)

fun DocumentSnapshot.toChat(): Chat = Chat(
    id = id,
    members = stringList("members"),
    group = getBoolean("group") == true,
    name = getString("name").orEmpty(),
    photo = getString("photo").orEmpty(),
    description = getString("description").orEmpty(),
    admin = getString("admin").orEmpty(),
    admins = stringList("admins"),
    adminOnly = getBoolean("adminOnly") == true,
    lastMessage = getString("lastMessage").orEmpty(),
    lastFrom = getString("lastFrom").orEmpty(),
    lastAtMs = getTimestamp("lastAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
    typing = boolMap("typing"),
    unread = numberMap("unread"),
    readAt = timeMap("readAt")
)

fun DocumentSnapshot.toChatPrefs(): ChatPrefs = ChatPrefs(
    muted = numberMap("muted"),
    pinned = numberMap("pinned"),
    archived = numberMap("archived")
)

fun DocumentSnapshot.toMessage(): Message {
    val reply = get("replyTo") as? Map<*, *>
    val call = get("callLog") as? Map<*, *>
    val sysMap = get("sys") as? Map<*, *>
    val rawAt = getTimestamp("at")
    return Message(
        id = id,
        from = getString("from").orEmpty(),
        type = getString("type") ?: "text",
        text = getString("text").orEmpty(),
        atMs = getTimestamp("at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        pending = metadata.hasPendingWrites() && rawAt == null,
        status = getString("status") ?: "sent",
        replyText = reply?.get("text") as? String,
        replyId = reply?.get("id") as? String,
        reactions = stringMap("reactions"),
        hiddenFor = stringList("hiddenFor"),
        deleted = getBoolean("deleted") == true,
        edited = getBoolean("edited") == true,
        forwarded = getBoolean("forwarded") == true,
        url = getString("url").orEmpty(),
        name = getString("name").orEmpty(),
        size = getLong("size") ?: 0L,
        duration = getDouble("duration") ?: 0.0,
        wave = (get("wave") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }.orEmpty(),
        callLog = call?.let {
            CallLog(
                kind = it["kind"] as? String ?: "",
                video = it["video"] == true,
                secs = (it["secs"] as? Number)?.toLong() ?: 0L
            )
        },
        sys = sysMap?.let {
            SysEvent(
                kind = it["kind"] as? String ?: "",
                by = it["by"] as? String ?: "",
                names = (it["names"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                target = (it["target"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                name = it["name"] as? String ?: ""
            )
        },
        thumb = getString("thumb").orEmpty(),
        width = (get("w") as? Number)?.toInt() ?: 0,
        height = (get("h") as? Number)?.toInt() ?: 0
    )
}
