package com.kotha.app.notify

import android.content.Context
import me.leolin.shortcutbadger.ShortcutBadger

object BadgeHelper {

    private const val PREFS_NAME = "kotha_badge"

    fun apply(context: Context, count: Int) {
        val safe = count.coerceAtLeast(0)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("count", safe).apply()
        try {
            if (safe > 0) ShortcutBadger.applyCount(context, safe) else ShortcutBadger.removeCount(context)
        } catch (e: Exception) {
            return
        }
    }
}
