package com.kotha.app.ui.screens.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.kotha.app.ui.components.EmptyState
import com.kotha.app.ui.theme.brandBrush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.ui.components.Avatar
import com.kotha.app.ui.components.ConfirmDialog
import com.kotha.app.ui.screens.chat.MuteDialog
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.util.Format
import com.kotha.app.util.StoredText

private enum class ListFilter { All, Unread, Archived }

@Composable
fun ChatListContent(
    groupsOnly: Boolean,
    viewModel: ChatListViewModel,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val base by viewModel.base.collectAsStateWithLifecycle()
    var query by rememberSaveable(groupsOnly) { mutableStateOf("") }
    var filter by rememberSaveable(groupsOnly) { mutableStateOf(ListFilter.All) }
    var actionRow by remember { mutableStateOf<ChatRowUi?>(null) }
    var muteRow by remember { mutableStateOf<ChatRowUi?>(null) }
    var deleteRow by remember { mutableStateOf<ChatRowUi?>(null) }
    val term = query.trim().lowercase()
    val rows = remember(base, term, filter, groupsOnly) {
        val archivedView = filter == ListFilter.Archived && !groupsOnly
        val pinFirst = term.isEmpty() && !archivedView
        base.rows
            .filter { term.isNotEmpty() || archivedView == it.archived }
            .filter {
                when {
                    groupsOnly -> it.group
                    filter == ListFilter.Unread -> it.unread > 0
                    else -> true
                }
            }
            .filter { term.isEmpty() || it.name.lowercase().contains(term) }
            .sortedByDescending { pinFirst && it.pinned }
    }

    actionRow?.let { row ->
        ChatActionsSheet(
            row = row,
            onPin = {
                actionRow = null
                viewModel.togglePin(row.id)
            },
            onMute = {
                actionRow = null
                if (row.muted) viewModel.mute(row.id, 0L) else muteRow = row
            },
            onArchive = {
                actionRow = null
                viewModel.toggleArchive(row.id)
            },
            onDelete = {
                actionRow = null
                deleteRow = row
            },
            onDismiss = { actionRow = null }
        )
    }
    muteRow?.let { row ->
        MuteDialog(
            onPick = {
                muteRow = null
                viewModel.mute(row.id, it)
            },
            onDismiss = { muteRow = null }
        )
    }
    deleteRow?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.chat_delete_title),
            text = stringResource(R.string.chat_delete_text),
            confirmLabel = stringResource(R.string.chat_delete),
            destructive = true,
            onConfirm = {
                deleteRow = null
                viewModel.deleteConversation(row.id)
            },
            onDismiss = { deleteRow = null }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            placeholder = { Text(stringResource(R.string.list_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                }
            }
        )
        if (!groupsOnly) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    label = stringResource(R.string.common_all),
                    selected = filter == ListFilter.All,
                    onClick = { filter = ListFilter.All }
                )
                FilterPill(
                    label = stringResource(R.string.common_unread),
                    selected = filter == ListFilter.Unread,
                    onClick = { filter = ListFilter.Unread }
                )
                FilterPill(
                    label = stringResource(R.string.common_archived) + if (base.archivedUnread) " •" else "",
                    selected = filter == ListFilter.Archived,
                    onClick = { filter = ListFilter.Archived }
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                !base.ready -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                rows.isEmpty() -> {
                    val emptyIcon: ImageVector = when {
                        term.isNotEmpty() -> Icons.Outlined.SearchOff
                        groupsOnly -> Icons.Outlined.GroupAdd
                        filter == ListFilter.Archived -> Icons.Outlined.Inventory2
                        filter == ListFilter.Unread -> Icons.Outlined.MarkChatUnread
                        else -> Icons.Outlined.ChatBubbleOutline
                    }
                    EmptyState(
                        icon = emptyIcon,
                        text = stringResource(
                            when {
                                term.isNotEmpty() -> R.string.list_no_match
                                groupsOnly -> R.string.list_no_groups
                                filter == ListFilter.Archived -> R.string.list_no_archived
                                filter == ListFilter.Unread -> R.string.list_no_unread
                                else -> R.string.list_no_chats
                            }
                        )
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(rows, key = { it.id }) { row ->
                        ChatRow(
                            modifier = Modifier.animateItem(),
                            row = row,
                            onClick = { onOpenChat(row.id) },
                            onLongClick = { actionRow = row }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(brandBrush(), shape)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer, shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(row: ChatRowUi, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val stored = StoredText.display(context, row.lastMessage)
    val preview = buildAnnotatedString {
        when {
            row.draft.isNotEmpty() -> {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                    append(stringResource(R.string.list_draft_prefix))
                }
                append(row.draft)
            }
            row.hiddenLast -> append(stringResource(R.string.block_hidden_message))
            else -> {
                if (row.lastFromMe) {
                    append(stringResource(R.string.list_you_prefix))
                } else if (row.senderName.isNotEmpty()) {
                    append(row.senderName + ": ")
                }
                append(stored)
            }
        }
    }
    val emphasized = row.unread > 0
    val rowShape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .then(
                if (emphasized && !row.muted) {
                    Modifier.background(CovaTheme.colors.accentSoft.copy(alpha = 0.55f), rowShape)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {}
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = row.name, photo = row.photo, size = 54.dp, online = row.online)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name.ifEmpty { stringResource(R.string.common_user) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Format.listTime(row.timeMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                    color = if (emphasized && !row.muted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (row.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.list_pinned_label),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (row.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(count = row.unread, muted = row.muted)
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .height(22.dp)
            .widthIn(min = 22.dp)
            .clip(shape)
            .then(
                if (muted) {
                    Modifier.background(MaterialTheme.colorScheme.outline, shape)
                } else {
                    Modifier.background(brandBrush(), shape)
                }
            )
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else Format.number(count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatActionsSheet(
    row: ChatRowUi,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = row.name, photo = row.photo, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                text = row.name.ifEmpty { stringResource(R.string.common_user) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ActionItem(
            icon = Icons.Outlined.PushPin,
            label = stringResource(if (row.pinned) R.string.pin_undo else R.string.pin_action),
            onClick = onPin
        )
        ActionItem(
            icon = if (row.muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
            label = stringResource(if (row.muted) R.string.mute_unmute else R.string.mute_action),
            onClick = onMute
        )
        ActionItem(
            icon = if (row.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
            label = stringResource(if (row.archived) R.string.archive_undo else R.string.archive_action),
            onClick = onArchive
        )
        ActionItem(
            icon = Icons.Outlined.Delete,
            label = stringResource(R.string.chat_delete),
            onClick = onDelete,
            danger = true
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
