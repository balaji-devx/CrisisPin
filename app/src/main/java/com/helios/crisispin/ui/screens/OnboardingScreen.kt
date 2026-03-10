package com.helios.crisispin.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.theme.*

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val accentColor: Color
)

val onboardingPages = listOf(
    OnboardingPage(
        emoji = "📡",
        title = "Stay Safe\nWithout Internet",
        description = "CrisisPin works using Bluetooth to send emergency alerts even when networks fail. No internet needed.",
        accentColor = EmergencyRed
    ),
    OnboardingPage(
        emoji = "🚨",
        title = "One Tap\nEmergency Alert",
        description = "Instantly notify every nearby device during emergencies. One button, immediate response.",
        accentColor = Color(0xFFFF9800)
    ),
    OnboardingPage(
        emoji = "🌐",
        title = "Crowd-Powered\nSafety",
        description = "Devices around you receive alerts and help extend communication across the entire crowd.",
        accentColor = Color(0xFF1E88E5)
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val page = onboardingPages[currentPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // Illustration area
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "page"
            ) { pageIndex ->
                val p = onboardingPages[pageIndex]
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(p.accentColor.copy(alpha = 0.25f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Decorative ring
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .then(
                                Modifier.padding(2.dp)
                            )
                    )
                    Text(p.emoji, fontSize = 72.sp)
                }
            }

            Spacer(Modifier.height(48.dp))

            // Page indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onboardingPages.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (index == currentPage) 28.dp else 8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index == currentPage) page.accentColor else TextMuted
                            )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // Text content
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "text"
            ) { pageIndex ->
                val p = onboardingPages[pageIndex]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = p.title,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = p.description,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // CTA Button
            Button(
                onClick = {
                    if (currentPage < onboardingPages.size - 1) {
                        currentPage++
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
            ) {
                Text(
                    text = if (currentPage < onboardingPages.size - 1) "Continue" else "Get Started",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Skip button
            if (currentPage < onboardingPages.size - 1) {
                Text(
                    text = "Skip",
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onFinished() }
                        .padding(8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
