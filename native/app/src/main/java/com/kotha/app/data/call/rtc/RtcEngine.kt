package com.kotha.app.data.call.rtc

import android.content.Context
import com.kotha.app.core.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

class RtcResources(
    val factory: PeerConnectionFactory,
    val egl: EglBase,
    val audioModule: JavaAudioDeviceModule
)

@Singleton
class RtcEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {

    private var resources: RtcResources? = null
    private var users = 0
    private var disposeJob: Job? = null

    @Synchronized
    fun acquire(): RtcResources {
        disposeJob?.cancel()
        disposeJob = null
        users += 1
        return resources ?: create().also { resources = it }
    }

    @Synchronized
    fun release() {
        if (users > 0) users -= 1
        if (users > 0) return
        disposeJob?.cancel()
        disposeJob = scope.launch {
            delay(IDLE_MS)
            disposeIfIdle()
        }
    }

    @Synchronized
    private fun disposeIfIdle() {
        if (users > 0) return
        val current = resources ?: return
        resources = null
        runCatching { current.factory.dispose() }
        runCatching { current.audioModule.release() }
        runCatching { current.egl.release() }
    }

    private fun create(): RtcResources {
        if (!initialized) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions()
            )
            initialized = true
        }
        val egl = EglBase.create()
        val audioModule = JavaAudioDeviceModule.builder(context.applicationContext).createAudioDeviceModule()
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()
        return RtcResources(factory, egl, audioModule)
    }

    private companion object {
        const val IDLE_MS = 30_000L

        @Volatile
        var initialized = false
    }
}
