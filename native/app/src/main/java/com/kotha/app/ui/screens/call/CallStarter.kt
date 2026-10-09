package com.kotha.app.ui.screens.call

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.kotha.app.call.CallActivity
import com.kotha.app.call.CallPermissions
import com.kotha.app.data.call.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CallStarterViewModel @Inject constructor(val manager: CallManager) : ViewModel()

private data class PendingCall(val chatId: String, val peerUid: String, val video: Boolean)

fun openCallScreen(context: Context) {
    context.startActivity(
        Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
}

@Composable
fun rememberCallStarter(viewModel: CallStarterViewModel = hiltViewModel()): (String, String, Boolean) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingCall?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val call = pending
        pending = null
        if (call != null && CallPermissions.missing(context, call.video).isEmpty()) {
            begin(context, viewModel.manager, call)
        }
    }
    return remember(viewModel, launcher) {
        { chatId, peerUid, video ->
            val call = PendingCall(chatId, peerUid, video)
            if (viewModel.manager.hasActiveCall()) {
                openCallScreen(context)
            } else {
                val missing = CallPermissions.missing(context, video) + CallPermissions.missingOptional(context)
                if (missing.isEmpty()) {
                    begin(context, viewModel.manager, call)
                } else {
                    pending = call
                    launcher.launch(missing.distinct().toTypedArray())
                }
            }
        }
    }
}

private fun begin(context: Context, manager: CallManager, call: PendingCall) {
    if (manager.startOutgoing(call.chatId, call.peerUid, call.video)) openCallScreen(context)
}
