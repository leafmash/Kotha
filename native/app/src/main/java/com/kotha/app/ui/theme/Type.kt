package com.kotha.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

val CovaTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp)
)

val BrandFont = FontFamily.Serif
