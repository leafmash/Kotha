package com.kotha.app.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.kotha.app.core.ApplicationScope
import com.kotha.app.core.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecordingState(val elapsedMs: Long, val levels: List<Float>)

class RecordedVoice(val file: File, val durationMs: Long, val wave: List<Int>)

@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var loop: Job? = null
    private val samples = ArrayList<Int>()
    private val mutableState = MutableStateFlow<RecordingState?>(null)

    val state: StateFlow<RecordingState?> = mutableState.asStateFlow()

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        if (recorder != null) return false
        val target = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return try {
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioChannels(1)
            instance.setAudioSamplingRate(SAMPLE_RATE)
            instance.setAudioEncodingBitRate(BIT_RATE)
            instance.setOutputFile(target.absolutePath)
            instance.prepare()
            instance.start()
            recorder = instance
            file = target
            startedAt = SystemClock.elapsedRealtime()
            synchronized(samples) { samples.clear() }
            mutableState.value = RecordingState(0L, emptyList())
            loop = scope.launch(Dispatchers.Default) { track(instance) }
            true
        } catch (e: Exception) {
            runCatching { instance.release() }
            target.delete()
            false
        }
    }

    fun stop(): RecordedVoice? {
        val active = recorder ?: return null
        val duration = SystemClock.elapsedRealtime() - startedAt
        loop?.cancel()
        loop = null
        val stopped = try {
            active.stop()
            true
        } catch (e: RuntimeException) {
            false
        }
        runCatching { active.release() }
        recorder = null
        mutableState.value = null
        val output = file
        file = null
        if (!stopped || output == null || duration < AppConfig.VOICE_MIN_MS) {
            output?.delete()
            return null
        }
        return RecordedVoice(output, duration, buildWave())
    }

    fun cancel() {
        val active = recorder ?: return
        loop?.cancel()
        loop = null
        runCatching { active.stop() }
        runCatching { active.release() }
        recorder = null
        mutableState.value = null
        file?.delete()
        file = null
    }

    private suspend fun track(instance: MediaRecorder) {
        val window = ArrayList<Float>()
        while (currentCoroutineContext().isActive) {
            delay(TICK_MS)
            val amplitude = try {
                instance.maxAmplitude
            } catch (e: Exception) {
                0
            }
            synchronized(samples) { samples.add(amplitude) }
            val level = (amplitude / MAX_AMPLITUDE).toDouble().pow(0.5).toFloat().coerceIn(0f, 1f)
            window.add(level)
            if (window.size > WINDOW) window.removeAt(0)
            mutableState.value = RecordingState(SystemClock.elapsedRealtime() - startedAt, window.toList())
        }
    }

    private fun buildWave(): List<Int> {
        val copy = synchronized(samples) { samples.toList() }
        if (copy.isEmpty()) return List(AppConfig.WAVE_POINTS) { 0 }
        val size = copy.size.toDouble() / AppConfig.WAVE_POINTS
        val peaks = List(AppConfig.WAVE_POINTS) { index ->
            val from = (index * size).toInt()
            val to = max(from + 1, ((index + 1) * size).toInt())
            var peak = 0
            var cursor = from
            while (cursor < to && cursor < copy.size) {
                peak = max(peak, copy[cursor])
                cursor++
            }
            peak.toDouble()
        }
        val top = max(peaks.maxOrNull() ?: 0.0, 1.0)
        return peaks.map { ((it / top).coerceIn(0.0, 1.0).pow(0.7) * 100).roundToInt() }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 96_000
        const val TICK_MS = 60L
        const val WINDOW = 30
        const val MAX_AMPLITUDE = 32_767f
    }
}
