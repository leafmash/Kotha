package com.kotha.app.ui.screens.chat.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kotha.app.R
import com.kotha.app.data.model.UploadStage
import com.kotha.app.data.model.UploadUi

@Composable
fun UploadButton(
    upload: UploadUi,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    if (upload.stage == UploadStage.Failed) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundAction(
                icon = { Icon(Icons.Filled.Refresh, stringResource(R.string.upload_retry), tint = tint) },
                background = background,
                size = size,
                onClick = onRetry
            )
            RoundAction(
                icon = { Icon(Icons.Filled.Close, stringResource(R.string.upload_cancel), tint = tint) },
                background = background,
                size = size,
                onClick = onCancel
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .clickable(role = Role.Button, onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            if (upload.stage == UploadStage.Uploading && upload.progress > 0f) {
                CircularProgressIndicator(
                    progress = { upload.progress },
                    modifier = Modifier.size(size - 8.dp),
                    color = tint,
                    strokeWidth = 3.dp,
                    trackColor = tint.copy(alpha = 0.25f)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(size - 8.dp),
                    color = tint,
                    strokeWidth = 3.dp,
                    trackColor = tint.copy(alpha = 0.25f)
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.upload_cancel),
                modifier = Modifier.size(18.dp),
                tint = tint
            )
        }
    }
}

@Composable
private fun RoundAction(
    icon: @Composable () -> Unit,
    background: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
fun UploadOverlay(
    upload: UploadUi,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        UploadButton(
            upload = upload,
            onCancel = onCancel,
            onRetry = onRetry,
            tint = Color.White,
            background = Color.Black.copy(alpha = 0.5f)
        )
    }
}
