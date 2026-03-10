package com.helios.crisispin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.components.SectionLabel
import com.helios.crisispin.ui.theme.*

@Composable
fun SettingsScreen(
    bleActive: Boolean,
    onBack: () -> Unit
) {
    var authorityMode by remember { mutableStateOf(false) }
    var backgroundRelay by remember { mutableStateOf(false) }
    var alertSound by remember { mutableStateOf(true) }
    var vibration by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Settings", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("Configure CrisisPin", color = TextSecondary, fontSize = 13.sp)
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = TextPrimary)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // BLE Status section
            SectionLabel("Bluetooth")
            SettingsCard {
                SettingsStatusRow(
                    icon = Icons.Rounded.Bluetooth,
                    iconColor = if (bleActive) ActiveGreen else OffGray,
                    title = "Bluetooth Status",
                    subtitle = if (bleActive) "Connected & Scanning" else "Disconnected",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (bleActive) ActiveGreen.copy(0.15f) else OffGray.copy(0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (bleActive) "ON" else "OFF",
                                color = if (bleActive) ActiveGreen else OffGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Alerts section
            SectionLabel("Alerts")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Rounded.VolumeUp,
                    iconColor = Color(0xFF1E88E5),
                    title = "Alert Sound",
                    subtitle = "Play voice announcement on alert",
                    checked = alertSound,
                    onCheckedChange = { alertSound = it }
                )
                Divider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(
                    icon = Icons.Rounded.Vibration,
                    iconColor = PanicPurple,
                    title = "Vibration",
                    subtitle = "Vibrate SOS pattern on alert",
                    checked = vibration,
                    onCheckedChange = { vibration = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Advanced section
            SectionLabel("Advanced")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Rounded.Shield,
                    iconColor = EmergencyRed,
                    title = "Authority Mode",
                    subtitle = "Mark device as security personnel",
                    checked = authorityMode,
                    onCheckedChange = { authorityMode = it }
                )
                Divider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(
                    icon = Icons.Rounded.Hub,
                    iconColor = FireOrange,
                    title = "Background Relay",
                    subtitle = "Future: Extend mesh relay range",
                    checked = backgroundRelay,
                    onCheckedChange = { backgroundRelay = it },
                    badge = "Soon"
                )
            }

            Spacer(Modifier.height(20.dp))

            // About section
            SectionLabel("About")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📍", fontSize = 24.sp)
                    }
                    Column {
                        Text("CrisisPin", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Version 1.0.0 • Helios", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Internet-free emergency communication using BLE.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
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
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(OffGray.copy(0.3f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(badge, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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

@Composable
fun SettingsStatusRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
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
        trailing()
    }
}
