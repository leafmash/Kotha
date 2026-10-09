package com.kotha.app.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.model.CallLog
import com.kotha.app.data.model.SysEvent
import com.kotha.app.data.model.UserProfile
import com.kotha.app.util.Format

@Composable
fun DayChip(ms: Long) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                text = Format.dayLabel(context, ms),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
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
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = sysText(sys, from, uid, users),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
fun CallEventRow(log: CallLog, mine: Boolean, atMs: Long) {
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
    val time = Format.clock(atMs)
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (log.video) Icons.Filled.Videocam else Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (sub.isNotEmpty()) "$time · $sub" else time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
