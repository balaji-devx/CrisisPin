package com.helios.crisispin.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.theme.*

@Composable
fun IncomingAlertScreen(
    alertType: String,
    onAcknowledge: () -> Unit,
    onIgnore: () -> Unit,
    onCallSecurity: () -> Unit
) {
    val alertColor = when (alertType.uppercase()) {
        "MED" -> MedicalBlue; "FIRE" -> FireOrange; "PANIC" -> PanicPurple; else -> EmergencyRed
    }
    val alertLabel = when (alertType.uppercase()) {
        "MED" -> "MEDICAL EMERGENCY"; "FIRE" -> "FIRE ALERT"
        "PANIC" -> "PANIC ALERT"; "SOS" -> "SOS EMERGENCY"; else -> "EMERGENCY ALERT"
    }
    val alertEmoji = when (alertType.uppercase()) {
        "MED" -> "🏥"; "FIRE" -> "🔥"; "PANIC" -> "⚠️"; else -> "🚨"
    }

    // Disable all buttons after first press — prevents double-firing
    var dismissed by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "inc")
    val headerPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "hp"
    )
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "ip"
    )

    Box(modifier = Modifier.fillMaxSize().background(DarkNavy)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // FIX 10: Use windowInsets instead of hardcoded 48dp top padding.
            // Hardcoded 48dp overlaps status bar on devices with tall notches (e.g. Mi 11, Redmi Note 12).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(headerPulse)
                    .background(Brush.verticalGradient(listOf(alertColor, alertColor.copy(0.75f))))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = 28.dp, start = 24.dp, end = 24.dp, top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠  INCOMING ALERT  ⚠", color = Color.White.copy(0.85f),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(alertLabel, color = Color.White, fontSize = 26.sp,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(32.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(120.dp).scale(iconPulse).clip(CircleShape)
                        .background(alertColor.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text(alertEmoji, fontSize = 56.sp) }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AlertInfoRow(Icons.Rounded.AccessTime, "Timestamp", "Just now")
                AlertInfoRow(Icons.Rounded.LocationOn, "Distance", "Nearby (~30m)")
                AlertInfoRow(Icons.Rounded.BluetoothSearching, "Source", "BLE Broadcast")

                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(alertColor.copy(0.1f)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Hub, null, tint = alertColor, modifier = Modifier.size(18.dp))
                        Text("Acknowledging relays this alert to extend mesh range — stop from Home screen",
                            color = alertColor, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(NavyLight).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { if (!dismissed) { dismissed = true; onAcknowledge() } },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = alertColor),
                    enabled = !dismissed
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Acknowledge + Relay", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { if (!dismissed) { dismissed = true; onCallSecurity() } },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        enabled = !dismissed
                    ) {
                        Icon(Icons.Rounded.LocalPolice, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Security", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { if (!dismissed) { dismissed = true; onIgnore() } },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                        enabled = !dismissed
                    ) {
                        Icon(Icons.Rounded.DoNotDisturb, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ignore", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard).padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Navigation, null,
                            tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Navigate to Source", color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                            .background(SurfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Soon", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Text(label, color = TextSecondary, fontSize = 13.sp)
        }
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}