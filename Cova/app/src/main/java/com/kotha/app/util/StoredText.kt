package com.kotha.app.util

import android.content.Context
import com.kotha.app.R

object StoredText {

    private val tokens = mapOf(
        "deleted" to R.string.chat_deleted,
        "groupCreated" to R.string.group_created,
        "membersAdded" to R.string.group_members_added,
        "image" to R.string.common_photo,
        "video" to R.string.common_video,
        "audio" to R.string.common_voice_message,
        "file" to R.string.common_file
    )

    private val legacy = mapOf(
        "মেসেজ মুছে ফেলা হয়েছে" to "deleted",
        "গ্রুপ তৈরি হয়েছে" to "groupCreated",
        "ছবি" to "image",
        "ভিডিও" to "video",
        "ভয়েস মেসেজ" to "audio",
        "ফাইল" to "file"
    )

    private val pattern = Regex("^\\[\\[(\\w+)]]$")

    fun token(name: String): String = "[[$name]]"

    fun display(context: Context, value: String): String {
        val name = pattern.matchEntire(value)?.groupValues?.get(1) ?: legacy[value]
        val res = name?.let { tokens[it] } ?: return value
        return context.getString(res)
    }
}
