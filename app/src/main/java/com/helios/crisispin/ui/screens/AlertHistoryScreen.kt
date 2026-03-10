package com.helios.crisispin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.theme.*

data class AlertHistoryItem(
    val id: String,
    val type: String,
    val emoji: String,
    val color: Color,
    val timestamp: String,
    val direction: String // "sent" or "received"
)

@Composable
fun AlertHistoryScreen(
    alerts: List<AlertHistoryItem>,
    onBack: () -> Unit
) {
    val mockAlerts = listOf(
        AlertHistoryItem("1", "SOS Emergency", "🚨", EmergencyRed, "2 mins ago", "received"),
        AlertHistoryItem("2", "Medical Alert", "🏥", MedicalBlue, "10 mins ago", "sent"),
        AlertHistoryItem("3", "Panic Alert", "⚠️", PanicPurple, "25 mins ago", "received"),
        AlertHistoryItem("4", "Fire Alert", "🔥", FireOrange, "1 hour ago", "received"),
        AlertHistoryItem("5", "SOS Emergency", "🚨", EmergencyRed, "Yesterday", "sent"),
        AlertHistoryItem("6", "General Help", "🆘", GeneralGreen, "Yesterday", "received"),
    )

    val displayAlerts = if (alerts.isEmpty()) mockAlerts else alerts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
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
                Text("Alert History", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("${displayAlerts.size} total alerts", color = TextSecondary, fontSize = 13.sp)
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = TextPrimary)
            }
        }

        // Filter chips
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Received", "Sent").forEach { filter ->
                val isSelected = filter == "All"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) EmergencyRed else SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        filter,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Alert list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(displayAlerts) { alert ->
                AlertHistoryCard(alert)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun AlertHistoryCard(alert: AlertHistoryItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Color coded icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(alert.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(alert.emoji, fontSize = 22.sp)
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(alert.type, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Schedule, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text(alert.timestamp, color = TextSecondary, fontSize = 12.sp)
            }
        }

        // Direction badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (alert.direction == "sent") EmergencyRed.copy(0.15f) else ActiveGreen.copy(0.1f)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                alert.direction.replaceFirstChar { it.uppercase() },
                color = if (alert.direction == "sent") EmergencyRed else ActiveGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Alert color indicator strip on right
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(alert.color)
        )
    }
}
