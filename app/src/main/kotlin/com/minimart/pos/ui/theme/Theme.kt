package com.minimart.pos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Brand palette ────────────────────────────────────────────────────────────
val Brand500  = Color(0xFF00897B)   // teal (POS green)
val Brand700  = Color(0xFF00695C)
val Brand100  = Color(0xFFB2DFDB)
val Accent    = Color(0xFFFFC107)   // amber for sale/discount highlights
val ErrorRed  = Color(0xFFD32F2F)
val SuccessGreen = Color(0xFF388E3C)
val SurfaceLight = Color(0xFFF8FFFE)
val SurfaceDark  = Color(0xFF101C1B)
val OnSurfaceDark = Color(0xFFCCE8E5)

private val LightColorScheme = lightColorScheme(
    primary          = Brand500,
    onPrimary        = Color.White,
    primaryContainer = Brand100,
    onPrimaryContainer = Brand700,
    secondary        = Accent,
    onSecondary      = Color.Black,
    background       = SurfaceLight,
    surface          = Color.White,
    error            = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary          = Brand100,
    onPrimary        = Brand700,
    primaryContainer = Brand700,
    onPrimaryContainer = Brand100,
    secondary        = Accent,
    onSecondary      = Color.Black,
    background       = SurfaceDark,
    surface          = Color(0xFF1A2E2D),
    error            = Color(0xFFEF9A9A)
)

/**
 * The app is dark on every screen, so Material's own parts (menus, dialogs, text selection, date
 * pickers, switches) are given the same dark palette. They used to follow the system light/dark
 * setting and the wallpaper colours, which put pale menus on dark screens. [darkTheme] and
 * [dynamicColor] are kept for callers but no longer change anything.
 */
@Composable
fun MiniMartTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = DT.Teal, onPrimary = Color.White,
        primaryContainer = DT.TealDim, onPrimaryContainer = DT.TealLight,
        secondary = DT.TealLight, onSecondary = Color.Black,
        secondaryContainer = DT.Surface2, onSecondaryContainer = DT.OnSurface,
        tertiary = Accent, onTertiary = Color.Black,
        background = DT.Bg, onBackground = DT.OnSurface,
        surface = DT.Surface, onSurface = DT.OnSurface,
        surfaceVariant = DT.Surface2, onSurfaceVariant = DT.SubText,
        surfaceTint = DT.Teal,
        outline = DT.SubText, outlineVariant = DT.Border,  // outline is the off-switch thumb: it must stand out from the track
        error = DT.Red, onError = Color.White,
        surfaceContainerLowest = DT.Bg, surfaceContainerLow = DT.Surface,
        surfaceContainer = DT.Surface, surfaceContainerHigh = DT.Surface2, surfaceContainerHighest = DT.Surface2
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            // Every screen paints a dark teal background whatever the Dark-mode switch says, so the
            // system bar icons must always be light. They used to follow !darkTheme, which made
            // the clock/battery and the back/home buttons near-invisible with Dark mode off.
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}

val AppTypography = Typography()
    // Material 3 defaults are great for POS displays — override per-screen as needed

// ─── Convenience extensions ───────────────────────────────────────────────────
@Composable fun successColor() = SuccessGreen
@Composable fun accentColor()  = MaterialTheme.colorScheme.secondary
