package com.borealis.dosey.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.borealis.dosey.ui.theme.doseyThemeDisplayName

private data class ThemePreview(
    val id: String,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val text: Color
)

private val previews = listOf(
    ThemePreview("frappe", Color(0xFF303446), Color(0xFF414559), Color(0xFFBABBF1), Color(0xFFC6D0F5)),
    ThemePreview("latte", Color(0xFFEFF1F5), Color(0xFFCCD0DA), Color(0xFF8839EF), Color(0xFF4C4F69)),
    ThemePreview("macchiato", Color(0xFF24273A), Color(0xFF363A4F), Color(0xFFB7BDF8), Color(0xFFCAD3F5)),
    ThemePreview("mocha", Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFFB4BEFE), Color(0xFFCDD6F4)),
    ThemePreview("crimson-void", Color(0xFF100D12), Color(0xFF321B26), Color(0xFFFF244F), Color(0xFFF2DCE5)),
    ThemePreview("coral-bloom", Color(0xFF241A20), Color(0xFF35222B), Color(0xFFFF91A4), Color(0xFFFFE0E4)),
    ThemePreview("sakura-dreams", Color(0xFFFFF7F5), Color(0xFFF4DCD9), Color(0xFFD94F70), Color(0xFF703744)),
    ThemePreview("moon-light", Color(0xFFF7F6FF), Color(0xFFDEDEF2), Color(0xFF777BD1), Color(0xFF353650)),
    ThemePreview("moon-shadow", Color(0xFF10121F), Color(0xFF252840), Color(0xFF8588ED), Color(0xFFE1E3FF))
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("user" to "User", "appearance" to "Appearance", "notifications" to "Notifications", "data" to "Data").forEach { (id, label) ->
                val selected = section == id
                TextButton(onClick = { onSectionChange(id) }, modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        when (section) {
            "appearance" -> AppearanceSettings(selectedTheme, onThemeChange)
            "notifications" -> NotificationSettings()
            "user" -> PlaceholderSettings(
                title = "User",
                heading = "Your Dosey profile",
                detail = "Profile and personalisation options will live here."
            )
            else -> PlaceholderSettings(
                title = "Data",
                heading = "Your data stays yours",
                detail = "Backup, export, import and storage options will live here."
            )
        }
    }
}

@Composable
private fun PlaceholderSettings(title: String, heading: String, detail: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowThemes.forEach { preview ->
                val selected = selectedTheme == preview.id
                Card(
                    modifier = Modifier.weight(1f).border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(20.dp)
                    ).clickable { onThemeChange(preview.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = preview.background)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(12.dp))
                                .background(preview.surface).padding(10.dp)
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
                        if (selected) Text("Selected", style = MaterialTheme.typography.labelSmall, color = preview.accent, fontWeight = FontWeight.Bold)
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

@Composable
private fun NotificationSettings() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("dosey_preferences", Context.MODE_PRIVATE) }
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var persistent by remember { mutableStateOf(preferences.getBoolean("notifications_persistent", false)) }
    var soundUri by remember { mutableStateOf(preferences.getString("notifications_sound_uri", null)) }
    var soundName by remember { mutableStateOf(soundUri?.let { Uri.parse(it).lastPathSegment } ?: "Default system sound") }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            soundUri = uri.toString()
            soundName = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Custom sound selected"
            preferences.edit().putString("notifications_sound_uri", soundUri).apply()
        }
    }

    Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Make sure medication reminders can reach you on time. Android may still apply device-specific restrictions.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SettingsCard {
        Text("Notification permission", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (notificationsAllowed) "Allowed — Dosey can post notifications." else "Not allowed — reminders may be silent or hidden.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!notificationsAllowed) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }) { Text("Grant permission") }
        } else {
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Open Android notification settings") }
        }
    }

    val ignoringBatteryOptimisations = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }
    SettingsCard {
        Text("Battery optimisation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (ignoringBatteryOptimisations) "No battery-optimisation exemption needed right now, or Dosey is already exempt." else
                "Some Android brands may delay reminders while Dosey is restricted in the background.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringBatteryOptimisations) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }) { Text(if (ignoringBatteryOptimisations) "Battery settings" else "Allow background activity") }
    }

    SettingsCard {
        Text("Persistent reminders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Keep reminder notifications pinned until you act on them. This is separate from keeping daily alarms scheduled after a restart.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (persistent) "Stay visible" else "Can be dismissed")
            Switch(checked = persistent, onCheckedChange = {
                persistent = it
                preferences.edit().putBoolean("notifications_persistent", it).apply()
            })
        }
    }

    SettingsCard {
        Text("Notification sound", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(soundName, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("Choose custom sound") }
            TextButton(onClick = {
                soundUri = null
                soundName = "Default system sound"
                preferences.edit().remove("notifications_sound_uri").apply()
            }) { Text("Reset") }
        }
        Text(
            "Choose an audio file from your device. Android notification channels may retain their previous sound until the channel is refreshed in system settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}
