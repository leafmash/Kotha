package com.kotha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.ui.theme.brandBrush
import com.kotha.app.ui.theme.pressScale

@Composable
fun ActionOrb(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val brush = brandBrush()
    val glow = CovaTheme.colors.glow
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(46.dp)
            .pressScale(source)
            .shadow(8.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .clip(CircleShape)
            .background(brush)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
