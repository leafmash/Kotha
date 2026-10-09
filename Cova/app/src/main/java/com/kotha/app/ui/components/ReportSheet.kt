package com.kotha.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.report.ReportReasons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    title: String,
    showBlock: Boolean,
    onSubmit: (reason: String, note: String, alsoBlock: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var alsoBlock by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.report_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ReportReasons.forEach { key ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { reason = key }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = reason == key, onClick = null)
                    Text(
                        text = stringResource(reasonLabel(key)),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= AppConfig.REPORT_NOTE_MAX) note = it },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                minLines = 2,
                maxLines = 4,
                placeholder = { Text(stringResource(R.string.report_note_hint)) }
            )
            if (showBlock) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Checkbox) { alsoBlock = !alsoBlock }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = alsoBlock, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.report_also_block),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSubmit(reason, note, showBlock && alsoBlock) },
                enabled = reason.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.report_submit))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun reasonLabel(key: String): Int = when (key) {
    "spam" -> R.string.report_reason_spam
    "harassment" -> R.string.report_reason_harassment
    "hate" -> R.string.report_reason_hate
    "sexual" -> R.string.report_reason_sexual
    "violence" -> R.string.report_reason_violence
    "scam" -> R.string.report_reason_scam
    else -> R.string.report_reason_other
}
