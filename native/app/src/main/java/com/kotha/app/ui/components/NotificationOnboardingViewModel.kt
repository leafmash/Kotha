package com.kotha.app.ui.components

import androidx.lifecycle.ViewModel
import com.kotha.app.data.push.NotificationPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationOnboardingViewModel @Inject constructor(
    private val prefs: NotificationPrefs
) : ViewModel() {

    fun isPermissionAsked(): Boolean = prefs.permissionAsked

    fun isBatteryPrompted(): Boolean = prefs.batteryPrompted

    fun isAutostartPrompted(): Boolean = prefs.autostartPrompted

    fun permissionAsked() {
        prefs.permissionAsked = true
    }

    fun batteryPrompted() {
        prefs.batteryPrompted = true
    }

    fun autostartPrompted() {
        prefs.autostartPrompted = true
    }
}
