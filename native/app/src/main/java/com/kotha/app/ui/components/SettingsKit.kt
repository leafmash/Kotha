package com.kotha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kotha.app.ui.theme.CovaTheme

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, CovaTheme.colors.hairline, shape),
        content = content
    )
}

@Composable
fun SettingsIconTile(icon: ImageVector, danger: Boolean = false) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val tile = if (danger) CovaTheme.colors.missed.copy(alpha = 0.12f) else CovaTheme.colors.accentSoft
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(tile),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = tint
        )
    }
}
