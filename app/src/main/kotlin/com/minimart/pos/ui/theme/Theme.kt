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

@Composable
fun MiniMartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}

val AppTypography = Typography()
    // Material 3 defaults are great for POS displays — override per-screen as needed

// ─── Convenience extensions ───────────────────────────────────────────────────
@Composable fun successColor() = SuccessGreen
@Composable fun accentColor()  = MaterialTheme.colorScheme.secondary
