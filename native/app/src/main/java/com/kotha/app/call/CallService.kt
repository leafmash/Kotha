package com.kotha.app.call

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.kotha.app.data.call.CallPhase
import com.kotha.app.data.call.CallState
import com.kotha.app.notify.AvatarLoader
import com.kotha.app.notify.NotificationIds
import com.kotha.app.notify.notifyEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var foregroundType = -1
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val entry = applicationContext.notifyEntryPoint()
        val manager = entry.callManager()
        val initial = manager.state.value
        val placeholder = if (initial != null && initial.phase == CallPhase.Incoming) {
            CallNotifications.buildIncoming(this, initial, "", null, false)
        } else {
            CallNotifications.buildPlaceholder(this)
        }
        enterForeground(placeholder, initial)
        if (watcher == null) {
            watcher = scope.launch {
                manager.state.collectLatest { state -> render(state) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun render(state: CallState?) {
        if (state == null || state.phase == CallPhase.Ended) {
            stopSelfClean()
            return
        }
        val users = applicationContext.notifyEntryPoint().userRepository()
        val profile = users.users.value[state.peerUid]
        val name = profile?.name.orEmpty()
        val icon = profile?.photo?.let { AvatarLoader.load(it) }
        val notification = if (state.phase == CallPhase.Incoming) {
            CallNotifications.buildIncoming(this, state, name, icon, false)
        } else {
            CallNotifications.buildOngoing(this, state, name, icon)
        }
        enterForeground(notification, state)
    }

    private fun typeFor(state: CallState?): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (state != null && state.phase != CallPhase.Incoming && state.phase != CallPhase.Ended) {
            val missing = CallPermissions.missing(this, state.video)
            if (!missing.contains(android.Manifest.permission.RECORD_AUDIO)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (state.video && !missing.contains(android.Manifest.permission.CAMERA)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return type
    }

    private fun enterForeground(notification: Notification, state: CallState?) {
        val id = NotificationIds.CALL_ONGOING_ID
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!started) startForeground(id, notification) else notifyOnly(id, notification)
            started = true
            return
        }
        val type = typeFor(state)
        if (started && type == foregroundType) {
            notifyOnly(id, notification)
            return
        }
        try {
            startForeground(id, notification, type)
            foregroundType = type
            started = true
        } catch (e: Exception) {
            try {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                started = true
            } catch (inner: Exception) {
                stopSelf()
            }
        }
    }

    private fun notifyOnly(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (e: SecurityException) {
            return
        }
    }

    private fun stopSelfClean() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
