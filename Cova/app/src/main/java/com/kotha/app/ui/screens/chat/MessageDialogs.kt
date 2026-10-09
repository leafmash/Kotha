package com.kotha.app.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.util.Format

@Composable
fun EditMessageDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.msg_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= AppConfig.EDIT_MAX) text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                shape = MaterialTheme.shapes.medium,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank() && text.trim() != initial
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun DeleteMessagesDialog(
    count: Int,
    everyoneAllowed: Boolean,
    onDeleteForAll: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDismiss: () -> Unit
) {
    val many = count > 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (many) stringResource(R.string.msg_delete_title_many, Format.number(count))
                else stringResource(R.string.msg_delete_title)
            )
        },
        text = {
            Text(
                text = stringResource(
                    when {
                        everyoneAllowed && many -> R.string.msg_delete_text_mine_many
                        everyoneAllowed -> R.string.msg_delete_text_mine
                        many -> R.string.msg_delete_text_other_many
                        else -> R.string.msg_delete_text_other
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (everyoneAllowed) {
                    TextButton(onClick = onDeleteForAll) {
                        Text(stringResource(R.string.msg_delete_for_all), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDeleteForMe) {
                    Text(
                        text = stringResource(R.string.msg_delete_for_me),
                        color = if (everyoneAllowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}

@Composable
fun MuteDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val now = System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mute_title)) },
        text = {
            Column {
                Text(stringResource(R.string.mute_text), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onPick(now + 8 * 3_600_000L) }) { Text(stringResource(R.string.mute_8h)) }
                TextButton(onClick = { onPick(now + 7 * 24 * 3_600_000L) }) { Text(stringResource(R.string.mute_1w)) }
                TextButton(onClick = { onPick(MUTE_FOREVER) }) { Text(stringResource(R.string.mute_always)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

const val MUTE_FOREVER = 4_102_444_800_000L
