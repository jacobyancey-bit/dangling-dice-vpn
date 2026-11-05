package com.danglingdice.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Emerald500 = Color(0xFF22C55E)
val Emerald600 = Color(0xFF16A34A)
val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate400 = Color(0xFF94A3B8)
val Amber500 = Color(0xFFF59E0B)
val Red500 = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    secondary = Emerald600,
    background = Slate950,
    surface = Slate900,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun DanglingDiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

