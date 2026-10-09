package com.kotha.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.kotha.app.R
import com.kotha.app.util.BatteryOptimization

private enum class OnboardingStep { None, Permission, Battery, Autostart }

@Composable
fun NotificationOnboarding(
    enabled: Boolean,
    onPermissionResult: () -> Unit,
    viewModel: NotificationOnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var started by rememberSaveable { mutableStateOf(false) }
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.None.name) }
    val step = OnboardingStep.valueOf(stepName)

    fun advance(from: OnboardingStep) {
        stepName = nextStep(viewModel, context, from).name
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionResult()
        advance(OnboardingStep.Permission)
    }

    LaunchedEffect(enabled) {
        if (enabled && !started) {
            started = true
            advance(OnboardingStep.None)
        }
    }

    when (step) {
        OnboardingStep.None -> Unit
        OnboardingStep.Permission -> PromptDialog(
            title = stringResource(R.string.notif_permission_title),
            text = stringResource(R.string.notif_permission_text),
            confirmLabel = stringResource(R.string.notif_permission_allow),
            dismissLabel = stringResource(R.string.notif_not_now),
            onConfirm = {
                viewModel.permissionAsked()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                viewModel.permissionAsked()
                advance(OnboardingStep.Permission)
            }
        )
        OnboardingStep.Battery -> PromptDialog(
            title = stringResource(R.string.battery_title),
            text = stringResource(R.string.battery_text),
            confirmLabel = stringResource(R.string.battery_allow),
            dismissLabel = stringResource(R.string.notif_not_now),
            onConfirm = {
                viewModel.batteryPrompted()
                BatteryOptimization.requestExemption(context)
                advance(OnboardingStep.Battery)
            },
            onDismiss = {
                viewModel.batteryPrompted()
                advance(OnboardingStep.Battery)
            }
        )
        OnboardingStep.Autostart -> PromptDialog(
            title = stringResource(R.string.autostart_title),
            text = stringResource(R.string.autostart_text),
            confirmLabel = stringResource(R.string.autostart_open),
            dismissLabel = stringResource(R.string.notif_not_now),
            onConfirm = {
                viewModel.autostartPrompted()
                BatteryOptimization.openAutostartSettings(context)
                advance(OnboardingStep.Autostart)
            },
            onDismiss = {
                viewModel.autostartPrompted()
                advance(OnboardingStep.Autostart)
            }
        )
    }
}

private fun nextStep(
    viewModel: NotificationOnboardingViewModel,
    context: android.content.Context,
    from: OnboardingStep
): OnboardingStep {
    if (from == OnboardingStep.None && needsPermission(viewModel, context)) return OnboardingStep.Permission
    val beforeBattery = from == OnboardingStep.None || from == OnboardingStep.Permission
    if (beforeBattery && !viewModel.isBatteryPrompted() && !BatteryOptimization.isIgnoring(context)) {
        return OnboardingStep.Battery
    }
    val beforeAutostart = from != OnboardingStep.Autostart
    if (beforeAutostart && !viewModel.isAutostartPrompted() && BatteryOptimization.hasAutostartSettings()) {
        return OnboardingStep.Autostart
    }
    return OnboardingStep.None
}

private fun needsPermission(viewModel: NotificationOnboardingViewModel, context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    if (viewModel.isPermissionAsked()) return false
    val state = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    return state != android.content.pm.PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PromptDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
