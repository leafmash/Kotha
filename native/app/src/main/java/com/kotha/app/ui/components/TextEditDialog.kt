package com.kotha.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.util.Format

@Composable
fun TextEditDialog(
    title: String,
    initial: String,
    maxLength: Int,
    multiline: Boolean,
    allowEmpty: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val value = text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= maxLength) text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (multiline) 96.dp else 56.dp),
                shape = MaterialTheme.shapes.medium,
                singleLine = !multiline,
                maxLines = if (multiline) 6 else 1,
                supportingText = {
                    Text(Format.number(text.length) + "/" + Format.number(maxLength))
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                enabled = value != initial.trim() && (allowEmpty || value.isNotEmpty())
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
