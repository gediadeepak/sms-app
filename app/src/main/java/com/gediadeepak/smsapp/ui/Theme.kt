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
private val Purple = lightColorScheme(
    primary = Color(0xFF6741B5), onPrimary = Color.White, primaryContainer = Color(0xFFEFE5FF), onPrimaryContainer = Color(0xFF422674),
    secondary = Color(0xFF426B76), secondaryContainer = Color(0xFFE2F0F3), onSecondaryContainer = Color(0xFF294D57),
    background = Color(0xFFF8F5FC), onBackground = Color(0xFF30263E), surface = Color.White, onSurface = Color(0xFF30263E),
    surfaceVariant = Color(0xFFF0EBF6), onSurfaceVariant = Color(0xFF71647E), surfaceContainerLow = Color(0xFFFCFAFF),
    surfaceContainer = Color(0xFFF3EDF9), surfaceContainerHigh = Color(0xFFECE5F3), surfaceContainerHighest = Color(0xFFE5DDED),
    surfaceTint = Color(0xFF6741B5), outline = Color(0xFF8C7BA2), outlineVariant = Color(0xFFE3DAED),
)
private val Rose = lightColorScheme(
    primary = Color(0xFFA72F66), onPrimary = Color.White, primaryContainer = Color(0xFFFFE5EF), onPrimaryContainer = Color(0xFF741B45),
    secondary = Color(0xFF3C6E69), secondaryContainer = Color(0xFFE0F1EC), onSecondaryContainer = Color(0xFF264F4A),
    background = Color(0xFFFFF6F9), onBackground = Color(0xFF402632), surface = Color.White, onSurface = Color(0xFF402632),
    surfaceVariant = Color(0xFFF9EDF2), onSurfaceVariant = Color(0xFF80616F), surfaceContainerLow = Color(0xFFFFFAFC),
    surfaceContainer = Color(0xFFFCEEF3), surfaceContainerHigh = Color(0xFFF5E5EC), surfaceContainerHighest = Color(0xFFEEDCE4),
    surfaceTint = Color(0xFFA72F66), outline = Color(0xFFA07D8E), outlineVariant = Color(0xFFEEDCE4),
)
private val Amber = lightColorScheme(
    primary = Color(0xFF8A5200), onPrimary = Color.White, primaryContainer = Color(0xFFFFE8BC), onPrimaryContainer = Color(0xFF653B00),
    secondary = Color(0xFF476851), secondaryContainer = Color(0xFFE5F0DF), onSecondaryContainer = Color(0xFF2D4B36),
    background = Color(0xFFFFF8EF), onBackground = Color(0xFF3D3021), surface = Color.White, onSurface = Color(0xFF3D3021),
    surfaceVariant = Color(0xFFF7F0E5), onSurfaceVariant = Color(0xFF786852), surfaceContainerLow = Color(0xFFFFFCF7),
    surfaceContainer = Color(0xFFFAF0E2), surfaceContainerHigh = Color(0xFFF3E7D6), surfaceContainerHighest = Color(0xFFECDDCA),
    surfaceTint = Color(0xFF8A5200), outline = Color(0xFF9B886B), outlineVariant = Color(0xFFEADFCF),
)

internal fun sandeshColors(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.BLUE -> Blue
    ThemeMode.GREEN -> Green
    ThemeMode.DARK -> Dark
    ThemeMode.PURPLE -> Purple
    ThemeMode.ROSE -> Rose
    ThemeMode.AMBER -> Amber
}

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
    MaterialTheme(colorScheme = sandeshColors(mode), typography = Typography(), content = content)
}
