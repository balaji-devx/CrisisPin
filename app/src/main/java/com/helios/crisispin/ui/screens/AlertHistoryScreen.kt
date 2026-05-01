package com.helios.crisispin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// Data model with real timestamp
data class AlertHistoryItem(
    val id: String,
    val type: String,
    val emoji: String,
    val colorHex: Int,
    val timestampMs: Long,   // Real system time in ms
    val direction: String    // "sent" or "received"
)

fun AlertHistoryItem.formattedTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hr ago"
        diff < 172_800_000 -> "Yesterday"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestampMs))
    }
}

@Composable
fun AlertHistoryScreen(
    alerts: List<AlertHistoryItem>,
    title: String = "Alert History",   // "Received Alerts" or "Alert History"
    showFilters: Boolean = true,       // FIX 5 & 6
    onBack: () -> Unit
) {
    var activeFilter by remember { mutableStateOf("All") }

    val filteredAlerts = when {
        !showFilters -> alerts // If filters hidden, show what's passed (usually already filtered)
        activeFilter == "Received" -> alerts.filter { it.direction == "received" }
        activeFilter == "Sent" -> alerts.filter { it.direction == "sent" }
        else -> alerts
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        // Status bar spacer
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(8.dp))

        // Header — back button on LEFT like common apps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
                if (showFilters) {
                    Text(
                        "${alerts.size} total • ${alerts.count { it.direction == "sent" }} sent · ${alerts.count { it.direction == "received" }} received",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        "${alerts.size} alerts found",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Filter chips — functional, filter list in real time
        if (showFilters) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Received", "Sent").forEach { filter ->
                    val isSelected = activeFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) EmergencyRed else SurfaceCard)
                            .clickable { activeFilter = filter }
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
        }

        if (filteredAlerts.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (activeFilter) {
                            "Sent" -> "No alerts sent yet"
                            "Received" -> "No alerts received yet"
                            else -> "No alerts yet"
                        },
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Alerts will appear here when sent or received",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredAlerts, key = { it.id }) { alert ->
                    AlertHistoryCard(alert)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun AlertHistoryCard(alert: AlertHistoryItem) {
    val color = Color(alert.colorHex)

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
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(alert.emoji, fontSize = 22.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                alert.type,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Rounded.AccessTime,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(11.dp)
                )
                // Real timestamp formatted relative to now
                Text(alert.formattedTime(), color = TextSecondary, fontSize = 11.sp)
            }
        }

        // Direction badge — real sent/received from BLE events
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (alert.direction == "sent") EmergencyRed.copy(0.15f)
                    else ActiveGreen.copy(0.1f)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (alert.direction == "sent") Icons.Rounded.Upload else Icons.Rounded.Download,
                    null,
                    tint = if (alert.direction == "sent") EmergencyRed else ActiveGreen,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    if (alert.direction == "sent") "Sent" else "Received",
                    color = if (alert.direction == "sent") EmergencyRed else ActiveGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Color strip
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
    }
}
