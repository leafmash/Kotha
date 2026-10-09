package com.kotha.app.notify

import android.content.Context

object NotificationNames {

    fun lookup(context: Context, uid: String): String =
        context.notifyEntryPoint().userRepository().users.value[uid]?.name.orEmpty()
}
