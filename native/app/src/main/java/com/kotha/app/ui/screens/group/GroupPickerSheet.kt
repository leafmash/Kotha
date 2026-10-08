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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kotha.app.R
import com.kotha.app.data.model.UserProfile
import com.kotha.app.ui.components.Avatar
import com.kotha.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPickerSheet(
    mode: PickerMode,
    chatId: String,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
    onAdded: () -> Unit,
    viewModel: GroupPickerViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val find by viewModel.find.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val add = mode == PickerMode.Add
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) viewModel.setCover(it)
    }

    LaunchedEffect(mode, chatId) { viewModel.start(mode, chatId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) }
    }

    val ready = if (add) form.picked.isNotEmpty() else form.name.trim().isNotEmpty() && form.picked.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.92f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = stringResource(if (add) R.string.group_add_title else R.string.group_new),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                if (!add) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(role = Role.Button) {
                                    coverPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (form.cover != null) {
                                AsyncImage(
                                    model = form.cover,
                                    contentDescription = stringResource(R.string.group_cover),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = stringResource(R.string.group_cover),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = viewModel::setName,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            label = { Text(stringResource(R.string.group_name)) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = form.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.group_find_contact)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Search)
                )
                FindResult(find = find, picked = form.picked, onPick = viewModel::pickFound)
                if (form.picked.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PickedStrip(
                        ids = form.picked,
                        lookup = { id -> contacts.firstOrNull { it.uid == id } ?: form.extras[id] },
                        onRemove = viewModel::toggle
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.group_your_contacts),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (contacts.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.group_no_contacts),
                                modifier = Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(contacts, key = { it.uid }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { viewModel.toggle(user.uid) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(name = user.name, photo = user.photo, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = user.name.ifEmpty { stringResource(R.string.common_user) },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Checkbox(checked = user.uid in form.picked, onCheckedChange = null)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (form.picked.isEmpty()) {
                            stringResource(R.string.group_pick_members)
                        } else {
                            stringResource(R.string.group_selected, Format.number(form.picked.size))
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = { viewModel.submit(onCreated, onAdded) },
                        enabled = ready && !busy
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(if (add) R.string.group_add else R.string.group_create))
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun FindResult(find: PickFind, picked: List<String>, onPick: (UserProfile) -> Unit) {
    when (find) {
        PickFind.Idle -> Unit
        PickFind.Invalid -> Hint(R.string.find_invalid)
        PickFind.Loading -> Hint(R.string.find_loading)
        PickFind.Self -> Hint(R.string.find_self)
        PickFind.None -> Hint(R.string.find_none)
        PickFind.Failed -> Hint(R.string.find_error)
        PickFind.Blocked -> Hint(R.string.block_in_group)
        PickFind.AlreadyIn -> Hint(R.string.group_already_in)
        is PickFind.Found -> {
            val already = find.profile.uid in picked
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !already) { onPick(find.profile) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(name = find.profile.name, photo = find.profile.photo, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = find.profile.name.ifEmpty { stringResource(R.string.common_user) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(if (already) R.string.find_added else R.string.find_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun Hint(textRes: Int) {
    Text(
        text = stringResource(textRes),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PickedStrip(ids: List<String>, lookup: (String) -> UserProfile?, onRemove: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ids, key = { it }) { id ->
            val user = lookup(id)
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .clickable(role = Role.Button) { onRemove(id) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Avatar(name = user?.name.orEmpty(), photo = user?.photo.orEmpty(), size = 48.dp)
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                            .padding(2.dp)
                    )
                }
                Text(
                    text = user?.name.orEmpty().ifEmpty { stringResource(R.string.common_user) },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
