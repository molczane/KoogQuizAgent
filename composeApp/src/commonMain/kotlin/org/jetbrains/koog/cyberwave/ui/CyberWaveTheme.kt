package org.jetbrains.koog.cyberwave.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CyberWaveColorScheme =
    lightColorScheme(
        primary = Color(0xFF0F5F53),
        onPrimary = Color(0xFFFFFCF6),
        primaryContainer = Color(0xFFCDE6DC),
        onPrimaryContainer = Color(0xFF0A2E27),
        secondary = Color(0xFF904B2C),
        onSecondary = Color(0xFFFFF7F1),
        secondaryContainer = Color(0xFFF0D4C4),
        onSecondaryContainer = Color(0xFF35160A),
        tertiary = Color(0xFF304B66),
        onTertiary = Color(0xFFF7FAFF),
        background = Color(0xFFF7F2E8),
        onBackground = Color(0xFF1E241F),
        surface = Color(0xFFFFFCF6),
        onSurface = Color(0xFF1E241F),
        surfaceVariant = Color(0xFFE8E1D5),
        onSurfaceVariant = Color(0xFF4A4F49),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFBF9),
        errorContainer = Color(0xFFF8D7D3),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF7A8078),
        outlineVariant = Color(0xFFC9C6BC),
    )

private val CyberWaveTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 48.sp,
                lineHeight = 52.sp,
                letterSpacing = (-1.2).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.6).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 26.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.6.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.6.sp,
            ),
    )

private val CyberWaveShapes =
    Shapes(
        small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
    )

@Composable
fun CyberWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberWaveColorScheme,
        typography = CyberWaveTypography,
        shapes = CyberWaveShapes,
        content = content,
    )
}
