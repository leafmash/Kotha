package com.kotha.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.ui.components.Avatar
import com.kotha.app.ui.components.ConfirmDialog
import com.kotha.app.ui.components.ReportSheet
import com.kotha.app.ui.screens.chat.presenceText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val presence by viewModel.presence.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    val name = profile?.name.orEmpty().ifEmpty { stringResource(R.string.common_user) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Avatar(
                name = profile?.name.orEmpty(),
                photo = profile?.photo.orEmpty(),
                size = 128.dp,
                online = presence?.info?.online == true
            )
            Spacer(Modifier.height(16.dp))
            Text(text = name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            presence?.let {
                Text(
                    text = presenceText(it.info, it.now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.info.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val bio = profile?.bio.orEmpty().trim()
            if (bio.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.profile_about),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = bio, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (!blocked) {
                Button(
                    onClick = { viewModel.openDirect()?.let(onOpenChat) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.user_message))
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (blocked) viewModel.unblock() else confirmBlock = true },
                    modifier = Modifier.weight(1f),
                    colors = if (blocked) {
                        ButtonDefaults.outlinedButtonColors()
                    } else {
                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    }
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(if (blocked) R.string.user_unblock else R.string.user_block))
                }
                OutlinedButton(
                    onClick = { showReport = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.report_user))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmBlock) {
        ConfirmDialog(
            title = stringResource(R.string.block_confirm_title, name),
            text = stringResource(R.string.block_confirm_text),
            confirmLabel = stringResource(R.string.block_action),
            destructive = true,
            onConfirm = {
                confirmBlock = false
                viewModel.block()
            },
            onDismiss = { confirmBlock = false }
        )
    }

    if (showReport) {
        ReportSheet(
            title = stringResource(R.string.report_title_user, name),
            showBlock = !blocked,
            onSubmit = { reason, note, alsoBlock ->
                showReport = false
                viewModel.report(reason, note, alsoBlock)
            },
            onDismiss = { showReport = false }
        )
    }
}
