package com.crispyburger.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CbmsColors = lightColorScheme(
    primary = Color(0xFF1B1410),
    onPrimary = Color(0xFFFFC93C),
    secondary = Color(0xFFD7263D),
    background = Color(0xFFFFFDF7),
    surface = Color(0xFFFFFDF7),
    primaryContainer = Color(0xFFFFC93C),
    onPrimaryContainer = Color(0xFF1B1410)
)

@Composable
fun CbmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CbmsColors, content = content)
}
