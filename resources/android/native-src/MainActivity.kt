package com.kotha.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.PermissionRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.getcapacitor.BridgeActivity
import com.getcapacitor.BridgeWebChromeClient

class MainActivity : BridgeActivity() {

    private var pendingMediaRequest: PermissionRequest? = null

    private val mediaPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = pendingMediaRequest
        pendingMediaRequest = null
        if (request != null) runOnUiThread { resolveMediaRequest(request) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(DeepLinkPlugin::class.java)
        registerPlugin(SessionPlugin::class.java)
        registerPlugin(CallAudioPlugin::class.java)
        registerPlugin(BatteryOptimizationPlugin::class.java)
        registerPlugin(AppUpdaterPlugin::class.java)
        super.onCreate(savedInstanceState)
        applyLockScreenFlags(intent)
        capturePendingChat(intent)
        setupMediaPermissionHandling()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLockScreenFlags(intent)
        val chatId = extractChatId(intent) ?: return
        bridge?.triggerJSEvent("kothaNotificationTap", "window", "\"${escapeForJs(chatId)}\"")
    }

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
        activeChatId = null
        super.onPause()
    }

    private fun setupMediaPermissionHandling() {
        val activeBridge = bridge ?: return
        val webView = activeBridge.webView ?: return
        webView.webChromeClient = object : BridgeWebChromeClient(activeBridge) {
            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsMedia = request.resources.any {
                    it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                }
                if (wantsMedia) handleMediaRequest(request) else super.onPermissionRequest(request)
            }
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun handleMediaRequest(request: PermissionRequest) {
        val missing = mutableListOf<String>()
        request.resources.forEach {
            if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && !isGranted(Manifest.permission.RECORD_AUDIO)) {
                missing.add(Manifest.permission.RECORD_AUDIO)
            }
            if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && !isGranted(Manifest.permission.CAMERA)) {
                missing.add(Manifest.permission.CAMERA)
            }
        }
        if (missing.isEmpty()) {
            runOnUiThread { resolveMediaRequest(request) }
            return
        }
        pendingMediaRequest?.deny()
        pendingMediaRequest = request
        mediaPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun resolveMediaRequest(request: PermissionRequest) {
        val allowed = request.resources.filter {
            when (it) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> isGranted(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> isGranted(Manifest.permission.CAMERA)
                else -> false
            }
        }.toTypedArray()
        if (allowed.isEmpty()) request.deny() else request.grant(allowed)
    }

    private fun applyLockScreenFlags(intent: Intent?) {
        if (Build.VERSION.SDK_INT < 27) return
        val incomingCall = intent?.getBooleanExtra(EXTRA_CALL, false) == true
        setShowWhenLocked(incomingCall)
        setTurnScreenOn(incomingCall)
    }

    private fun capturePendingChat(intent: Intent?) {
        pendingChatId = extractChatId(intent)
    }

    private fun extractChatId(intent: Intent?): String? {
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)?.takeIf { it.isNotBlank() } ?: return null
        val wasCall = intent.getBooleanExtra(EXTRA_CALL, false)
        intent.removeExtra(EXTRA_CHAT_ID)
        intent.removeExtra(EXTRA_CALL)
        ChatConversationStore.clear(applicationContext, chatId)
        val manager = NotificationManagerCompat.from(this)
        manager.cancel(chatId.hashCode())
        if (wasCall) manager.cancel(("call:$chatId").hashCode())
        return chatId
    }

    private fun escapeForJs(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val EXTRA_CHAT_ID = "kotha_chat_id"
        const val EXTRA_CALL = "kotha_call"

        @Volatile
        var foreground = false

        @Volatile
        private var pendingChatId: String? = null

        @Volatile
        private var activeChatId: String? = null

        @Synchronized
        fun consumePendingChat(): String? {
            val chatId = pendingChatId
            pendingChatId = null
            return chatId
        }

        @Synchronized
        fun setActiveChat(chatId: String?) {
            activeChatId = chatId
        }

        @Synchronized
        fun getActiveChat(): String? = activeChatId
    }
}
