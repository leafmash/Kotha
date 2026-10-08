package com.kotha.app.core

object AppConfig {
    const val TERMS_VERSION = "2026-09-30"
    const val RESEND_LOCK_MS = 60_000L
    const val DELETE_LOCK_SECONDS = 3
    const val PAGE = 50
    const val EDIT_WINDOW_MS = 15 * 60 * 1000L
    const val EDIT_MAX = 5000
    const val FORWARD_MAX = 5
    const val MAX_PINNED = 3
    const val TYPING_IDLE_MS = 1_600L
    const val PRESENCE_BEAT_MS = 90_000L
    const val PRESENCE_STALE_MS = 240_000L
    const val RT_GRACE_MS = 180_000L
    const val PRESENCE_TICK_MS = 20_000L
    const val CLOUD_NAME = "xreqa1wz"
    const val UPLOAD_PRESET = "CovaMsg"
    const val IMAGE_MAX_SIDE = 1600
    const val IMAGE_QUALITY = 80
    const val IMAGE_SKIP_BYTES = 150 * 1024L
    const val THUMB_MAX_SIDE = 480
    const val THUMB_QUALITY = 75
    const val VOICE_MIN_MS = 700L
    const val WAVE_POINTS = 44
    const val ATTACH_MAX = 10
    const val UPLOAD_MAX_ATTEMPTS = 5
    const val UPLOAD_TIMEOUT_MS = 60_000
    const val RTDB_URL = "https://duskchat-e02ba-default-rtdb.asia-southeast1.firebasedatabase.app"
}
