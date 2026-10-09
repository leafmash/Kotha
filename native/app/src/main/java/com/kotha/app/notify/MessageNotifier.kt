package com.kotha.app.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kotha.app.R

object MessageNotifier {

    private const val CALL_TIMEOUT_MS = 45_000L
    private const val OPEN_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP
    private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private const val MUTABLE_UPDATE = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    suspend fun showMessage(context: Context, data: Map<String, String>) {
        val chatId = data["chatId"]?.takeIf { it.isNotBlank() } ?: return
        val messageId = data["messageId"].orEmpty()
        if (messageId.isNotEmpty() && !ConversationStore.registerMessage(context, chatId, messageId)) return
        val group = data["group"] == "1"
        val senderName = data["senderName"]?.takeIf { it.isNotBlank() } ?: data["title"].orEmpty()
        val title = if (group) {
            data["chatName"]?.takeIf { it.isNotBlank() } ?: data["title"].orEmpty()
        } else {
            senderName
        }
        val text = data["text"] ?: data["body"].orEmpty()

        ConversationStore.addMessage(
            context,
            chatId,
            text,
            fromMe = false,
            senderName = senderName,
            timestamp = System.currentTimeMillis()
        )
        val photoUrl = data["senderPhoto"].orEmpty()
        if (photoUrl.startsWith("http")) ConversationStore.setSenderPhotoUrl(context, chatId, photoUrl)
        ConversationStore.setConversationTitle(context, chatId, title)
        ConversationStore.incrementUnread(context, chatId)
        post(context, chatId, data["senderUid"] ?: chatId, title, group)
    }

    fun showCall(context: Context, data: Map<String, String>) {
        val chatId = data["chatId"].orEmpty()
        val notificationId = NotificationIds.call(chatId)
        NotificationChannels.ensure(context)
        val open = openIntent(context, notificationId, chatId, true)
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)

        val notification = NotificationCompat.Builder(context, NotificationIds.CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setContentTitle(title)
            .setContentText(data["body"].orEmpty())
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .setTimeoutAfter(CALL_TIMEOUT_MS)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun clearChat(context: Context, chatId: String, includeCall: Boolean) {
        ConversationStore.clear(context, chatId)
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(NotificationIds.chat(chatId))
        if (includeCall) manager.cancel(NotificationIds.call(chatId))
    }

    fun reset(context: Context) {
        BadgeHelper.apply(context, 0)
        ConversationStore.clearAll(context)
        NotificationManagerCompat.from(context).cancelAll()
    }

    private suspend fun post(context: Context, chatId: String, otherUid: String, title: String, group: Boolean) {
        val notificationId = NotificationIds.chat(chatId)
        NotificationChannels.ensure(context)
        val unread = ConversationStore.getUnreadCount(context, chatId)
        val notification = NotificationCompat.Builder(context, NotificationIds.MESSAGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setStyle(messagingStyle(context, chatId, title, otherUid, group))
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, notificationId, chatId, false))
            .setDeleteIntent(dismissIntent(context, notificationId, chatId))
            .addAction(replyAction(context, chatId, notificationId))
            .addAction(markReadAction(context, chatId, notificationId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(unread)
            .setShortcutId(chatId)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun blankIcon(): IconCompat {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun replyAction(context: Context, chatId: String, notificationId: Int): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(ChatReplyReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.reply_label))
            .build()
        val intent = Intent(context, ChatReplyReceiver::class.java)
            .putExtra(ChatReplyReceiver.EXTRA_CHAT_ID, chatId)
            .putExtra(ChatReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        val pending = PendingIntent.getBroadcast(context, notificationId, intent, MUTABLE_UPDATE)
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            context.getString(R.string.reply_label),
            pending
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun markReadAction(context: Context, chatId: String, notificationId: Int): NotificationCompat.Action {
        val intent = Intent(context, MarkReadReceiver::class.java)
            .putExtra(MarkReadReceiver.EXTRA_CHAT_ID, chatId)
        val pending = PendingIntent.getBroadcast(context, notificationId, intent, IMMUTABLE_UPDATE)
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            context.getString(R.string.mark_read_label),
            pending
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun openIntent(context: Context, notificationId: Int, chatId: String, call: Boolean): PendingIntent {
        val intent = DeepLink.intent(context, chatId, call).apply { flags = OPEN_FLAGS }
        return PendingIntent.getActivity(context, notificationId, intent, IMMUTABLE_UPDATE)
    }

    private fun dismissIntent(context: Context, notificationId: Int, chatId: String): PendingIntent {
        val intent = Intent(context, ChatDismissReceiver::class.java)
            .putExtra(ChatDismissReceiver.EXTRA_CHAT_ID, chatId)
        return PendingIntent.getBroadcast(context, notificationId, intent, IMMUTABLE_UPDATE)
    }

    private suspend fun messagingStyle(
        context: Context,
        chatId: String,
        title: String,
        otherUid: String,
        group: Boolean
    ): NotificationCompat.MessagingStyle {
        val me = Person.Builder()
            .setName(context.getString(R.string.you_label))
            .setKey("kotha_me")
            .setIcon(blankIcon())
            .build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(title)
            .setGroupConversation(group)

        val messages = ConversationStore.getMessages(context, chatId)
        val lastSender = messages.lastOrNull { !it.fromMe }?.senderName ?: title
        val icon = AvatarLoader.load(ConversationStore.getSenderPhotoUrl(context, chatId))

        val people = HashMap<String, Person>()
        fun personFor(name: String): Person = people.getOrPut(name) {
            val builder = Person.Builder()
                .setName(name.ifBlank { title })
                .setKey(if (group) "kotha_$name" else otherUid)
                .setImportant(true)
            if (icon != null && name == lastSender) builder.setIcon(icon)
            builder.build()
        }

        ensureShortcut(context, chatId, title, icon, personFor(lastSender))

        val unread = ConversationStore.getUnreadCount(context, chatId)
        val historic = (messages.size - unread).coerceAtLeast(0)
        messages.forEachIndexed { index, message ->
            val person = if (message.fromMe) null else personFor(message.senderName)
            val entry = NotificationCompat.MessagingStyle.Message(message.text, message.timestamp, person)
            if (index < historic) style.addHistoricMessage(entry) else style.addMessage(entry)
        }
        return style
    }

    private fun ensureShortcut(context: Context, chatId: String, title: String, icon: IconCompat?, person: Person) {
        try {
            val intent = DeepLink.intent(context, chatId, false).apply { action = Intent.ACTION_VIEW }
            val builder = ShortcutInfoCompat.Builder(context, chatId)
                .setShortLabel(title.ifBlank { context.getString(R.string.app_name) })
                .setLongLived(true)
                .setPerson(person)
                .setIntent(intent)
                .setCategories(setOf("android.shortcut.conversation"))
            if (icon != null) builder.setIcon(icon)
            ShortcutManagerCompat.pushDynamicShortcut(context, builder.build())
        } catch (e: Exception) {
            return
        }
    }
}
