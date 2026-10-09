package com.kotha.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kotha.app.BuildConfig

object Links {

    fun openLegal(context: Context, page: String) {
        val uri = Uri.parse("${BuildConfig.API_BASE}/$page.html?lang=${AppLanguage.code()}")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
