package com.kotha.app.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallMissedOutgoing
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.model.CallLog
import com.kotha.app.data.model.SysEvent
import com.kotha.app.data.model.UserProfile
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.ui.theme.brandBrush
import com.kotha.app.util.Format

@Composable
fun DayChip(ms: Long) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, CovaTheme.colors.hairline)
        ) {
            Text(
                text = Format.dayLabel(context, ms),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HiddenNote() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.block_hidden_message),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SystemEventRow(sys: SysEvent, from: String, uid: String, users: Map<String, UserProfile>) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
        ) {
            Text(
                text = sysText(sys, from, uid, users),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun sysText(sys: SysEvent, from: String, uid: String, users: Map<String, UserProfile>): String {
    val fallbackUser = stringResource(R.string.common_user)
    val by = when {
        from == uid -> stringResource(R.string.common_you)
        else -> users[from]?.name?.ifEmpty { null } ?: sys.by.ifEmpty { fallbackUser }
    }
    val youObj = stringResource(R.string.sys_you_obj)
    val list = sys.target.mapIndexed { index, id ->
        if (id == uid) youObj else users[id]?.name?.ifEmpty { null } ?: sys.names.getOrNull(index) ?: fallbackUser
    }
    val names = if (sys.target.size == 1 && sys.target[0] == uid) {
        stringResource(R.string.sys_you_full)
    } else {
        stringResource(R.string.sys_obj, list.joinToString(", "))
    }
    return when (sys.kind) {
        "added" -> stringResource(R.string.sys_added, by, names)
        "removed" -> stringResource(R.string.sys_removed, by, names)
        "left" -> stringResource(R.string.sys_left, by)
        "renamed" -> stringResource(R.string.sys_renamed, by, sys.name)
        "promoted" -> stringResource(R.string.sys_promoted, by, names)
        "demoted" -> stringResource(R.string.sys_demoted, by, names)
        "photo" -> stringResource(R.string.sys_photo, by)
        "desc" -> stringResource(R.string.sys_desc, by)
        else -> ""
    }
}

@Composable
fun CallEventRow(log: CallLog, mine: Boolean, atMs: Long, onCallBack: (() -> Unit)? = null) {
    val label = stringResource(if (log.video) R.string.call_short_video else R.string.call_short_voice)
    val kind = if (!mine && log.kind == "cancelled") "missed" else log.kind
    val seconds = log.secs
    val duration = if (seconds > 0) "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else ""
    val title = when (kind) {
        "done" -> stringResource(if (mine) R.string.call_ev_outgoing else R.string.call_ev_incoming, label)
        "declined" -> stringResource(R.string.call_ev_declined, label)
        "cancelled" -> stringResource(R.string.call_ev_cancelled, label)
        else -> if (mine) {
            stringResource(R.string.call_ev_outgoing, label)
        } else {
            stringResource(R.string.call_ev_missed, label)
        }
    }
    val sub = when {
        kind == "done" -> duration
        kind != "declined" && kind != "cancelled" && mine -> stringResource(R.string.call_no_answer)
        else -> ""
    }
    val bad = !mine && (kind == "missed" || kind == "cancelled")
    val failed = kind != "done"
    val time = Format.clock(atMs)
    val colors = CovaTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val tail = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (mine) 22.dp else tail,
        topEnd = if (mine) tail else 22.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp
    )
    val container = when {
        bad -> colors.missed.copy(alpha = 0.10f)
        mine -> colors.accentSoft
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val outline = when {
        bad -> colors.missed.copy(alpha = 0.28f)
        mine -> primary.copy(alpha = 0.28f)
        else -> colors.hairline
    }
    val directionIcon = when {
        kind == "done" && mine -> Icons.AutoMirrored.Filled.CallMade
        kind == "done" -> Icons.AutoMirrored.Filled.CallReceived
        mine -> Icons.AutoMirrored.Filled.CallMissedOutgoing
        else -> Icons.AutoMirrored.Filled.CallMissed
    }
    val directionTint = when {
        bad -> colors.missed
        failed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> colors.success
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = shape,
            color = container,
            border = BorderStroke(1.dp, outline),
            modifier = Modifier.widthIn(min = 220.dp, max = 312.dp)
        ) {
            Row(
                modifier = Modifier
                    .clickable(enabled = onCallBack != null) { onCallBack?.invoke() }
                    .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .then(
                            if (bad) Modifier.background(colors.missed) else Modifier.background(brandBrush())
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (log.video) Icons.Filled.Videocam else Icons.Filled.Call,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (bad) colors.missed else MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = directionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = directionTint
                        )
                        Text(
                            text = if (sub.isNotEmpty()) "$time · $sub" else time,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
