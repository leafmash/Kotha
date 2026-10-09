package com.kotha.app.data.call

import android.media.AudioManager
import android.media.ToneGenerator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallTones @Inject constructor() {

    private var generator: ToneGenerator? = null

    fun startRingback() {
        stop()
        generator = try {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, VOLUME).also { it.startTone(ToneGenerator.TONE_SUP_RINGTONE) }
        } catch (e: RuntimeException) {
            null
        }
    }

    fun stop() {
        val current = generator ?: return
        generator = null
        runCatching { current.stopTone() }
        runCatching { current.release() }
    }

    private companion object {
        const val VOLUME = 70
    }
}
