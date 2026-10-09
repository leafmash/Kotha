package com.kotha.app.data.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("cova_notifications", Context.MODE_PRIVATE)

    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION, value).apply()

    var batteryPrompted: Boolean
        get() = prefs.getBoolean(KEY_BATTERY, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY, value).apply()

    var autostartPrompted: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSTART, value).apply()

    private companion object {
        const val KEY_PERMISSION = "permission_asked"
        const val KEY_BATTERY = "battery_prompted"
        const val KEY_AUTOSTART = "autostart_prompted"
    }
}
