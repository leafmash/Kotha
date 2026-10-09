package com.kotha.app.notify

import android.content.Context
import android.content.Intent
import com.kotha.app.MainActivity

data class DeepLink(
    val chatId: String,
    val call: Boolean,
    val stamp: Long = System.nanoTime()
) {

    companion object {
        const val EXTRA_CHAT_ID = "kotha_chat_id"
        const val EXTRA_CALL = "kotha_call"

        fun intent(context: Context, chatId: String, call: Boolean): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_CALL, call)
    }
}
