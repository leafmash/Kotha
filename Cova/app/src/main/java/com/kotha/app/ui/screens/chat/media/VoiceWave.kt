package com.kotha.app.ui.screens.chat.media

import kotlin.math.max

object VoiceWave {

    const val BARS = 38

    fun resolve(raw: List<Float>, seed: String): List<Float> =
        if (raw.size in 4..256) fit(raw) else seeded(seed)

    private fun fit(raw: List<Float>): List<Float> {
        val clean = raw.map { it.coerceIn(0f, 100f) }
        return List(BARS) { index ->
            val from = index * clean.size / BARS
            val to = max(from + 1, (index + 1) * clean.size / BARS)
            var peak = 0f
            var cursor = from
            while (cursor < to && cursor < clean.size) {
                peak = max(peak, clean[cursor])
                cursor++
            }
            peak
        }
    }

    private fun seeded(seed: String): List<Float> {
        var hash = 2166136261L
        for (char in seed) hash = ((hash xor char.code.toLong()) * 16777619L) and 0xFFFFFFFFL
        var previous = 50f
        return List(BARS) { index ->
            hash = ((hash xor (index + 1).toLong()) * 16777619L) and 0xFFFFFFFFL
            previous = previous * 0.45f + (22 + (hash % 72)).toFloat() * 0.55f
            previous
        }
    }
}
