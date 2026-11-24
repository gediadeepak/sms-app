package com.gediadeepak.smsapp.ui

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gediadeepak.smsapp.core.ThemeMode

val Forest: Color @Composable get() = MaterialTheme.colorScheme.primary
val Canvas: Color @Composable get() = MaterialTheme.colorScheme.background
val Sage: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Work: Color @Composable get() = MaterialTheme.colorScheme.secondary
val WorkBackground: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer

private val Green = lightColorScheme(
    primary = Color(0xFF245C46), onPrimary = Color.White, primaryContainer = Color(0xFFE8F0E4), onPrimaryContainer = Color(0xFF365633),
    secondary = Color(0xFF956A37), secondaryContainer = Color(0xFFF7EEE1), onSecondaryContainer = Color(0xFF795128),
    background = Color(0xFFF5F7F3), onBackground = Color(0xFF26392E), surface = Color.White, onSurface = Color(0xFF26392E),
    surfaceVariant = Color(0xFFF0F3EC), onSurfaceVariant = Color(0xFF6E7E66), surfaceContainerLow = Color(0xFFFAFBF8),
    outline = Color(0xFF819078), outlineVariant = Color(0xFFE0E7D9),
)
private val Blue = lightColorScheme(
    primary = Color(0xFF2856D8), onPrimary = Color.White, primaryContainer = Color(0xFFE8EEFF), onPrimaryContainer = Color(0xFF203F91),
    secondary = Color(0xFF526694), secondaryContainer = Color(0xFFEFF2FA), onSecondaryContainer = Color(0xFF384D78),
    background = Color(0xFFF5F7FC), onBackground = Color(0xFF202B42), surface = Color.White, onSurface = Color(0xFF202B42),
    surfaceVariant = Color(0xFFF0F3FA), onSurfaceVariant = Color(0xFF65728C), surfaceContainerLow = Color(0xFFFAFBFF),
    outline = Color(0xFF7A8AB0), outlineVariant = Color(0xFFDFE5F1),
)
private val Dark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black, primaryContainer = Color(0xFF303030), onPrimaryContainer = Color.White,
    secondary = Color(0xFFD3D3D3), secondaryContainer = Color(0xFF272727), onSecondaryContainer = Color(0xFFECECEC),
    background = Color.Black, onBackground = Color.White, surface = Color(0xFF101010), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF232323), onSurfaceVariant = Color(0xFFBDBDBD), surfaceContainerLow = Color(0xFF080808),
    outline = Color(0xFF8C8C8C), outlineVariant = Color(0xFF343434),
    error = Color.White, onError = Color.Black, errorContainer = Color(0xFF303030), onErrorContainer = Color.White,
)
@Composable
fun SandeshTheme(mode: ThemeMode = ThemeMode.BLUE, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window -> WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        } }
    }
    MaterialTheme(colorScheme = when (mode) { ThemeMode.BLUE -> Blue; ThemeMode.GREEN -> Green; ThemeMode.DARK -> Dark }, typography = Typography(), content = content)
}
