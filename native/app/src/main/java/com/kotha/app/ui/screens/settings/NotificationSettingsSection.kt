package com.kotha.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kotha.app.R
import com.kotha.app.notify.NotificationIds
import com.kotha.app.util.BatteryOptimization

@Composable
fun NotificationSettingsSection() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh += 1 }
    val enabled = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val unrestricted = remember(refresh) { BatteryOptimization.isIgnoring(context) }
    val autostart = remember { BatteryOptimization.hasAutostartSettings() }

    Text(
        text = stringResource(R.string.settings_notifications),
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    NotificationRow(
        icon = Icons.Filled.Notifications,
        title = stringResource(R.string.settings_notifications_app),
        value = stringResource(if (enabled) R.string.notif_on else R.string.notif_off)
    ) { BatteryOptimization.openAppNotificationSettings(context) }
    NotificationRow(
        icon = Icons.Filled.NotificationsActive,
        title = stringResource(R.string.settings_notifications_messages),
        value = ""
    ) { BatteryOptimization.openChannelSettings(context, NotificationIds.MESSAGE_CHANNEL) }
    NotificationRow(
        icon = Icons.Filled.BatteryChargingFull,
        title = stringResource(R.string.settings_battery),
        value = stringResource(if (unrestricted) R.string.settings_battery_unrestricted else R.string.settings_battery_optimized)
    ) {
        if (unrestricted) BatteryOptimization.openBatterySettings(context) else BatteryOptimization.requestExemption(context)
    }
    if (!BatteryOptimization.canUseFullScreenIntent(context)) {
        NotificationRow(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.call_fsi_title),
            value = stringResource(R.string.call_fsi_text)
        ) { BatteryOptimization.openFullScreenIntentSettings(context) }
    }
    if (autostart) {
        NotificationRow(
            icon = Icons.Filled.PowerSettingsNew,
            title = stringResource(R.string.settings_autostart),
            value = ""
        ) { BatteryOptimization.openAutostartSettings(context) }
    }
}

@Composable
private fun NotificationRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
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
