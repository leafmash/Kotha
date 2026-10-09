package com.kotha.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

val CovaTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)
