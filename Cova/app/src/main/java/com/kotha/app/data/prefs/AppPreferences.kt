package com.kotha.app.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("cova_settings", Context.MODE_PRIVATE)
    private val mutableTheme = MutableStateFlow(readTheme())
    private val mutableFont = MutableStateFlow(prefs.getFloat(KEY_FONT, 1f))
    private val mutableReceipts = MutableStateFlow(prefs.getBoolean(KEY_RECEIPTS, true))

    val themeMode: StateFlow<ThemeMode> = mutableTheme.asStateFlow()
    val fontScale: StateFlow<Float> = mutableFont.asStateFlow()
    val readReceipts: StateFlow<Boolean> = mutableReceipts.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        mutableTheme.value = mode
    }

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT, scale).apply()
        mutableFont.value = scale
    }

    fun setReadReceipts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECEIPTS, enabled).apply()
        mutableReceipts.value = enabled
    }

    private fun readTheme(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FONT = "font_scale"
        const val KEY_RECEIPTS = "read_receipts"
    }
}
