package com.kotha.app.data.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kotha.app.core.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class VoiceState(
    val id: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f
)

@Singleton
class VoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {

    private var exo: ExoPlayer? = null
    private var ticker: Job? = null
    private var pendingSeek: Float? = null
    private var speedIndex = 0
    private val mutableState = MutableStateFlow(VoiceState())

    val state: StateFlow<VoiceState> = mutableState.asStateFlow()

    fun observe(id: String): Flow<VoiceState?> =
        mutableState.map { if (it.id == id) it else null }.distinctUntilChanged()

    fun toggle(id: String, source: String) {
        if (source.isEmpty()) return
        val current = mutableState.value
        if (current.id == id && exo != null) {
            val player = player()
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        begin(id, source, null)
    }

    fun seek(id: String, source: String, fraction: Float) {
        if (source.isEmpty()) return
        val current = mutableState.value
        if (current.id == id && exo != null) {
            val player = player()
            val total = if (player.duration > 0) player.duration else current.durationMs
            if (total > 0) player.seekTo((total * fraction).toLong())
            if (!player.isPlaying) player.play()
            return
        }
        begin(id, source, fraction)
    }

    fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.size
        val speed = SPEEDS[speedIndex]
        exo?.setPlaybackSpeed(speed)
        mutableState.value = mutableState.value.copy(speed = speed)
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        pendingSeek = null
        exo?.let {
            it.stop()
            it.clearMediaItems()
        }
        mutableState.value = VoiceState(speed = SPEEDS[speedIndex])
    }

    private fun begin(id: String, source: String, fraction: Float?) {
        val player = player()
        pendingSeek = fraction
        player.setMediaItem(MediaItem.fromUri(source))
        player.setPlaybackSpeed(SPEEDS[speedIndex])
        player.prepare()
        player.play()
        mutableState.value = VoiceState(id = id, playing = true, speed = SPEEDS[speedIndex])
        startTicker()
    }

    private fun player(): ExoPlayer {
        exo?.let { return it }
        val created = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        created.addListener(listener)
        exo = created
        return created
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main) {
            while (true) {
                val player = exo ?: break
                val current = mutableState.value
                if (current.id != null) {
                    val duration = if (player.duration > 0) player.duration else current.durationMs
                    mutableState.value = current.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = duration,
                        playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
                    )
                }
                delay(TICK_MS)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exo ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    val seek = pendingSeek
                    if (seek != null && player.duration > 0) {
                        pendingSeek = null
                        player.seekTo((player.duration * seek).toLong())
                    }
                    mutableState.value = mutableState.value.copy(durationMs = player.duration.coerceAtLeast(0L))
                }
                Player.STATE_ENDED -> {
                    player.pause()
                    player.seekTo(0L)
                    mutableState.value = mutableState.value.copy(playing = false, positionMs = 0L)
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val current = mutableState.value
            if (current.id != null) mutableState.value = current.copy(playing = isPlaying)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            ticker?.cancel()
            ticker = null
            mutableState.value = VoiceState(speed = SPEEDS[speedIndex])
        }
    }

    companion object {
        val SPEEDS = listOf(1f, 1.5f, 2f)
        private const val TICK_MS = 50L
    }
}
