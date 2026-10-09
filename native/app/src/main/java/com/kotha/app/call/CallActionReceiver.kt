package com.kotha.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kotha.app.notify.notifyEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.applicationContext.notifyEntryPoint().callManager()
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        when (intent.action) {
            ACTION_DECLINE -> {
                val pending = goAsync()
                val job = manager.decline(callId)
                CoroutineScope(Dispatchers.Main).launch {
                    withTimeoutOrNull(WAIT_MS) { job.join() }
                    pending.finish()
                }
            }
            ACTION_HANGUP -> manager.hangUp()
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.kotha.app.call.DECLINE"
        const val ACTION_HANGUP = "com.kotha.app.call.HANGUP"
        const val EXTRA_CALL_ID = "kotha_call_id"
        private const val WAIT_MS = 8_000L
    }
}
