package com.edgeclaw.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// EdgeClaw brand colors
val EdgeClawPrimary = Color(0xFF1A73E8)
val EdgeClawSecondary = Color(0xFF34A853)
val EdgeClawTertiary = Color(0xFFFBBC04)
val EdgeClawError = Color(0xFFEA4335)
val EdgeClawSurface = Color(0xFF121212)
val EdgeClawSurfaceLight = Color(0xFFF8F9FA)

private val DarkColorScheme = darkColorScheme(
    primary = EdgeClawPrimary,
    secondary = EdgeClawSecondary,
    tertiary = EdgeClawTertiary,
    error = EdgeClawError,
    surface = EdgeClawSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = EdgeClawPrimary,
    secondary = EdgeClawSecondary,
    tertiary = EdgeClawTertiary,
    error = EdgeClawError,
    surface = EdgeClawSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun EdgeClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
