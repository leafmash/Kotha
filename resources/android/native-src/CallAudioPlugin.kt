package com.kotha.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "CallAudio")
class CallAudioPlugin : Plugin() {

    private var previousMode = AudioManager.MODE_NORMAL
    private var active = false

    private val privateOutputs = listOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    )

    private fun audio(): AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @PluginMethod
    fun start(call: PluginCall) {
        val manager = audio()
        if (!active) {
            previousMode = manager.mode
            active = true
        }
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        applySpeaker(call.getBoolean("speaker") ?: false)
        keepScreenOn(true)
        call.resolve()
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        releaseAudio()
        call.resolve()
    }

    @PluginMethod
    fun setSpeaker(call: PluginCall) {
        applySpeaker(call.getBoolean("on") ?: false)
        call.resolve()
    }

    @PluginMethod
    fun isSpeaker(call: PluginCall) {
        val result = JSObject()
        result.put("on", speakerActive())
        call.resolve(result)
    }

    override fun handleOnDestroy() {
        releaseAudio()
        super.handleOnDestroy()
    }

    private fun releaseAudio() {
        keepScreenOn(false)
        if (!active) return
        active = false
        val manager = audio()
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                manager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            return
        } finally {
            manager.mode = previousMode
        }
    }

    private fun applySpeaker(on: Boolean) {
        val manager = audio()
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val devices = manager.availableCommunicationDevices
                if (on) {
                    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) manager.setCommunicationDevice(speaker)
                } else {
                    val target = privateOutputs.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
                    if (target != null) manager.setCommunicationDevice(target) else manager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = on
            }
        } catch (e: Exception) {
            return
        }
    }

    private fun speakerActive(): Boolean {
        val manager = audio()
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                manager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun keepScreenOn(on: Boolean) {
        val host = activity ?: return
        host.runOnUiThread {
            if (on) {
                host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
