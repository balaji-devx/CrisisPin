package com.helios.crisispin.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // Animate rings outward
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseOut), RepeatMode.Restart),
        label = "r1"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseOut), RepeatMode.Restart),
        label = "r1a"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1800, delayMillis = 500, easing = EaseOut), RepeatMode.Restart),
        label = "r2"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, delayMillis = 500, easing = EaseOut), RepeatMode.Restart),
        label = "r2a"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1800, delayMillis = 1000, easing = EaseOut), RepeatMode.Restart),
        label = "r3"
    )
    val ring3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, delayMillis = 1000, easing = EaseOut), RepeatMode.Restart),
        label = "r3a"
    )

    LaunchedEffect(Unit) {
        delay(3000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(NavyLight, DarkNavy),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Signal rings
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(ring1)
                    .clip(CircleShape)
                    .background(EmergencyRed.copy(alpha = ring1Alpha))
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(ring2)
                    .clip(CircleShape)
                    .background(EmergencyRed.copy(alpha = ring2Alpha))
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(ring3)
                    .clip(CircleShape)
                    .background(EmergencyRed.copy(alpha = ring3Alpha))
            )

            // Center pin icon circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(EmergencyRed),
                contentAlignment = Alignment.Center
            ) {
                Text("📍", fontSize = 40.sp)
            }
        }

        // Bottom text
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CrisisPin",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Decentralized Safety Network",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
