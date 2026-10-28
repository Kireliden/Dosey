package com.borealis.dosey.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private data class DoseyPalette(
    val background: Color, val surface: Color, val text: Color,
    val accent: Color, val secondary: Color, val dark: Boolean
)

private val palettes = mapOf(
    "frappe" to DoseyPalette(Color(0xFF303446), Color(0xFF414559), Color(0xFFC6D0F5), Color(0xFFBABBF1), Color(0xFF81C8BE), true),
    "latte" to DoseyPalette(Color(0xFFEFF1F5), Color(0xFFCCD0DA), Color(0xFF4C4F69), Color(0xFF8839EF), Color(0xFF179299), false),
    "macchiato" to DoseyPalette(Color(0xFF24273A), Color(0xFF363A4F), Color(0xFFCAD3F5), Color(0xFFB7BDF8), Color(0xFF8BD5CA), true),
    "mocha" to DoseyPalette(Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFFCDD6F4), Color(0xFFB4BEFE), Color(0xFF94E2D5), true),
    "crimson-void" to DoseyPalette(Color(0xFF100D12), Color(0xFF321B26), Color(0xFFF2DCE5), Color(0xFFFF244F), Color(0xFF83C9BE), true),
    "coral-bloom" to DoseyPalette(Color(0xFF241A20), Color(0xFF35222B), Color(0xFFFFE0E4), Color(0xFFFF91A4), Color(0xFF8EC9C1), true),
    "sakura-dreams" to DoseyPalette(Color(0xFFFFF7F5), Color(0xFFF4DCD9), Color(0xFF703744), Color(0xFFD94F70), Color(0xFF3D8581), false),
    "moon-light" to DoseyPalette(Color(0xFFF7F6FF), Color(0xFFDEDEF2), Color(0xFF353650), Color(0xFF777BD1), Color(0xFF4B9997), false),
    "moon-shadow" to DoseyPalette(Color(0xFF10121F), Color(0xFF252840), Color(0xFFE1E3FF), Color(0xFF8588ED), Color(0xFF8DD0CC), true)
)

val DoseyThemeNames = listOf("frappe", "latte", "macchiato", "mocha", "crimson-void", "coral-bloom", "sakura-dreams", "moon-light", "moon-shadow")

fun doseyThemeDisplayName(id: String): String = when (id) {
    "frappe" -> "Catppuccin Frappé"
    "latte" -> "Catppuccin Latte"
    "macchiato" -> "Catppuccin Macchiato"
    "mocha" -> "Catppuccin Mocha"
    "crimson-void" -> "Crimson Void"
    "coral-bloom" -> "Coral Bloom"
    "sakura-dreams" -> "Sakura Dreams"
    "moon-light" -> "Moon Light"
    "moon-shadow" -> "Moon Shadow"
    else -> "Catppuccin Frappé"
}

@Composable
fun DoseyTheme(themeName: String = "frappe", content: @Composable () -> Unit) {
    val palette = palettes[themeName] ?: palettes.getValue("frappe")
    val colorScheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = if (themeName == "crimson-void") Color(0xFF100D12) else Color(0xFF1E1E2E),
            secondary = palette.secondary,
            tertiary = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.background,
            onSurface = palette.text,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.text.copy(alpha = 0.78f),
            outline = palette.text.copy(alpha = 0.32f)
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.secondary,
            tertiary = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.background,
            onSurface = palette.text,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.text.copy(alpha = 0.78f),
            outline = palette.text.copy(alpha = 0.32f)
        )
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
