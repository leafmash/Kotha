package com.kotha.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLanguage {

    fun code(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val locale = if (locales.isEmpty()) Locale.getDefault() else locales.get(0)
        return if (locale?.language == "bn") "bn" else "en"
    }

    fun toggle() {
        val next = if (code() == "bn") "en" else "bn"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
    }
}
