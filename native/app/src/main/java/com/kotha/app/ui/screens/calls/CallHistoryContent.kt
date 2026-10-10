package com.kotha.app.ui.screens.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.data.call.CallKind
import com.kotha.app.ui.components.Avatar
import com.kotha.app.ui.screens.call.rememberCallStarter
import com.kotha.app.util.Format

@Composable
fun CallHistoryContent(
    modifier: Modifier = Modifier,
    viewModel: CallHistoryViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val startCall = rememberCallStarter()
    if (rows.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Call,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.calls_no_calls),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(rows, key = { it.record.id }) { row ->
            val record = row.record
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
            ListItem(
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                leadingContent = { Avatar(name = row.name, photo = row.photo, size = 48.dp) },
                headlineContent = {
                    Text(
                        text = row.name.ifEmpty { stringResource(R.string.common_user) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                },
                supportingContent = {
                    Text(
                        text = kindText + duration + " · " + Format.listTime(record.atMs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    IconButton(onClick = { startCall(record.chatId, record.peerUid, record.video) }) {
                        Icon(
                            imageVector = if (record.video) Icons.Filled.Videocam else Icons.Filled.Call,
                            contentDescription = stringResource(R.string.calls_call_back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    }
}
