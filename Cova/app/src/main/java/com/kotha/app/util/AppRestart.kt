package com.kotha.app.util

import android.content.Context
import android.content.Intent

object AppRestart {

    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }
}
