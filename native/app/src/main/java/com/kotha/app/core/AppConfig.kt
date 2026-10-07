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
    const val RTDB_URL = "https://duskchat-e02ba-default-rtdb.asia-southeast1.firebasedatabase.app"
}
