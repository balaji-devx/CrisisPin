package com.helios.crisispin.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.theme.*

@Composable
fun PermissionScreen(onPermissionsGranted: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "perm")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            // Animated shield icon
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(EmergencyRed.copy(0.3f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = EmergencyRed,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Enable Bluetooth\nfor Safety",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "CrisisPin requires the following permissions to send and receive emergency alerts.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(40.dp))

            // Permission cards
            PermissionItem(
                icon = Icons.Rounded.Bluetooth,
                title = "Bluetooth",
                description = "Required to broadcast and receive BLE emergency alerts",
                color = Color(0xFF1E88E5)
            )

            Spacer(Modifier.height(12.dp))

            PermissionItem(
                icon = Icons.Rounded.LocationOn,
                title = "Location",
                description = "Required by Android for BLE scanning to function",
                color = ActiveGreen
            )

            Spacer(Modifier.weight(1f))

            // Device illustration (simple phones connecting)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == 1) 44.dp else 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (i == 1) EmergencyRed else SurfaceCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📱", fontSize = if (i == 1) 24.sp else 18.sp)
                    }
                    if (i < 2) {
                        Text("···", color = EmergencyRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Button(
                onClick = onPermissionsGranted,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
            ) {
                Icon(Icons.Rounded.Bluetooth, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Allow Permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun PermissionItem(icon: ImageVector, title: String, description: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
