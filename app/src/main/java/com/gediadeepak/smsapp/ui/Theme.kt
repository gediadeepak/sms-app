package com.gediadeepak.smsapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF245C46)
val Canvas = Color(0xFFF5F7F3)
val Sage = Color(0xFFE8F0E4)
val Ink = Color(0xFF26392E)
val Muted = Color(0xFF6E7E66)
val Work = Color(0xFF956A37)
val WorkBackground = Color(0xFFF7EEE1)

private val RelayColors = lightColorScheme(
    primary = Forest, onPrimary = Color.White, primaryContainer = Sage, onPrimaryContainer = Forest,
    secondary = Work, secondaryContainer = WorkBackground, onSecondaryContainer = Work,
    background = Canvas, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFF0F3EC), onSurfaceVariant = Muted,
    outline = Color(0xFF819078), outlineVariant = Color(0xFFE0E7D9),
)

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RelayColors, typography = Typography(), content = content)
}
