package com.kotha.app.call

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.kotha.app.R
import com.kotha.app.data.call.CallPhase
import com.kotha.app.data.call.CallState
import com.kotha.app.notify.DeepLink
import com.kotha.app.notify.NotificationChannels
import com.kotha.app.notify.NotificationIds

object CallNotifications {

    private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private const val RING_TIMEOUT_MS = 50_000L

    private fun person(context: Context, name: String, icon: IconCompat?): Person {
        val builder = Person.Builder()
            .setName(name.ifBlank { context.getString(R.string.app_name) })
            .setImportant(true)
        if (icon != null) builder.setIcon(icon)
        return builder.build()
    }

    private fun screenIntent(context: Context, action: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (action != null) intent.action = action
        return PendingIntent.getActivity(context, requestCode, intent, IMMUTABLE_UPDATE)
    }

    private fun receiverIntent(context: Context, action: String, callId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java)
            .setAction(action)
            .putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        return PendingIntent.getBroadcast(context, requestCode, intent, IMMUTABLE_UPDATE)
    }

    fun buildIncoming(
        context: Context,
        state: CallState,
        name: String,
        icon: IconCompat?,
        standalone: Boolean
    ): Notification {
        NotificationChannels.ensure(context)
        val open = screenIntent(context, null, 1)
        val answer = screenIntent(context, CallActivity.ACTION_ANSWER, 2)
        val decline = receiverIntent(context, CallActionReceiver.ACTION_DECLINE, state.callId, 3)
        val text = context.getString(
            if (state.video) R.string.call_incoming_video else R.string.call_incoming_voice
        )
        val builder = NotificationCompat.Builder(context, NotificationIds.CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setContentTitle(name.ifBlank { context.getString(R.string.app_name) })
            .setContentText(text)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person(context, name, icon), decline, answer))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setTimeoutAfter(RING_TIMEOUT_MS)
        if (!standalone) builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        return notification
    }

    fun buildOngoing(context: Context, state: CallState, name: String, icon: IconCompat?): Notification {
        NotificationChannels.ensure(context)
        val open = screenIntent(context, null, 4)
        val hangUp = receiverIntent(context, CallActionReceiver.ACTION_HANGUP, state.callId, 5)
        val text = when (state.phase) {
            CallPhase.Connected -> context.getString(
                if (state.video) R.string.call_video else R.string.call_voice
            )
            CallPhase.Connecting -> context.getString(R.string.call_connecting)
            CallPhase.Ringing -> context.getString(R.string.call_ringing)
            else -> context.getString(R.string.call_calling)
        }
        val builder = NotificationCompat.Builder(context, NotificationIds.CALL_ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setContentTitle(name.ifBlank { context.getString(R.string.app_name) })
            .setContentText(text)
            .setContentIntent(open)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(context, name, icon), hangUp))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (state.phase == CallPhase.Connected && state.connectedAtElapsed > 0L) {
            val wall = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtime() - state.connectedAtElapsed)
            builder.setWhen(wall).setUsesChronometer(true).setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    fun buildPlaceholder(context: Context): Notification {
        NotificationChannels.ensure(context)
        return NotificationCompat.Builder(context, NotificationIds.CALL_ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.call_calling))
            .setContentIntent(screenIntent(context, null, 4))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showMissed(context: Context, chatId: String, peerUid: String, video: Boolean) {
        NotificationChannels.ensure(context)
        val name = peerName(context, peerUid)
        val id = NotificationIds.CALL_MISSED_BASE + (chatId.hashCode() and 0xFFF)
        val open = PendingIntent.getActivity(
            context,
            id,
            DeepLink.intent(context, chatId, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            IMMUTABLE_UPDATE
        )
        val label = context.getString(
            R.string.call_log_missed,
            context.getString(if (video) R.string.call_short_video else R.string.call_short_voice)
        )
        val notification = NotificationCompat.Builder(context, NotificationIds.MESSAGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setContentTitle(name.ifBlank { context.getString(R.string.app_name) })
            .setContentText(label)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            return
        }
    }

    private fun peerName(context: Context, uid: String): String =
        com.kotha.app.notify.NotificationNames.lookup(context, uid)

    fun cancelAll(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(NotificationIds.CALL_RING_ID)
        manager.cancel(NotificationIds.CALL_ONGOING_ID)
    }
}
