package com.kotha.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.kotha.app.R
import com.kotha.app.util.Links

@Composable
fun TermsGateDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
        title = { Text(stringResource(R.string.terms_gate_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.terms_gate_text),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { Links.openLegal(context, "terms") }) {
                        Text(stringResource(R.string.legal_terms))
                    }
                    TextButton(onClick = { Links.openLegal(context, "privacy") }) {
                        Text(stringResource(R.string.legal_privacy))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) { Text(stringResource(R.string.terms_agree)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.signout_ok)) }
        }
    )
}
