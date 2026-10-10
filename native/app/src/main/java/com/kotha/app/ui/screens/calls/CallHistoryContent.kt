package com.kotha.app.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallMissedOutgoing
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.data.call.CallKind
import com.kotha.app.ui.components.Avatar
import com.kotha.app.ui.components.EmptyState
import com.kotha.app.ui.screens.call.rememberCallStarter
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.util.Format

private sealed interface CallListItem {
    val key: String

    data class Header(val ms: Long) : CallListItem {
        override val key: String = "day-${Format.dayStart(ms)}"
    }

    data class Entry(val row: CallRowUi) : CallListItem {
        override val key: String = row.record.id
    }
}

@Composable
fun CallHistoryContent(
    modifier: Modifier = Modifier,
    viewModel: CallHistoryViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val startCall = rememberCallStarter()
    val context = LocalContext.current
    if (rows.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Call,
            text = stringResource(R.string.calls_no_calls),
            modifier = modifier
        )
        return
    }
    val entries = remember(rows) {
        val out = ArrayList<CallListItem>(rows.size + 8)
        var lastDay = -1L
        for (row in rows) {
            val day = Format.dayStart(row.record.atMs)
            if (day != lastDay) {
                out.add(CallListItem.Header(row.record.atMs))
                lastDay = day
            }
            out.add(CallListItem.Entry(row))
        }
        out
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)
    ) {
        items(entries, key = { it.key }) { item ->
            when (item) {
                is CallListItem.Header -> Text(
                    text = Format.dayLabel(context, item.ms),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                is CallListItem.Entry -> CallRow(
                    row = item.row,
                    onCallBack = { startCall(item.row.record.chatId, item.row.record.peerUid, item.row.record.video) }
                )
            }
        }
    }
}

@Composable
private fun CallRow(row: CallRowUi, onCallBack: () -> Unit) {
    val record = row.record
    val colors = CovaTheme.colors
    val label = stringResource(if (record.video) R.string.call_short_video else R.string.call_short_voice)
    val kindText = when (record.kind) {
        CallKind.Done -> stringResource(if (record.outgoing) R.string.call_outgoing else R.string.call_incoming, label)
        CallKind.Missed -> stringResource(R.string.call_ev_missed, label)
        CallKind.Declined -> stringResource(R.string.call_ev_declined, label)
        CallKind.Cancelled -> stringResource(R.string.call_ev_cancelled, label)
        CallKind.Ringing -> stringResource(R.string.call_ringing)
    }
    val duration = if (record.kind == CallKind.Done && record.secs > 0) {
        " · " + Format.duration(record.secs.toDouble())
    } else {
        ""
    }
    val missed = record.kind == CallKind.Missed
    val directionIcon = when {
        record.kind == CallKind.Done || record.kind == CallKind.Ringing ->
            if (record.outgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived
        record.outgoing -> Icons.AutoMirrored.Filled.CallMissedOutgoing
        else -> Icons.AutoMirrored.Filled.CallMissed
    }
    val directionTint = when {
        missed -> colors.missed
        record.kind == CallKind.Done -> colors.success
        record.kind == CallKind.Ringing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rowShape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(role = Role.Button, onClick = onCallBack)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = row.name, photo = row.photo, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name.ifEmpty { stringResource(R.string.common_user) },
                style = MaterialTheme.typography.titleMedium,
                color = if (missed) colors.missed else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = directionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = directionTint
                )
                Text(
                    text = kindText + duration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Format.listTime(record.atMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.accentSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (record.video) Icons.Filled.Videocam else Icons.Filled.Call,
                    contentDescription = stringResource(R.string.calls_call_back),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
