package com.kotha.app.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.presence.PresenceInfo
import com.kotha.app.ui.components.Avatar
import com.kotha.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(header: HeaderUi, onBack: () -> Unit) {
    val (statusText, live) = statusLabel(header.status)
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name = header.name, photo = header.photo, size = 40.dp, online = header.online)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = header.name.ifEmpty { stringResource(R.string.common_user) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (statusText.isNotEmpty()) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
private fun statusLabel(status: HeaderStatus): Pair<String, Boolean> = when (status) {
    HeaderStatus.None -> "" to false
    is HeaderStatus.Typing -> {
        val text = if (status.names.isEmpty()) {
            stringResource(R.string.chat_typing)
        } else {
            stringResource(R.string.chat_typing_many, status.names.joinToString(", "))
        }
        text to true
    }
    is HeaderStatus.Members -> stringResource(R.string.chat_members, Format.number(status.count)) to false
    is HeaderStatus.Active -> presenceText(status.info, status.now) to status.info.online
}

@Composable
private fun presenceText(info: PresenceInfo, now: Long): String {
    if (info.online) return stringResource(R.string.presence_now)
    if (info.ms <= 0) return stringResource(R.string.chat_offline)
    val mins = maxOf(0L, now - info.ms) / 60_000L
    if (mins < 1) return stringResource(R.string.presence_just_now)
    if (mins < 60) return stringResource(R.string.presence_min, Format.number(mins))
    val hours = mins / 60
    if (hours < 24) return stringResource(R.string.presence_hour, Format.number(hours))
    if (hours < 48 && Format.dayStart(info.ms) == Format.dayStart(now - 86_400_000L)) {
        return stringResource(R.string.presence_yesterday)
    }
    val days = hours / 24
    if (days < 7) return stringResource(R.string.presence_day, Format.number(days))
    return stringResource(R.string.presence_on, Format.shortDate(info.ms, "d MMM"))
}
