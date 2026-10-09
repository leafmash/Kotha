package com.kotha.app.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object CallPermissions {

    fun required(video: Boolean): List<String> {
        val list = ArrayList<String>()
        list.add(Manifest.permission.RECORD_AUDIO)
        if (video) list.add(Manifest.permission.CAMERA)
        return list
    }

    fun optional(): List<String> {
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) list.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list
    }

    fun missing(context: Context, video: Boolean): List<String> =
        required(video).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun missingOptional(context: Context): List<String> =
        optional().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
}
