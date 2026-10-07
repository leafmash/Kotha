package com.kotha.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{chatId}"

    fun chat(chatId: String): String = "chat/$chatId"
}
