package com.kotha.app.ui.screens.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.ui.components.Avatar
import com.kotha.app.ui.components.ConfirmDialog
import com.kotha.app.ui.components.ReportSheet
import com.kotha.app.ui.components.TextEditDialog
import com.kotha.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenUser: (String, String) -> Unit,
    onLeft: () -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editName by rememberSaveable { mutableStateOf(false) }
    var editDesc by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var memberId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) viewModel.changePhoto(it)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) }
    }

    val group = chat
    if (group == null || !group.members.contains(viewModel.uid)) {
        LaunchedEffect(group == null) { if (group != null) onBack() }
        Scaffold(topBar = { InfoTopBar(onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }

    val admin = group.isAdmin(viewModel.uid)
    val adminIds = group.adminIds()
    val members = group.members.sortedByDescending { adminIds.contains(it) }

    Scaffold(
        topBar = { InfoTopBar(onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(modifier = Modifier.weight(1f).navigationBarsPadding()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .clickable(enabled = admin, role = Role.Button) {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Avatar(name = group.name, photo = group.photo, size = 112.dp)
                            if (admin) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = stringResource(R.string.ginfo_change_photo),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(8.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = admin) { editName = true }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (admin) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.ginfo_edit_name),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.chat_members, Format.number(members.size)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    val text = group.description.trim()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = admin) { editDesc = true }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ginfo_description),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when {
                                text.isNotEmpty() -> text
                                admin -> stringResource(R.string.ginfo_add_description)
                                else -> stringResource(R.string.ginfo_no_description)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (text.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    HorizontalDivider()
                }
                if (admin) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Switch) { viewModel.toggleAdminOnly(!group.adminOnly) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ginfo_admin_only),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.ginfo_admin_only_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = group.adminOnly, onCheckedChange = null)
                        }
                        HorizontalDivider()
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.ginfo_members_label, Format.number(members.size)),
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (admin) {
                    item {
                        ActionRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            label = stringResource(R.string.ginfo_add_members),
                            color = MaterialTheme.colorScheme.primary
                        ) { showAdd = true }
                    }
                }
                items(members, key = { it }) { id ->
                    val user = users[id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = id != viewModel.uid) { memberId = id }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = user?.name.orEmpty(), photo = user?.photo.orEmpty(), size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = user?.name.orEmpty().ifEmpty { stringResource(R.string.common_user) },
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (id == viewModel.uid) {
                            Text(
                                text = "  (" + stringResource(R.string.common_you) + ")",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (adminIds.contains(id)) {
                            Text(
                                text = stringResource(R.string.group_admin),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    ActionRow(
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        label = stringResource(R.string.group_leave),
                        color = MaterialTheme.colorScheme.error
                    ) { confirmLeave = true }
                    ActionRow(
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        label = stringResource(R.string.report_group),
                        color = MaterialTheme.colorScheme.error
                    ) { showReport = true }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (editName) {
        TextEditDialog(
            title = stringResource(R.string.ginfo_edit_name),
            initial = group.name,
            maxLength = AppConfig.NAME_MAX,
            multiline = false,
            allowEmpty = false,
            onSave = {
                editName = false
                viewModel.rename(it)
            },
            onDismiss = { editName = false }
        )
    }

    if (editDesc) {
        TextEditDialog(
            title = stringResource(R.string.ginfo_edit_desc),
            initial = group.description,
            maxLength = AppConfig.GROUP_DESC_MAX,
            multiline = true,
            allowEmpty = true,
            onSave = {
                editDesc = false
                viewModel.describe(it)
            },
            onDismiss = { editDesc = false }
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.group_leave_title),
            text = stringResource(R.string.group_leave_text),
            confirmLabel = stringResource(R.string.group_leave),
            destructive = true,
            onConfirm = {
                confirmLeave = false
                viewModel.leave(onLeft)
            },
            onDismiss = { confirmLeave = false }
        )
    }

    confirmRemove?.let { id ->
        val name = users[id]?.name.orEmpty().ifEmpty { stringResource(R.string.common_user) }
        ConfirmDialog(
            title = stringResource(R.string.member_remove_title, name),
            text = stringResource(R.string.member_remove_text),
            confirmLabel = stringResource(R.string.member_remove),
            destructive = true,
            onConfirm = {
                confirmRemove = null
                viewModel.remove(id)
            },
            onDismiss = { confirmRemove = null }
        )
    }

    if (showAdd) {
        GroupPickerSheet(
            mode = PickerMode.Add,
            chatId = viewModel.chatId,
            onDismiss = { showAdd = false },
            onCreated = {},
            onAdded = { showAdd = false }
        )
    }

    if (showReport) {
        ReportSheet(
            title = stringResource(R.string.report_title_group),
            showBlock = false,
            onSubmit = { reason, note, alsoBlock ->
                showReport = false
                viewModel.report(reason, note, alsoBlock)
            },
            onDismiss = { showReport = false }
        )
    }

    memberId?.let { id ->
        val user = users[id]
        val name = user?.name.orEmpty().ifEmpty { stringResource(R.string.common_user) }
        val isAdminTarget = adminIds.contains(id)
        val isOwner = group.admin == id
        val canMessage = user != null && !viewModel.blocked.collectAsStateWithLifecycle().value.contains(id)
        ModalBottomSheet(onDismissRequest = { memberId = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                ActionRow(
                    icon = {},
                    label = stringResource(R.string.member_view_profile),
                    color = MaterialTheme.colorScheme.onSurface
                ) {
                    memberId = null
                    onOpenUser(id, viewModel.chatId)
                }
                if (canMessage) {
                    ActionRow(
                        icon = {},
                        label = stringResource(R.string.member_message, name),
                        color = MaterialTheme.colorScheme.onSurface
                    ) {
                        memberId = null
                        viewModel.openDirect(id)?.let(onOpenChat)
                    }
                }
                if (admin && !isOwner) {
                    ActionRow(
                        icon = {},
                        label = stringResource(if (isAdminTarget) R.string.member_remove_admin else R.string.member_make_admin),
                        color = MaterialTheme.colorScheme.onSurface
                    ) {
                        memberId = null
                        viewModel.setAdmin(id, !isAdminTarget)
                    }
                    ActionRow(
                        icon = {},
                        label = stringResource(R.string.member_remove),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        memberId = null
                        confirmRemove = id
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.ginfo_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
