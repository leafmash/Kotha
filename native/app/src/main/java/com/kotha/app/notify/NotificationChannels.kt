package com.kotha.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.kotha.app.R

object NotificationChannels {

    private val CALL_VIBRATION = longArrayOf(0, 400, 200, 400, 200, 400)

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureMessages(context, manager)
        ensureCalls(context, manager)
        ensureOngoing(context, manager)
    }

    private fun ensureMessages(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(NotificationIds.MESSAGE_CHANNEL) != null) return
        val channel = NotificationChannel(
            NotificationIds.MESSAGE_CHANNEL,
            context.getString(R.string.channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        )
        val sound = Uri.parse("android.resource://${context.packageName}/raw/kotha_message")
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        channel.setSound(sound, attributes)
        manager.createNotificationChannel(channel)
    }

    private fun ensureCalls(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(NotificationIds.CALL_CHANNEL) != null) return
        val channel = NotificationChannel(
            NotificationIds.CALL_CHANNEL,
            context.getString(R.string.channel_calls),
            NotificationManager.IMPORTANCE_HIGH
        )
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), attributes)
        channel.enableVibration(true)
        channel.vibrationPattern = CALL_VIBRATION
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }

    private fun ensureOngoing(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(NotificationIds.CALL_ONGOING_CHANNEL) != null) return
        val channel = NotificationChannel(
            NotificationIds.CALL_ONGOING_CHANNEL,
            context.getString(R.string.channel_calls_ongoing),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }
}
