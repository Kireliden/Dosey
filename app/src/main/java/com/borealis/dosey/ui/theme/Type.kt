package com.borealis.dosey.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.borealis.dosey.R

private val Ubuntu = FontFamily(
    Font(R.font.ubuntu_regular, FontWeight.Normal),
    Font(R.font.ubuntu_medium, FontWeight.Medium),
    Font(R.font.ubuntu_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Ubuntu, fontWeight = FontWeight.Medium)
)
