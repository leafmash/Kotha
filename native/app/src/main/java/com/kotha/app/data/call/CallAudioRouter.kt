package com.kotha.app.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioRoute { Earpiece, Speaker, Bluetooth, Wired }

data class AudioRoutes(
    val available: List<AudioRoute> = listOf(AudioRoute.Earpiece, AudioRoute.Speaker),
    val selected: AudioRoute = AudioRoute.Earpiece
)

@Singleton
class CallAudioRouter @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(AudioRoutes())
    val routes: StateFlow<AudioRoutes> = mutable.asStateFlow()

    private var active = false
    private var previousMode = AudioManager.MODE_NORMAL
    private var focusRequest: AudioFocusRequest? = null
    private var scoStarted = false
    private var preferred: AudioRoute? = null
    private var fallback = AudioRoute.Earpiece

    private val focusListener = AudioManager.OnAudioFocusChangeListener { _ -> }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            preferred = null
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }
    }

    fun start(video: Boolean) {
        if (active) return
        active = true
        previousMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        requestFocus()
        preferred = null
        fallback = if (video) AudioRoute.Speaker else AudioRoute.Earpiece
        manager.registerAudioDeviceCallback(deviceCallback, handler)
        refresh()
    }

    fun stop() {
        if (!active) return
        active = false
        runCatching { manager.unregisterAudioDeviceCallback(deviceCallback) }
        resetRouting()
        abandonFocus()
        manager.mode = previousMode
        preferred = null
        mutable.value = AudioRoutes()
    }

    fun select(route: AudioRoute) {
        if (!active || route !in mutable.value.available) return
        preferred = route
        refresh()
    }

    fun toggleSpeaker() {
        val state = mutable.value
        if (state.selected == AudioRoute.Speaker) {
            val other = state.available.firstOrNull { it != AudioRoute.Speaker } ?: return
            select(other)
        } else {
            select(AudioRoute.Speaker)
        }
    }

    private fun refresh() {
        if (!active) return
        val available = scan()
        if (preferred != null && preferred !in available) preferred = null
        val auto = when {
            AudioRoute.Wired in available -> AudioRoute.Wired
            AudioRoute.Bluetooth in available -> AudioRoute.Bluetooth
            fallback in available -> fallback
            else -> available.firstOrNull() ?: AudioRoute.Speaker
        }
        val target = preferred ?: auto
        val applied = apply(target)
        mutable.value = AudioRoutes(available, applied)
    }

    private fun scan(): List<AudioRoute> {
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.availableCommunicationDevices.map { it.type }
        } else {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        }
        val out = ArrayList<AudioRoute>(4)
        if (types.contains(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)) out.add(AudioRoute.Earpiece)
        if (types.contains(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) || out.isEmpty()) out.add(AudioRoute.Speaker)
        if (types.any { routeOf(it) == AudioRoute.Bluetooth }) out.add(AudioRoute.Bluetooth)
        if (types.any { routeOf(it) == AudioRoute.Wired }) out.add(AudioRoute.Wired)
        return out
    }

    private fun routeOf(type: Int): AudioRoute? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.Earpiece
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.Speaker
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, TYPE_BLE_HEADSET -> AudioRoute.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRoute.Wired
        else -> null
    }

    private fun apply(route: AudioRoute): AudioRoute {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return applyModern(route)
            applyLegacy(route)
        } catch (e: Exception) {
            return route
        }
        return route
    }

    private fun applyModern(route: AudioRoute): AudioRoute {
        val device = manager.availableCommunicationDevices.firstOrNull { routeOf(it.type) == route }
        if (device != null) manager.setCommunicationDevice(device) else manager.clearCommunicationDevice()
        val actual = manager.communicationDevice?.let { routeOf(it.type) }
        return actual ?: route
    }

    @Suppress("DEPRECATION")
    private fun applyLegacy(route: AudioRoute) {
        when (route) {
            AudioRoute.Speaker -> {
                stopSco()
                manager.isSpeakerphoneOn = true
            }
            AudioRoute.Bluetooth -> {
                manager.isSpeakerphoneOn = false
                startSco()
            }
            AudioRoute.Earpiece, AudioRoute.Wired -> {
                stopSco()
                manager.isSpeakerphoneOn = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startSco() {
        if (scoStarted || !manager.isBluetoothScoAvailableOffCall) return
        manager.startBluetoothSco()
        manager.isBluetoothScoOn = true
        scoStarted = true
    }

    @Suppress("DEPRECATION")
    private fun stopSco() {
        if (!scoStarted) return
        manager.isBluetoothScoOn = false
        manager.stopBluetoothSco()
        scoStarted = false
    }

    @Suppress("DEPRECATION")
    private fun resetRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.clearCommunicationDevice()
            } else {
                stopSco()
                manager.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            return
        }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(focusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusListener)
        }
    }

    private companion object {
        const val TYPE_BLE_HEADSET = 26
    }
}
