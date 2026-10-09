package com.kotha.app.ui.screens.chat.media

import androidx.compose.runtime.Stable
import com.kotha.app.data.media.VoicePlayer

@Stable
class MediaCallbacks(
    val voice: VoicePlayer,
    val onOpen: (String) -> Unit,
    val onRetry: (String) -> Unit,
    val onCancel: (String) -> Unit
)
