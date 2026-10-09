package com.kotha.app.call

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kotha.app.ui.screens.call.CallScreen
import com.kotha.app.ui.screens.call.CallViewModel
import com.kotha.app.ui.theme.CovaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class CallActivity : AppCompatActivity() {

    private val viewModel: CallViewModel by viewModels()
    private var answerRequested = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.accept()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyLockScreenFlags()
        answerRequested = intent?.action == ACTION_ANSWER
        intent?.action = null
        setContent {
            CovaTheme(darkTheme = true) {
                CallScreen(onAnswer = ::answer)
            }
        }
        lifecycleScope.launch {
            val first = withTimeoutOrNull(START_WAIT_MS) { viewModel.state.filterNotNull().first() }
            if (first == null) {
                finish()
                return@launch
            }
            if (answerRequested) {
                answerRequested = false
                answer()
            }
            viewModel.state.filter { it == null }.first()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_ANSWER) {
            intent.action = null
            answer()
        }
    }

    private fun answer() {
        val video = viewModel.state.value?.video ?: false
        val missing = CallPermissions.missing(this, video) + CallPermissions.missingOptional(this)
        if (missing.isEmpty()) {
            viewModel.accept()
        } else {
            permissionLauncher.launch(missing.distinct().toTypedArray())
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.kotha.app.call.ANSWER"
        private const val START_WAIT_MS = 4_000L
    }
}
