package com.helios.crisispin.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val CrisisPinColorScheme = darkColorScheme(
    primary = EmergencyRed,
    onPrimary = Color.White,
    secondary = NavyMid,
    onSecondary = TextPrimary,
    background = DarkNavy,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    error = EmergencyRed,
    outline = Divider
)

val CrisisPinTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 32.sp, color = Color.White),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, color = Color(0xFFF5F7FA)),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, color = Color(0xFF8A9BB0)),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.5.sp),
)

@Composable
fun CrisisPinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CrisisPinColorScheme,
        typography = CrisisPinTypography,
        content = content
    )
}