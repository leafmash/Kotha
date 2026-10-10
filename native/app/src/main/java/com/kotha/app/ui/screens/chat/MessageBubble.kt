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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.model.Message
import com.kotha.app.ui.components.avatarColor
import com.kotha.app.ui.screens.chat.media.FileBody
import com.kotha.app.ui.screens.chat.media.MediaCallbacks
import com.kotha.app.ui.screens.chat.media.VisualBody
import com.kotha.app.ui.screens.chat.media.VoiceBody
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.ui.theme.bubbleBrush
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
    selecting: Boolean,
    media: MediaCallbacks,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onQuoteClick: (String) -> Unit
) {
    val message = item.message
    val mine = item.mine
    val colors = CovaTheme.colors
    val container = if (mine) Color.Transparent else colors.bubbleTheirs
    val bubbleBackground = bubbleBrush()
    val actionsLabel = stringResource(R.string.a11y_message_actions)
    val content = if (mine) colors.onBubbleMine else colors.onBubbleTheirs
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    val maxDrag = with(density) { 84.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var armed by remember { mutableStateOf(false) }
    val direction = if (mine) -1f else 1f
    val tap: () -> Unit = { if (selecting) onClick() else media.onOpen(message.id) }
    val longPress: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress()
    }
    val tail = if (item.first) 6.dp else 9.dp
    val shape = RoundedCornerShape(
        topStart = if (mine) 22.dp else tail,
        topEnd = if (mine) tail else 22.dp,
        bottomStart = if (mine) 22.dp else 9.dp,
        bottomEnd = if (mine) 9.dp else 22.dp
    )
    val accent = if (mine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (item.first) 8.dp else 2.dp)
            .pointerInput(message.id, message.deleted, selecting) {
                if (message.deleted || selecting) return@pointerInput
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
        Box(
            modifier = Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 10.dp)
                .alpha((offset.value / threshold).coerceIn(0f, 1f))
                .size(34.dp)
                .background(CovaTheme.colors.accentSoft, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
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
                border = when {
                    highlighted -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    !mine -> BorderStroke(1.dp, colors.hairline)
                    else -> null
                },
                modifier = Modifier
                    .widthIn(max = 312.dp)
                    .then(if (mine) Modifier.background(bubbleBackground, shape) else Modifier)
                    .semantics(mergeDescendants = true) {}
                    .combinedClickable(
                        onClick = onClick,
                        onLongClickLabel = actionsLabel,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 7.dp)) {
                    if (group && !mine) {
                        Text(
                            text = senderName.ifEmpty { stringResource(R.string.common_user) },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = avatarColor(message.from),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 2.dp)
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
                            accent = accent,
                            onClick = { message.replyId?.let(onQuoteClick) }
                        )
                    }
                    BubbleBody(
                        message = message,
                        content = content,
                        mine = mine,
                        selecting = selecting,
                        media = media,
                        onTap = tap,
                        onLongPress = longPress
                    )
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
private fun QuoteBlock(text: String, content: Color, accent: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = content.copy(alpha = 0.12f),
        modifier = Modifier
            .padding(top = 2.dp, bottom = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BubbleBody(
    message: Message,
    content: Color,
    mine: Boolean,
    selecting: Boolean,
    media: MediaCallbacks,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
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
        message.type == "image" || message.type == "video" ->
            VisualBody(message = message, onTap = onTap, onLongPress = onLongPress, callbacks = media)
        message.type == "audio" -> VoiceBody(
            message = message,
            content = content,
            selecting = selecting,
            onTap = onTap,
            onLongPress = onLongPress,
            callbacks = media
        )
        message.type == "file" ->
            FileBody(message = message, content = content, onTap = onTap, onLongPress = onLongPress, callbacks = media)
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
private fun ReactionChip(message: Message, content: Color) {
    val picks = message.reactions.values
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = content.copy(alpha = 0.14f),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
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
        modifier = modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (message.edited && !message.deleted) {
            Text(
                text = stringResource(R.string.msg_edited),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.72f)
            )
        }
        Text(
            text = Format.clock(message.atMs),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.72f)
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
                modifier = Modifier.size(16.dp),
                tint = if (seen) CovaTheme.colors.tick else content.copy(alpha = 0.72f)
            )
        }
    }
}
