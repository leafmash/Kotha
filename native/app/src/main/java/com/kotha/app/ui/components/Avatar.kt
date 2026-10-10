package com.kotha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kotha.app.ui.theme.CovaTheme

private val palette = listOf(
    Color(0xFFFF8A5C),
    Color(0xFFFF4F8B),
    Color(0xFF8B5CF6),
    Color(0xFF22B8CF),
    Color(0xFFF59E0B),
    Color(0xFF10B981)
)

private val paletteEnd = listOf(
    Color(0xFFFF5E78),
    Color(0xFFD62F9A),
    Color(0xFF5B4BF5),
    Color(0xFF2F7DE1),
    Color(0xFFEF6B2B),
    Color(0xFF0E9AA7)
)

private fun avatarIndex(seed: String): Int = seed.sumOf { it.code } % palette.size

fun avatarColor(seed: String): Color = palette[avatarIndex(seed)]

fun avatarBrush(seed: String): Brush {
    val index = avatarIndex(seed)
    return Brush.linearGradient(listOf(palette[index], paletteEnd[index]))
}

@Composable
fun Avatar(
    name: String,
    photo: String,
    size: Dp,
    modifier: Modifier = Modifier,
    online: Boolean = false
) {
    Box(modifier = modifier.size(size)) {
        if (photo.isNotBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(avatarBrush(name)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = (size.value * 0.40f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .padding(size * 0.055f)
                    .background(CovaTheme.colors.success, CircleShape)
            )
        }
    }
}
