package com.kotha.app.ui

import android.content.Context
import androidx.annotation.StringRes

data class UiMessage(@StringRes val res: Int, val args: List<String> = emptyList()) {

    fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
}
