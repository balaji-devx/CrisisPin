package com.helios.crisispin.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.components.SectionLabel
import com.helios.crisispin.ui.theme.*

@Composable
fun SettingsScreen(
    bleActive: Boolean,
    alertSoundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onAlertSoundToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onBluetoothToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(rememberScrollState())
    ) {
        // Status bar padding
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Settings", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("Configure CrisisPin", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ── Bluetooth ─────────────────────────────────────────────
            SectionLabel("Bluetooth")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Rounded.Bluetooth,
                    iconColor = if (bleActive) ActiveGreen else OffGray,
                    title = "Bluetooth",
                    subtitle = if (bleActive) "On · Scanning for alerts" else "Off · Tap to enable",
                    checked = bleActive,
                    onCheckedChange = onBluetoothToggle
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Alerts ────────────────────────────────────────────────
            SectionLabel("Alert Notifications")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    iconColor = Color(0xFF1E88E5),
                    title = "Alert Sound",
                    subtitle = if (alertSoundEnabled) "Voice announcement on alert · On" else "Muted",
                    checked = alertSoundEnabled,
                    onCheckedChange = onAlertSoundToggle
                )
                HorizontalDivider(
                    color = Divider,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 56.dp)
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Vibration,
                    iconColor = PanicPurple,
                    title = "Vibration",
                    subtitle = if (vibrationEnabled) "SOS pattern on alert · On" else "Vibration off",
                    checked = vibrationEnabled,
                    onCheckedChange = onVibrationToggle
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Advanced ──────────────────────────────────────────────
            // FIX 8 & 9: Simplified Advanced Section
            SectionLabel("Advanced")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OffGray.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = OffGray, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Authority Mode", color = TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Coming Soon", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Status summary card ───────────────────────────────────
            SectionLabel("Status")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusRow("Bluetooth", if (bleActive) "Active" else "Off", if (bleActive) ActiveGreen else OffGray)
                StatusRow("Sound Alerts", if (alertSoundEnabled) "Enabled" else "Disabled", if (alertSoundEnabled) ActiveGreen else OffGray)
                StatusRow("Vibration", if (vibrationEnabled) "Enabled" else "Disabled", if (vibrationEnabled) ActiveGreen else OffGray)
                StatusRow("Network", "Decentralized BLE", ActiveGreen)
            }

            Spacer(Modifier.height(20.dp))

            // ── About ─────────────────────────────────────────────────
            // FIX 10: Improved About Section
            SectionLabel("About")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://crisispin.netlify.app"))
                            context.startActivity(intent)
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📍", fontSize = 24.sp)
                    }
                    Column {
                        Text(
                            "CrisisPin",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text("Version 1.0.0", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap to visit our website",
                            color = EmergencyRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard),
        content = content
    )
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmergencyRed,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}
