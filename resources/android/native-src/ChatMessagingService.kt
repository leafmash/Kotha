package com.kotha.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import PLUGIN_MESSAGING_SERVICE_IMPORT

class ChatMessagingService : MessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isEmpty() || MainActivity.foreground) return
        val context = applicationContext
        when {
            data["type"] == "call" -> showCall(context, data)
            !data["chatId"].isNullOrBlank() -> serviceScope.launch { handleMessage(context, data) }
        }
    }

    private suspend fun handleMessage(context: Context, data: Map<String, String>) {
        data["badge"]?.toIntOrNull()?.let { BadgeHelper.apply(context, it) }
        reportDelivered(context, data)
        if (data["muted"] == "1") return
        showMessage(context, data)
    }

    private fun reportDelivered(context: Context, data: Map<String, String>) {
        val chatId = data["chatId"] ?: return
        val messageId = data["messageId"]?.takeIf { it.isNotBlank() } ?: return
        val session = SessionStore.read(context) ?: return
        if (data["senderUid"] == session.uid) return
        for (attempt in 0 until DELIVERY_ATTEMPTS) {
            if (attempt > 0) Thread.sleep(DELIVERY_RETRY_MS * attempt)
            val outcome = FirestoreRest.markDelivered(context, session, chatId, messageId)
            if (outcome != FirestoreRest.Outcome.RETRY) return
        }
    }

    private suspend fun showMessage(context: Context, data: Map<String, String>) {
        val chatId = data["chatId"] ?: return
        val group = data["group"] == "1"
        val senderName = data["senderName"]?.takeIf { it.isNotBlank() } ?: (data["title"] ?: "")
        val title = if (group) (data["chatName"]?.takeIf { it.isNotBlank() } ?: data["title"] ?: "") else senderName
        val text = data["text"] ?: data["body"] ?: ""

        ChatConversationStore.addMessage(
            context,
            chatId,
            text,
            fromMe = false,
            senderName = senderName,
            timestamp = System.currentTimeMillis()
        )
        val photoUrl = data["senderPhoto"] ?: ""
        if (photoUrl.startsWith("http")) ChatConversationStore.setSenderPhotoUrl(context, chatId, photoUrl)
        ChatConversationStore.setConversationTitle(context, chatId, title)

        ChatConversationStore.incrementUnread(context, chatId)
        buildAndShowNotification(context, chatId, data["senderUid"] ?: chatId, title, group)
    }

    private fun showCall(context: Context, data: Map<String, String>) {
        val chatId = data["chatId"] ?: ""
        val notificationId = ("call:$chatId").hashCode()
        ensureCallChannel(context)
        val open = buildOpenPendingIntent(context, notificationId, chatId, true)

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(resolveIcon(context))
            .setContentTitle(data["title"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name))
            .setContentText(data["body"] ?: "")
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

    companion object {
        const val MESSAGE_CHANNEL_ID = "kotha_chat_channel_v2"
        const val CALL_CHANNEL_ID = "kotha_call_channel_v2"
        private const val CALL_TIMEOUT_MS = 45000L
        private const val DELIVERY_ATTEMPTS = 3
        private const val DELIVERY_RETRY_MS = 1500L

        suspend fun buildAndShowNotification(context: Context, chatId: String, otherUid: String, title: String, group: Boolean) {
            val notificationId = chatId.hashCode()
            ensureMessageChannel(context)

            val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(resolveIcon(context))
                .setStyle(buildMessagingStyle(context, chatId, title, otherUid, group))
                .setAutoCancel(true)
                .setContentIntent(buildOpenPendingIntent(context, notificationId, chatId, false))
                .setDeleteIntent(buildDeletePendingIntent(context, notificationId, chatId))
                .addAction(buildReplyAction(context, chatId, notificationId))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setNumber(ChatConversationStore.getUnreadCount(context, chatId))
                .setShortcutId(chatId)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }

        fun ensureMessageChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(MESSAGE_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                context.getString(R.string.channel_messages),
                NotificationManager.IMPORTANCE_HIGH
            )
            val soundUri = Uri.parse("android.resource://${context.packageName}/raw/kotha_message")
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            channel.setSound(soundUri, attributes)
            manager.createNotificationChannel(channel)
        }

        fun ensureCallChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CALL_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CALL_CHANNEL_ID,
                context.getString(R.string.channel_calls),
                NotificationManager.IMPORTANCE_HIGH
            )
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val customRes = context.resources.getIdentifier("kotha_call", "raw", context.packageName)
            val soundUri = if (customRes != 0) {
                Uri.parse("android.resource://${context.packageName}/raw/kotha_call")
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            channel.setSound(soundUri, attributes)
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            channel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            manager.createNotificationChannel(channel)
        }

        fun resolveIcon(context: Context): Int {
            val iconRes = context.resources.getIdentifier("ic_stat_notify", "drawable", context.packageName)
            return if (iconRes != 0) iconRes else android.R.drawable.ic_dialog_email
        }

        fun blankIcon(): IconCompat {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            return IconCompat.createWithBitmap(bitmap)
        }

        fun buildReplyAction(context: Context, chatId: String, notificationId: Int): NotificationCompat.Action {
            val remoteInput = RemoteInput.Builder(ChatReplyReceiver.KEY_REPLY_TEXT)
                .setLabel(context.getString(R.string.reply_label))
                .build()

            val replyIntent = Intent(context, ChatReplyReceiver::class.java).apply {
                putExtra(ChatReplyReceiver.EXTRA_CHAT_ID, chatId)
                putExtra(ChatReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                replyIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                context.getString(R.string.reply_label),
                replyPendingIntent
            ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()
        }

        fun buildOpenPendingIntent(context: Context, notificationId: Int, chatId: String, call: Boolean): PendingIntent {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
                putExtra(MainActivity.EXTRA_CALL, call)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun buildDeletePendingIntent(context: Context, notificationId: Int, chatId: String): PendingIntent {
            val deleteIntent = Intent(context, ChatDismissReceiver::class.java).apply {
                putExtra(ChatDismissReceiver.EXTRA_CHAT_ID, chatId)
            }
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        suspend fun buildMessagingStyle(
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

            val messages = ChatConversationStore.getMessages(context, chatId)
            val lastSender = messages.lastOrNull { !it.fromMe }?.senderName ?: title
            val icon = AvatarLoader.load(ChatConversationStore.getSenderPhotoUrl(context, chatId))

            val people = mutableMapOf<String, Person>()
            fun personFor(name: String): Person = people.getOrPut(name) {
                val builder = Person.Builder().setName(name.ifBlank { title }).setKey(if (group) "kotha_$name" else otherUid).setImportant(true)
                if (icon != null && name == lastSender) builder.setIcon(icon)
                builder.build()
            }

            val shortcutPerson = personFor(lastSender)
            ensureConversationShortcut(context, chatId, title, icon, shortcutPerson)

            val unreadCount = ChatConversationStore.getUnreadCount(context, chatId)
            val historicCount = (messages.size - unreadCount).coerceAtLeast(0)
            messages.forEachIndexed { index, message ->
                val person = if (message.fromMe) null else personFor(message.senderName)
                if (index < historicCount) {
                    style.addHistoricMessage(NotificationCompat.MessagingStyle.Message(message.text, message.timestamp, person))
                } else {
                    style.addMessage(message.text, message.timestamp, person)
                }
            }
            return style
        }

        fun ensureConversationShortcut(context: Context, chatId: String, title: String, icon: IconCompat?, person: Person) {
            try {
                val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
                }
                val builder = ShortcutInfoCompat.Builder(context, chatId)
                    .setShortLabel(title.ifBlank { context.getString(R.string.app_name) })
                    .setLongLived(true)
                    .setPerson(person)
                    .setIntent(shortcutIntent)
                    .setCategories(setOf("android.shortcut.conversation"))
                icon?.let { builder.setIcon(it) }
                ShortcutManagerCompat.pushDynamicShortcut(context, builder.build())
            } catch (e: Exception) {
                return
            }
        }
    }
}
