package com.crispyburger.admin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B1410),
    onPrimary = Color(0xFFFFC93C),
    secondary = Color(0xFFD7263D),
    background = Color(0xFFFFFDF7),
    surface = Color(0xFFFFFDF7),
    primaryContainer = Color(0xFFFFC93C),
    onPrimaryContainer = Color(0xFF1B1410)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC93C),
    onPrimary = Color(0xFF1B1410),
    secondary = Color(0xFFFF6B7F),
    background = Color(0xFF16110D),
    onBackground = Color(0xFFF4EDE4),
    surface = Color(0xFF16110D),
    onSurface = Color(0xFFF4EDE4),
    surfaceVariant = Color(0xFF2A211B),
    onSurfaceVariant = Color(0xFFD9CCBE),
    primaryContainer = Color(0xFF5A4300),
    onPrimaryContainer = Color(0xFFFFE08A)
)

@Composable
fun CbmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
