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

fun avatarColor(seed: String): Color = palette[seed.sumOf { it.code } % palette.size]

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
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(avatarColor(name)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .padding(size * 0.05f)
                    .background(CovaTheme.colors.lantern, CircleShape)
            )
        }
    }
}
