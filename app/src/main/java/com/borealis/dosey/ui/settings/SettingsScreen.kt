package com.borealis.dosey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.borealis.dosey.ui.theme.DoseyThemeNames
import com.borealis.dosey.ui.theme.doseyThemeDisplayName

private data class ThemePreview(
    val id: String,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val text: Color,
    val dark: Boolean
)

private val previews = listOf(
    ThemePreview("frappe", Color(0xFF303446), Color(0xFF414559), Color(0xFFBABBF1), Color(0xFFC6D0F5), true),
    ThemePreview("latte", Color(0xFFEFF1F5), Color(0xFFCCD0DA), Color(0xFF8839EF), Color(0xFF4C4F69), false),
    ThemePreview("macchiato", Color(0xFF24273A), Color(0xFF363A4F), Color(0xFFB7BDF8), Color(0xFFCAD3F5), true),
    ThemePreview("mocha", Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFFB4BEFE), Color(0xFFCDD6F4), true),
    ThemePreview("crimson-void", Color(0xFF100D12), Color(0xFF321B26), Color(0xFFFF244F), Color(0xFFF2DCE5), true),
    ThemePreview("coral-bloom", Color(0xFF241A20), Color(0xFF35222B), Color(0xFFFF91A4), Color(0xFFFFE0E4), true),
    ThemePreview("sakura-dreams", Color(0xFFFFF7F5), Color(0xFFF4DCD9), Color(0xFFD94F70), Color(0xFF703744), false),
    ThemePreview("moon-light", Color(0xFFF7F6FF), Color(0xFFDEDEF2), Color(0xFF777BD1), Color(0xFF353650), false),
    ThemePreview("moon-shadow", Color(0xFF10121F), Color(0xFF252840), Color(0xFF8588ED), Color(0xFFE1E3FF), true)
)

@Composable
fun SettingsScreen(
    section: String,
    onSectionChange: (String) -> Unit,
    selectedTheme: String,
    onThemeChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("user" to "User", "appearance" to "Appearance", "data" to "Data").forEach { (id, label) ->
                val selected = section == id
                TextButton(
                    onClick = { onSectionChange(id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        when (section) {
            "appearance" -> AppearanceSettings(selectedTheme = selectedTheme, onThemeChange = onThemeChange)
            "user" -> {
                Text("User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your Dosey profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Profile and personalisation options will live here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your data stays yours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Backup, export, import and storage options will live here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(selectedTheme: String, onThemeChange: (String) -> Unit) {
    Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Choose a Borealis Roleplay Den theme. Your choice is saved on this device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    previews.chunked(2).forEach { rowThemes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowThemes.forEach { preview ->
                val selected = selectedTheme == preview.id
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onThemeChange(preview.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = preview.background)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(preview.surface)
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.fillMaxWidth(0.58f).height(7.dp).clip(CircleShape).background(preview.text.copy(alpha = 0.75f)))
                                Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(CircleShape).background(preview.text.copy(alpha = 0.28f)))
                                Spacer(Modifier.height(1.dp))
                                Box(Modifier.size(width = 42.dp, height = 10.dp).clip(CircleShape).background(preview.accent))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(preview.accent))
                            Text(
                                text = doseyThemeDisplayName(preview.id),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = preview.text,
                                maxLines = 2
                            )
                        }
                        if (selected) {
                            Text("Selected", style = MaterialTheme.typography.labelSmall, color = preview.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("App font", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Ubuntu", style = MaterialTheme.typography.titleLarge)
            Text(
                "Ubuntu is the primary typeface for Dosey, matching the Borealis design language.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
