package com.kotha.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{chatId}"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val BLOCKED = "blocked"
    const val GROUP = "group/{chatId}"
    const val USER = "user/{uid}/{chatId}"

    fun chat(chatId: String): String = "chat/$chatId"

    fun group(chatId: String): String = "group/$chatId"

    fun user(uid: String, chatId: String): String = "user/$uid/$chatId"
}
