package com.helios.crisispin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.theme.EmergencyRed

@Composable
fun CpLogo(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(EmergencyRed),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "CP",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value / 2).sp
        )
    }
}