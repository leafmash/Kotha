package com.kotha.app.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.ui.components.Avatar
import com.kotha.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    onBack: () -> Unit,
    viewModel: MyProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val account = remember { viewModel.account() }
    val name = profile?.name.orEmpty().ifEmpty { viewModel.displayName }.ifEmpty { stringResource(R.string.common_user) }
    val bio = profile?.bio.orEmpty().trim()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) viewModel.changePhoto(it)
    }
    val pickPhoto = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

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
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { pickPhoto() }
                ) {
                    Avatar(name = name, photo = profile?.photo.orEmpty(), size = 128.dp)
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.common_change_photo),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(10.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(text = name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.profile_edit))
                }
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        InfoRow(stringResource(R.string.profile_name), name)
                        if (bio.isNotEmpty()) {
                            HorizontalDivider()
                            InfoRow(stringResource(R.string.profile_about), bio)
                        }
                        if (account.email.isNotEmpty()) {
                            HorizontalDivider()
                            InfoRow(stringResource(R.string.profile_email), account.email)
                            HorizontalDivider()
                            InfoRow(
                                stringResource(R.string.profile_email_status),
                                stringResource(if (account.verified) R.string.profile_verified else R.string.profile_not_verified)
                            )
                        }
                        val methods = buildList {
                            if (account.passwordSignIn) add(stringResource(R.string.profile_method_password))
                            if (account.googleSignIn) add("Google")
                        }
                        if (methods.isNotEmpty()) {
                            HorizontalDivider()
                            InfoRow(stringResource(R.string.profile_method), methods.joinToString(" · "))
                        }
                        if (account.createdMs > 0) {
                            HorizontalDivider()
                            InfoRow(
                                stringResource(R.string.profile_joined),
                                Format.shortDate(account.createdMs, "yMMMMd")
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (editing) {
        EditProfileSheet(
            initialName = name,
            initialBio = bio,
            busy = busy,
            onSave = { newName, newBio ->
                viewModel.save(newName, newBio) { editing = false }
            },
            onDismiss = { editing = false }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    initialName: String,
    initialBio: String,
    busy: Boolean,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var bio by rememberSaveable { mutableStateOf(initialBio) }
    val cleanName = name.replace(Regex("\\s+"), " ").trim()
    val cleanBio = bio.replace(Regex("[ \\t]+"), " ").replace(Regex("\\s*\\n\\s*"), "\n").trim()
    val changed = cleanName != initialName || cleanBio != initialBio
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(text = stringResource(R.string.profile_edit), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= AppConfig.NAME_MAX) name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                label = { Text(stringResource(R.string.profile_name_ph)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= AppConfig.BIO_MAX) bio = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = MaterialTheme.shapes.medium,
                label = { Text(stringResource(R.string.profile_about_ph)) },
                supportingText = {
                    Text(Format.number(bio.length) + "/" + Format.number(AppConfig.BIO_MAX))
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Text(
                text = stringResource(R.string.profile_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(cleanName, cleanBio) },
                    enabled = cleanName.isNotEmpty() && changed && !busy
                ) {
                    Text(stringResource(R.string.profile_save))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
