package com.kotha.app.data.call

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProximityLock @Inject constructor(@ApplicationContext context: Context) {

    private val lock: PowerManager.WakeLock? = run {
        val manager = context.getSystemService(PowerManager::class.java)
        if (manager != null && manager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            manager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "cova:call-proximity").apply {
                setReferenceCounted(false)
            }
        } else {
            null
        }
    }

    fun update(enabled: Boolean) {
        val current = lock ?: return
        try {
            if (enabled && !current.isHeld) {
                current.acquire(MAX_HOLD_MS)
            } else if (!enabled && current.isHeld) {
                current.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        } catch (e: RuntimeException) {
            return
        }
    }

    private companion object {
        const val MAX_HOLD_MS = 4 * 60 * 60 * 1000L
    }
}
