package com.kotha.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.data.prefs.AppPreferences
import com.kotha.app.data.prefs.ThemeMode
import com.kotha.app.data.session.SessionState
import com.kotha.app.notify.DeepLink
import com.kotha.app.notify.DeepLinkRouter
import com.kotha.app.notify.MessageNotifier
import com.kotha.app.notify.NotificationCoordinator
import com.kotha.app.ui.CovaRoot
import com.kotha.app.ui.theme.CovaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var preferences: AppPreferences

    @Inject
    lateinit var router: DeepLinkRouter

    @Inject
    lateinit var coordinator: NotificationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        coordinator.start()
        if (savedInstanceState == null) handleLink(intent)
        splash.setKeepOnScreenCondition { viewModel.session.value is SessionState.Loading }
        setContent {
            val session by viewModel.session.collectAsStateWithLifecycle()
            val mode by preferences.themeMode.collectAsStateWithLifecycle()
            val fontScale by preferences.fontScale.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1B, 0x1B, 0x1B)
                    ) { dark }
                )
                onDispose { }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * fontScale)
            ) {
                CovaTheme(darkTheme = dark) {
                    CovaRoot(session)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(source: Intent?) {
        val launched = source ?: return
        val fromHistory = launched.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val chatId = launched.getStringExtra(DeepLink.EXTRA_CHAT_ID)?.takeIf { it.isNotBlank() }
        val call = launched.getBooleanExtra(DeepLink.EXTRA_CALL, false)
        launched.removeExtra(DeepLink.EXTRA_CHAT_ID)
        launched.removeExtra(DeepLink.EXTRA_CALL)
        val link = if (chatId != null && !fromHistory) DeepLink(chatId, call) else null
        applyLockScreenFlags(link?.call == true)
        if (link == null) return
        MessageNotifier.clearChat(this, link.chatId, link.call)
        router.publish(link)
    }

    @Suppress("DEPRECATION")
    private fun applyLockScreenFlags(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(show)
            setTurnScreenOn(show)
            return
        }
        val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (show) window.addFlags(flags) else window.clearFlags(flags)
    }
}
