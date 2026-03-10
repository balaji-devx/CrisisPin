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
    onCallSecurity: () -> Unit
) {
    val alertColor = when (alertType.uppercase()) {
        "MED" -> MedicalBlue
        "FIRE" -> FireOrange
        "PANIC" -> PanicPurple
        else -> EmergencyRed
    }

    val alertLabel = when (alertType.uppercase()) {
        "MED" -> "MEDICAL EMERGENCY"
        "FIRE" -> "FIRE ALERT"
        "PANIC" -> "PANIC ALERT"
        "SOS" -> "SOS EMERGENCY"
        else -> "EMERGENCY ALERT"
    }

    val alertEmoji = when (alertType.uppercase()) {
        "MED" -> "🏥"
        "FIRE" -> "🔥"
        "PANIC" -> "⚠️"
        else -> "🚨"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "incoming")
    val headerScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "hscale"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "iscale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Urgent red header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(headerScale)
                    .background(
                        Brush.verticalGradient(listOf(alertColor, alertColor.copy(0.8f)))
                    )
                    .padding(vertical = 32.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠ INCOMING ALERT ⚠", color = Color.White.copy(0.85f),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(alertLabel, color = Color.White, fontSize = 26.sp,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(40.dp))

            // Alert icon
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(iconScale)
                        .clip(CircleShape)
                        .background(alertColor.copy(0.15f))
                        .then(Modifier.padding(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(alertEmoji, fontSize = 56.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Info cards
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlertInfoRow(Icons.Rounded.Schedule, "Timestamp", "Just now")
                AlertInfoRow(Icons.Rounded.LocationOn, "Distance", "Nearby (~30m)")
                AlertInfoRow(Icons.Rounded.Bluetooth, "Source", "BLE Device")
            }

            Spacer(Modifier.weight(1f))

            // Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(NavyLight)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = alertColor)
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Acknowledge", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = onCallSecurity,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.LocalPolice, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Call Security", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                // Future feature placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Navigation, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Navigate to Source", color = TextMuted, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Coming Soon", color = TextMuted, fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Text(label, color = TextSecondary, fontSize = 13.sp)
        }
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
