package com.kotha.app.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import com.kotha.app.ui.theme.CovaTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.chat.ReplyRef
import com.kotha.app.data.media.RecordingState
import com.kotha.app.ui.components.ActionOrb
import com.kotha.app.ui.theme.CovaMotion
import com.kotha.app.ui.theme.rememberReducedMotion
import com.kotha.app.ui.screens.chat.media.RecordingBar

@Composable
fun Composer(
    chatId: String,
    initialText: String,
    replyTo: ReplyRef?,
    onCancelReply: () -> Unit,
    recording: RecordingState?,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit
) {
    var text by rememberSaveable(chatId) { mutableStateOf(initialText) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
            HorizontalDivider(color = CovaTheme.colors.hairline)
            AnimatedVisibility(visible = replyTo != null) {
                ReplyBar(text = replyTo?.text.orEmpty(), onCancel = onCancelReply)
            }
            if (recording != null) {
                RecordingBar(
                    state = recording,
                    onCancel = onCancelRecording,
                    onSend = onStopRecording,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onAttach) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = stringResource(R.string.attach_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, CovaTheme.colors.hairline, RoundedCornerShape(26.dp))
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                onTextChange(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            decorationBox = { inner ->
                                Box {
                                    if (text.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.chat_message_placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    val reduced = rememberReducedMotion()
                    AnimatedContent(
                        targetState = text.isBlank(),
                        transitionSpec = {
                            if (reduced) {
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                (fadeIn(tween(CovaMotion.Quick)) + scaleIn(initialScale = 0.6f)) togetherWith
                                    (fadeOut(tween(CovaMotion.Quick)) + scaleOut(targetScale = 0.6f))
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp),
                        label = "composerAction"
                    ) { blank ->
                        if (blank) {
                            ActionOrb(
                                icon = Icons.Filled.Mic,
                                description = stringResource(R.string.voice_record),
                                onClick = onStartRecording
                            )
                        } else {
                            ActionOrb(
                                icon = Icons.AutoMirrored.Filled.Send,
                                description = stringResource(R.string.chat_send),
                                onClick = {
                                    val value = text
                                    text = ""
                                    onSend(value)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyBar(text: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 8.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 2.dp)) {
            Text(
                text = stringResource(R.string.chat_replying_to),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
        }
    }
}

@Composable
fun InfoBar(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
