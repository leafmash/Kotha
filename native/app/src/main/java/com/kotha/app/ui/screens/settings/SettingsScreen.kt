package com.kotha.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.data.prefs.ThemeMode
import com.kotha.app.ui.components.Avatar
import com.kotha.app.ui.components.ChoiceDialog
import com.kotha.app.ui.components.ChoiceOption
import com.kotha.app.ui.components.ConfirmDialog
import com.kotha.app.ui.components.DeleteAccountDialog
import com.kotha.app.ui.components.SettingsCard
import com.kotha.app.ui.components.SettingsIconTile
import com.kotha.app.ui.components.SettingsSectionLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.util.AppLanguage
import com.kotha.app.util.Format
import com.kotha.app.util.Links

private val FontScales = listOf(0.9f, 1f, 1.15f, 1.3f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenBlocked: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val receipts by viewModel.readReceipts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf("") }
    val name = profile?.name.orEmpty().ifEmpty { stringResource(R.string.common_user) }
    val languageCode = AppLanguage.code()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            val profileShape = RoundedCornerShape(28.dp)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(profileShape)
                    .background(CovaTheme.colors.accentSoft)
                    .border(1.dp, CovaTheme.colors.hairline, profileShape)
                    .clickable(role = Role.Button, onClick = onOpenProfile)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(name = name, photo = profile?.photo.orEmpty(), size = 68.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (viewModel.email.isNotEmpty()) {
                        Text(
                            text = viewModel.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            SettingsSectionLabel(stringResource(R.string.settings_preferences))
            SettingsCard {
            SettingRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                value = if (languageCode == "bn") "বাংলা" else "English"
            ) { dialog = "language" }
            SettingRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.settings_theme),
                value = stringResource(themeLabel(theme))
            ) { dialog = "theme" }
            SettingRow(
                icon = Icons.Filled.TextFields,
                title = stringResource(R.string.settings_font_size),
                value = stringResource(fontLabel(fontScale))
            ) { dialog = "font" }
            }
            SettingsSectionLabel(stringResource(R.string.settings_privacy))
            SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { viewModel.setReadReceipts(!receipts) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconTile(Icons.Filled.DoneAll)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_read_receipts), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.settings_read_receipts_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = receipts, onCheckedChange = null)
            }
            SettingRow(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.settings_blocked),
                value = if (blockedCount > 0) Format.number(blockedCount) else "",
                onClick = onOpenBlocked
            )
            }
            NotificationSettingsSection()
            SettingsSectionLabel(stringResource(R.string.settings_about))
            SettingsCard {
            SettingRow(
                icon = Icons.AutoMirrored.Filled.Notes,
                title = stringResource(R.string.legal_terms),
                value = ""
            ) { Links.openLegal(context, "terms") }
            SettingRow(
                icon = Icons.Filled.PrivacyTip,
                title = stringResource(R.string.legal_privacy),
                value = ""
            ) { Links.openLegal(context, "privacy") }
            }
            SettingsSectionLabel(stringResource(R.string.settings_account))
            SettingsCard {
            SettingRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = stringResource(R.string.menu_sign_out),
                value = ""
            ) { dialog = "signout" }
            SettingRow(
                icon = Icons.Filled.DeleteForever,
                title = stringResource(R.string.settings_delete_account),
                value = "",
                danger = true
            ) { dialog = "delete" }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        "language" -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            text = stringResource(R.string.settings_language_text),
            options = listOf(ChoiceOption("en", "English"), ChoiceOption("bn", "বাংলা")),
            selected = languageCode,
            onSelect = {
                dialog = ""
                if (it != languageCode) AppLanguage.set(it)
            },
            onDismiss = { dialog = "" }
        )
        "theme" -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { ChoiceOption(it, stringResource(themeLabel(it))) },
            selected = theme,
            onSelect = {
                dialog = ""
                viewModel.setTheme(it)
            },
            onDismiss = { dialog = "" }
        )
        "font" -> ChoiceDialog(
            title = stringResource(R.string.settings_font_size),
            options = FontScales.map { ChoiceOption(it, stringResource(fontLabel(it))) },
            selected = fontScale,
            onSelect = {
                dialog = ""
                viewModel.setFontScale(it)
            },
            onDismiss = { dialog = "" }
        )
        "signout" -> ConfirmDialog(
            title = stringResource(R.string.signout_title),
            text = stringResource(R.string.signout_text),
            confirmLabel = stringResource(R.string.signout_ok),
            destructive = false,
            onConfirm = {
                dialog = ""
                viewModel.signOut()
            },
            onDismiss = { dialog = "" }
        )
        "delete" -> DeleteAccountDialog(onDismiss = { dialog = "" })
    }
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

private fun fontLabel(scale: Float): Int = when (scale) {
    0.9f -> R.string.font_small
    1.15f -> R.string.font_large
    1.3f -> R.string.font_huge
    else -> R.string.font_default
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon = icon, danger = danger)
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
