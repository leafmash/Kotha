package com.kotha.app.notify

import android.content.Context
import com.kotha.app.data.app.AppForeground
import com.kotha.app.data.push.PushActionsRepository
import com.kotha.app.data.push.PushTokenRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotifyEntryPoint {
    fun pushActions(): PushActionsRepository
    fun pushTokens(): PushTokenRepository
    fun appForeground(): AppForeground
}

fun Context.notifyEntryPoint(): NotifyEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, NotifyEntryPoint::class.java)
