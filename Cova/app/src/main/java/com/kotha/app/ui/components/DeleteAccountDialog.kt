package com.kotha.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.ui.screens.home.DeleteAccountViewModel
import com.kotha.app.ui.screens.home.DeleteMethod
import com.kotha.app.util.AppRestart
import kotlinx.coroutines.delay

@Composable
fun DeleteAccountDialog(onDismiss: () -> Unit, viewModel: DeleteAccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.start() }

    val onDeleted: suspend () -> Unit = {
        Toast.makeText(context, context.getString(R.string.delete_done), Toast.LENGTH_LONG).show()
        delay(1_200)
        AppRestart.restart(context)
    }
    val confirm = { method: DeleteMethod -> viewModel.confirm(method, context, onDeleted) }
    val locked = state.lockLeft > 0
    val baseLabel = stringResource(
        if (state.method == DeleteMethod.Password) R.string.delete_confirm else R.string.delete_confirm_google
    )
    val label = when {
        state.busy -> stringResource(R.string.delete_working)
        locked -> "$baseLabel (${state.lockLeft})"
        else -> baseLabel
    }

    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !state.busy, dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.delete_text) + " " + stringResource(
                        if (state.method == DeleteMethod.Password) R.string.delete_text_password else R.string.delete_text_google
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.method == DeleteMethod.Password) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPassword,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        label = { Text(stringResource(R.string.delete_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { confirm(DeleteMethod.Password) })
                    )
                }
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.canUseGoogle) {
                    TextButton(
                        onClick = { confirm(DeleteMethod.Google) },
                        enabled = !state.busy && !locked
                    ) {
                        Text(stringResource(R.string.delete_use_google))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirm(state.method) },
                enabled = !state.busy && !locked
            ) {
                Text(label, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
