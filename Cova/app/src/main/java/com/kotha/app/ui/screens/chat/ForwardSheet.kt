package com.kotha.app.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.model.Message
import com.kotha.app.ui.UiMessage
import com.kotha.app.ui.components.Avatar
import com.kotha.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(
    messages: List<Message>,
    onDismiss: () -> Unit,
    onResult: (UiMessage) -> Unit,
    viewModel: ForwardViewModel = hiltViewModel()
) {
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    val term = query.trim().lowercase()
    val shown = remember(targets, term) { targets.filter { term.isEmpty() || it.name.lowercase().contains(term) } }
    val maxMessage = UiMessage(R.string.fwd_max, listOf(Format.number(AppConfig.FORWARD_MAX)))

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.fwd_title),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text(stringResource(R.string.fwd_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.fwd_none),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(shown, key = { it.id }) { target ->
                        val on = target.id in picked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    if (on) {
                                        picked = picked - target.id
                                    } else if (picked.size >= AppConfig.FORWARD_MAX) {
                                        onResult(maxMessage)
                                    } else {
                                        picked = picked + target.id
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(name = target.name, photo = target.photo, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = target.name.ifEmpty { stringResource(R.string.common_user) },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Checkbox(checked = on, onCheckedChange = null)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (picked.isEmpty()) {
                        stringResource(R.string.fwd_pick)
                    } else {
                        stringResource(R.string.group_selected, Format.number(picked.size))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        viewModel.send(messages, picked) {
                            onResult(it)
                            onDismiss()
                        }
                    },
                    enabled = picked.isNotEmpty() && !busy
                ) {
                    Text(stringResource(R.string.fwd_send))
                }
            }
        }
    }
}
