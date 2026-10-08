package com.kotha.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLanguage {

    fun locale(): Locale {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) Locale.getDefault() else locales.get(0) ?: Locale.getDefault()
    }

    fun code(): String = if (locale().language == "bn") "bn" else "en"

    fun set(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    fun toggle() {
        val next = if (code() == "bn") "en" else "bn"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
    }
}
