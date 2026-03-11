package com.helios.crisispin.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.components.*
import com.helios.crisispin.ui.theme.*

data class AlertTypeOption(
    val label: String,
    val emoji: String,
    val color: Color,
    val message: String
)

val alertTypes = listOf(
    AlertTypeOption("Medical", "🏥", MedicalBlue, "MED"),
    AlertTypeOption("Fire", "🔥", FireOrange, "FIRE"),
    AlertTypeOption("Panic", "⚠️", PanicPurple, "PANIC"),
    AlertTypeOption("Help", "🆘", GeneralGreen, "HELP"),
)

@Composable
fun HomeScreen(
    bleActive: Boolean,
    isBroadcasting: Boolean,
    isRelaying: Boolean,
    nearbyDevices: Int,
    alertsReceived: Int,
    receivedMessage: String,
    eventModeEnabled: Boolean,
    onEventModeToggle: (Boolean) -> Unit,
    onSendAlert: (String) -> Unit,
    onStopAlert: () -> Unit,
    onStopRelay: () -> Unit,
    onNavigate: (String) -> Unit,
    userRole: String
) {
    var selectedAlertType by remember { mutableStateOf(alertTypes[0]) }

    val infiniteTransition = rememberInfiniteTransition(label = "home")
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isBroadcasting) 1.04f else 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "btn"
    )
    val relayPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "relay"
    )

    Box(modifier = Modifier.fillMaxSize().background(DarkNavy)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Status bar inset
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(12.dp))

            // ── Top bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CrisisPin", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusDot(bleActive)
                        Text(
                            if (bleActive) "BLE Active" else "BLE Off — tap to enable",
                            color = if (bleActive) ActiveGreen else OffGray,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Event mode toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (eventModeEnabled) EmergencyRed.copy(0.15f) else SurfaceCard)
                        .border(1.dp,
                            if (eventModeEnabled) EmergencyRed.copy(0.5f) else Color.Transparent,
                            RoundedCornerShape(20.dp))
                        .clickable { onEventModeToggle(!eventModeEnabled) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(if (eventModeEnabled) EmergencyRed else OffGray))
                    Text("Event", color = if (eventModeEnabled) EmergencyRed else TextSecondary,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Relay active banner — shows when mesh relay is running ──
            if (isRelaying) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MedicalBlue.copy(0.12f))
                        .border(1.dp, MedicalBlue.copy(0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Hub, null, tint = MedicalBlue,
                                modifier = Modifier.size(18.dp).scale(relayPulse))
                            Column {
                                Text("Mesh Relay Active", color = MedicalBlue,
                                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Broadcasting received alert to extend range",
                                    color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        // Stop relay button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmergencyRed.copy(0.15f))
                                .clickable { onStopRelay() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("Stop", color = EmergencyRed, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Event mode banner
            if (eventModeEnabled) {
                Box(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmergencyRed.copy(0.1f))
                    .border(1.dp, EmergencyRed.copy(0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("🎪 Event Mode Active — Enhanced alert sensitivity",
                        color = EmergencyRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Received alert banner
            if (receivedMessage != "No Alerts") {
                Box(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmergencyRed.copy(0.15f))
                    .border(1.dp, EmergencyRed.copy(0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🚨", fontSize = 18.sp)
                        Column {
                            Text("Last Alert Received", color = EmergencyRed,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(receivedMessage, color = TextPrimary,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Emergency button ──────────────────────────────────────
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (isBroadcasting) {
                    PulsingRing(EmergencyRed, 280.dp, 0)
                    PulsingRing(EmergencyRed, 280.dp, 500)
                    PulsingRing(EmergencyRed, 280.dp, 1000)
                }
                Box(
                    modifier = Modifier.size(220.dp).clip(CircleShape)
                        .background(EmergencyRed.copy(0.08f))
                        .border(2.dp, EmergencyRed.copy(0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(176.dp).scale(buttonScale).clip(CircleShape)
                            .background(
                                if (isBroadcasting)
                                    Brush.radialGradient(listOf(EmergencyRedDark, EmergencyRed))
                                else
                                    Brush.radialGradient(listOf(EmergencyRed, EmergencyRedDark))
                            )
                            .clickable {
                                if (isBroadcasting) onStopAlert() else onSendAlert(selectedAlertType.message)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isBroadcasting) "⬛" else "🚨", fontSize = 32.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(if (isBroadcasting) "STOP" else "SEND",
                                color = Color.White, fontWeight = FontWeight.Black,
                                fontSize = 13.sp, letterSpacing = 2.sp)
                            Text(if (isBroadcasting) "BROADCAST" else "ALERT",
                                color = Color.White.copy(0.85f), fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, letterSpacing = 1.5.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Alert type selector ───────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                alertTypes.forEach { type ->
                    val isSelected = selectedAlertType == type
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) type.color.copy(0.2f) else SurfaceCard)
                            .border(1.5.dp, if (isSelected) type.color else Color.Transparent,
                                RoundedCornerShape(12.dp))
                            .clickable { selectedAlertType = type }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(type.emoji, fontSize = 20.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(type.label,
                                color = if (isSelected) type.color else TextSecondary,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Live activity panel ───────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(NavyLight).padding(20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Live Activity", color = TextPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusDot(true)
                        Text("Decentralized", color = ActiveGreen,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("DEVICES", "$nearbyDevices",
                        Icons.Rounded.BluetoothSearching, Modifier.weight(1f))
                    StatCard("ALERTS RX", "$alertsReceived",
                        Icons.Rounded.NotificationsActive, Modifier.weight(1f))
                    StatCard("RANGE",
                        if (bleActive) "~50m" else "OFF",
                        Icons.Rounded.SignalCellularAlt, Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround) {
                    BottomNavItem(Icons.Rounded.Home, "Home", true) {}
                    BottomNavItem(Icons.Rounded.NotificationsActive, "Alerts", false) { onNavigate("alerts") }
                    BottomNavItem(Icons.Rounded.History, "History", false) { onNavigate("history") }
                    BottomNavItem(Icons.Rounded.Settings, "Settings", false) { onNavigate("settings") }
                }
            }
        }
    }
}

@Composable
fun BottomNavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp)
    ) {
        Icon(icon, null, tint = if (selected) EmergencyRed else TextMuted,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) EmergencyRed else TextMuted,
            fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}