package com.kotha.app.ui.screens.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            shape = CircleShape,
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
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == ListFilter.All,
                    onClick = { filter = ListFilter.All },
                    label = { Text(stringResource(R.string.common_all)) }
                )
                FilterChip(
                    selected = filter == ListFilter.Unread,
                    onClick = { filter = ListFilter.Unread },
                    label = { Text(stringResource(R.string.common_unread)) }
                )
                FilterChip(
                    selected = filter == ListFilter.Archived,
                    onClick = { filter = ListFilter.Archived },
                    label = {
                        Text(
                            text = stringResource(R.string.common_archived) + if (base.archivedUnread) " •" else ""
                        )
                    }
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                !base.ready -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                rows.isEmpty() -> Text(
                    text = stringResource(
                        when {
                            term.isNotEmpty() -> R.string.list_no_match
                            groupsOnly -> R.string.list_no_groups
                            filter == ListFilter.Archived -> R.string.list_no_archived
                            filter == ListFilter.Unread -> R.string.list_no_unread
                            else -> R.string.list_no_chats
                        }
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it.id }) { row ->
                        ChatRow(row = row, onClick = { onOpenChat(row.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(row: ChatRowUi, onClick: () -> Unit) {
    val context = LocalContext.current
    val stored = StoredText.display(context, row.lastMessage)
    val preview = buildAnnotatedString {
        when {
            row.draft.isNotEmpty() -> {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = row.name, photo = row.photo, size = 52.dp, online = row.online)
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
                    style = MaterialTheme.typography.bodySmall,
                    color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Badge(
                        containerColor = if (row.muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(if (row.unread > 99) "99+" else Format.number(row.unread))
                    }
                }
            }
        }
    }
}
