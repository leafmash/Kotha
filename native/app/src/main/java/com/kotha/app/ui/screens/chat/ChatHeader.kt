package com.kotha.app.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.presence.PresenceInfo
import com.kotha.app.ui.components.Avatar
import com.kotha.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    header: HeaderUi,
    menu: ChatMenuUi,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onOpenInfo: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onDeleteConversation: () -> Unit
) {
    val (statusText, live) = statusLabel(header.status)
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        },
        title = {
            Row(
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenInfo),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        actions = {
            if (!menu.group) {
                val callable = menu.peerUid.isNotEmpty() && !menu.blocked
                IconButton(onClick = onVoiceCall, enabled = callable) {
                    Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.call_voice))
                }
                IconButton(onClick = onVideoCall, enabled = callable) {
                    Icon(Icons.Outlined.Videocam, contentDescription = stringResource(R.string.call_video))
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (menu.pinned) R.string.pin_undo else R.string.pin_action)) },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onTogglePin()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (menu.archived) R.string.archive_undo else R.string.archive_action)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (menu.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleArchive()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (menu.muted) R.string.mute_unmute else R.string.mute_action)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (menu.muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleMute()
                    }
                )
                if (!menu.group && menu.peerUid.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(if (menu.blocked) R.string.block_unblock_user else R.string.block_user),
                                color = if (menu.blocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = if (menu.blocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleBlock()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        menuOpen = false
                        onDeleteConversation()
                    }
                )
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
internal fun presenceText(info: PresenceInfo, now: Long): String {
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
