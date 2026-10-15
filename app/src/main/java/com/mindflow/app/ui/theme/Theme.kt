package com.mindflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Mindflow design system — dark-first palette from the product spec.
val Background = Color(0xFF101010)
val Surface1 = Color(0xFF1A1A1D)
val Surface2 = Color(0xFF242428)
val PrimaryPurple = Color(0xFFA995FF)
val SecondaryPurple = Color(0xFF8171E8)
val AccentLime = Color(0xFFB7FF59)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFFA3A3AA)
val BorderColor = Color(0xFF39393F)
val Success = Color(0xFF57D9A3)
val ErrorRed = Color(0xFFFF6B6B)

private val MindflowColors = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Background,
    secondary = SecondaryPurple,
    onSecondary = TextPrimary,
    tertiary = AccentLime,
    onTertiary = Background,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = ErrorRed,
    onError = Background
)

val MindflowTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun MindflowTheme(
    darkTheme: Boolean = true, // app is dark-first; parameter kept for future light mode
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MindflowColors,
        typography = MindflowTypography,
        content = content
    )
}
