package com.helios.crisispin.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
import com.helios.crisispin.ui.components.PulsingRing
import com.helios.crisispin.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AlertSentScreen(alertType: String, onCancel: () -> Unit) {
    var countdown by remember { mutableIntStateOf(10) }

    // FIX 11: Auto-cancel when countdown reaches 0 — previously advertising ran forever
    // if the user navigated away or ignored the screen after sending.
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        // Auto-stop advertising when timer expires
        onCancel()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sent")
    val checkScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "check"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(EmergencyRedDark.copy(0.4f), DarkNavy),
                    radius = 1000f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                PulsingRing(EmergencyRed, 200.dp, 0)
                PulsingRing(EmergencyRed, 200.dp, 500)

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(checkScale)
                        .clip(CircleShape)
                        .background(EmergencyRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Text("Alert Sent!", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("Emergency Broadcasted Successfully",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Nearby devices are being notified.",
                color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(EmergencyRed.copy(0.2f))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(alertType.uppercase(), color = EmergencyRed,
                    fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
            }

            Spacer(Modifier.height(60.dp))

            // Cancel button with live countdown — auto-fires at 0
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(EmergencyRed, EmergencyRedDark))
                )
            ) {
                Text(
                    if (countdown > 0) "Cancel Alert ($countdown)" else "Stopping…",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
        }
    }
}