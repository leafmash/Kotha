package com.kotha.app.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.model.Message
import com.kotha.app.ui.components.avatarColor
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.util.Format
import com.kotha.app.util.MessageText
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    item: ChatItem.Bubble,
    group: Boolean,
    senderName: String,
    highlighted: Boolean,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onQuoteClick: (String) -> Unit
) {
    val message = item.message
    val mine = item.mine
    val colors = CovaTheme.colors
    val container = if (mine) colors.bubbleMine else colors.bubbleTheirs
    val content = if (mine) colors.onBubbleMine else colors.onBubbleTheirs
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    val maxDrag = with(density) { 84.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var armed by remember { mutableStateOf(false) }
    val direction = if (mine) -1f else 1f
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (!mine && item.first) 6.dp else 18.dp,
        bottomEnd = if (mine && item.first) 6.dp else 18.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (item.first) 6.dp else 0.dp)
            .pointerInput(message.id, message.deleted) {
                if (message.deleted) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offset.value >= threshold) onReply()
                        armed = false
                        scope.launch { offset.animateTo(0f, spring()) }
                    },
                    onDragCancel = {
                        armed = false
                        scope.launch { offset.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val next = (offset.value + amount * direction).coerceIn(0f, maxDrag)
                        scope.launch { offset.snapTo(next) }
                        val nowArmed = next >= threshold
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (nowArmed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
            }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            modifier = Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 12.dp)
                .alpha((offset.value / threshold).coerceIn(0f, 1f)),
            tint = MaterialTheme.colorScheme.primary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset((offset.value * direction).roundToInt(), 0) },
            contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                shape = shape,
                color = container,
                contentColor = content,
                border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (group && !mine) {
                        Text(
                            text = senderName.ifEmpty { stringResource(R.string.common_user) },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = avatarColor(message.from),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (message.forwarded && !message.deleted) {
                        Text(
                            text = "↪ " + stringResource(R.string.msg_forwarded),
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = content.copy(alpha = 0.7f)
                        )
                    }
                    if (message.replyText != null && !message.deleted && !item.quoteHidden) {
                        QuoteBlock(
                            text = message.replyText,
                            content = content,
                            onClick = { message.replyId?.let(onQuoteClick) }
                        )
                    }
                    BubbleBody(message = message, content = content, mine = mine)
                    if (message.reactions.isNotEmpty() && !message.deleted) {
                        ReactionChip(message = message, content = content)
                    }
                    MetaRow(
                        message = message,
                        mine = mine,
                        content = content,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteBlock(text: String, content: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = content.copy(alpha = 0.10f),
        modifier = Modifier
            .padding(bottom = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BubbleBody(message: Message, content: Color, mine: Boolean) {
    when {
        message.deleted -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = content.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.chat_deleted),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = content.copy(alpha = 0.7f)
            )
        }
        message.type == "image" || message.type == "video" || message.type == "audio" || message.type == "file" ->
            MediaPlaceholder(message = message, content = content)
        else -> Text(
            text = MessageText.linkify(
                message.text,
                if (mine) content else MaterialTheme.colorScheme.primary
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = content
        )
    }
}

@Composable
private fun MediaPlaceholder(message: Message, content: Color) {
    val uriHandler = LocalUriHandler.current
    val (icon, label) = when (message.type) {
        "image" -> Icons.Filled.Image to stringResource(R.string.common_photo)
        "video" -> Icons.Filled.Videocam to stringResource(R.string.common_video)
        "audio" -> Icons.Filled.Mic to stringResource(R.string.common_voice_message)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to message.name.ifEmpty { stringResource(R.string.common_file) }
    }
    Row(
        modifier = Modifier
            .clickable(enabled = message.url.isNotEmpty()) { runCatching { uriHandler.openUri(message.url) } }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = content)
    }
}

@Composable
private fun ReactionChip(message: Message, content: Color) {
    val picks = message.reactions.values
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = content.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = picks.toSet().take(3).joinToString(""), style = MaterialTheme.typography.bodyMedium)
            if (picks.size > 1) {
                Text(
                    text = Format.number(picks.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun MetaRow(message: Message, mine: Boolean, content: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (message.edited && !message.deleted) {
            Text(
                text = stringResource(R.string.msg_edited),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.65f)
            )
        }
        Text(
            text = Format.clock(message.atMs),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.65f)
        )
        if (mine && !message.deleted) {
            val seen = message.status == "seen"
            val reached = seen || message.status == "delivered"
            Icon(
                imageVector = when {
                    message.pending -> Icons.Filled.Schedule
                    reached -> Icons.Filled.DoneAll
                    else -> Icons.Filled.Done
                },
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (seen) CovaTheme.colors.tick else content.copy(alpha = 0.65f)
            )
        }
    }
}
