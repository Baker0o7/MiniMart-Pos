package com.minimart.pos.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Everything an accent colour changes. Teal is the original palette, unchanged. */
data class Palette(
    val bg: Color, val surface: Color, val surface2: Color, val border: Color,
    val accent: Color, val accentLight: Color, val accentDim: Color,
    val glow: Color, val subText: Color, val onSurface: Color,
    val dashBg: Color, val dashTop: Color,
    val navBg: Color, val navSurface: Color, val navUnsel: Color, val navBorder: Color,
    val headerMid: Color, val headerEnd: Color,
    val cardTop: Color, val cardBottom: Color, val chip: Color, val storeText: Color
)

/** HSL (hue 0–360, saturation and lightness 0–1) to an opaque colour. Pure, so it is unit-tested. */
fun hslColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else    -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}

private fun tinted(hue: Float, accent: Color, light: Color) = Palette(
    bg = hslColor(hue, .45f, .07f), surface = hslColor(hue, .35f, .105f),
    surface2 = hslColor(hue, .36f, .14f), border = hslColor(hue, .34f, .19f),
    accent = accent, accentLight = light, accentDim = hslColor(hue, .36f, .18f),
    glow = light, subText = hslColor(hue, .14f, .58f), onSurface = hslColor(hue, .45f, .93f),
    dashBg = hslColor(hue, .45f, .04f), dashTop = hslColor(hue, .5f, .11f),
    navBg = hslColor(hue, .4f, .09f), navSurface = hslColor(hue, .3f, .14f),
    navUnsel = hslColor(hue, .2f, .35f), navBorder = hslColor(hue, .3f, .22f),
    headerMid = lerp(accent, Color.Black, .22f), headerEnd = lerp(accent, Color.Black, .42f),
    cardTop = hslColor(hue, .5f, .14f), cardBottom = hslColor(hue, .55f, .07f),
    chip = hslColor(hue, .5f, .08f), storeText = hslColor(hue, .7f, .78f)
)

enum class AppAccent(val label: String, val palette: Palette) {
    TEAL("Teal", Palette(
        bg = Color(0xFF0A1A18), surface = Color(0xFF112420), surface2 = Color(0xFF163028), border = Color(0xFF1E4040),
        accent = Color(0xFF00897B), accentLight = Color(0xFF4DB6AC), accentDim = Color(0xFF1E3D38),
        glow = Color(0xFF00C9A7), subText = Color(0xFF80A09C), onSurface = Color(0xFFE0F2F1),
        dashBg = Color(0xFF060C0B), dashTop = Color(0xFF0A2623),
        navBg = Color(0xFF0F1E1D), navSurface = Color(0xFF1A2E2C), navUnsel = Color(0xFF4A6B68), navBorder = Color(0xFF1F4A42),
        headerMid = Color(0xFF006B5E), headerEnd = Color(0xFF004D40),
        cardTop = Color(0xFF0E3A33), cardBottom = Color(0xFF06201C), chip = Color(0xFF0A1F1C), storeText = Color(0xFF8FF0E0)
    )),
    BLUE("Blue", tinted(212f, Color(0xFF1E88E5), Color(0xFF64B5F6))),
    PURPLE("Purple", tinted(262f, Color(0xFF7E57C2), Color(0xFFB39DDB))),
    ORANGE("Orange", tinted(28f, Color(0xFFEF6C00), Color(0xFFFFB74D))),
    ROSE("Rose", tinted(340f, Color(0xFFD81B60), Color(0xFFF06292)));

    companion object {
        fun fromName(name: String?): AppAccent = entries.firstOrNull { it.name == name } ?: TEAL
    }
}

// ── Shared dark theme palette used across all screens ──────────────────────
// The colours below read the chosen accent from Compose state, so changing it in Settings
// repaints every screen that uses them straight away.
object DT {
    var accent by mutableStateOf(AppAccent.TEAL)

    val Bg: Color        get() = accent.palette.bg          // deepest background
    val Surface: Color   get() = accent.palette.surface     // card/surface
    val Surface2: Color  get() = accent.palette.surface2    // elevated surface
    val Border: Color    get() = accent.palette.border      // subtle border
    val Teal: Color      get() = accent.palette.accent      // primary accent (name kept from the original teal theme)
    val TealLight: Color get() = accent.palette.accentLight // lighter accent
    val TealDim: Color   get() = accent.palette.accentDim   // accent background tint
    val OnSurface: Color get() = accent.palette.onSurface   // primary text
    val SubText: Color   get() = accent.palette.subText     // secondary text
    val Green      = Color(0xFF4CAF50)
    val Red        = Color(0xFFEF5350)
    val Amber      = Color(0xFFFFC107)

    val Glow: Color      get() = accent.palette.glow
    val DashBg: Color    get() = accent.palette.dashBg
    val DashTop: Color   get() = accent.palette.dashTop
    val NavBg: Color     get() = accent.palette.navBg
    val NavSurface: Color get() = accent.palette.navSurface
    val NavUnsel: Color  get() = accent.palette.navUnsel
    val NavBorder: Color get() = accent.palette.navBorder
    val HeaderMid: Color get() = accent.palette.headerMid
    val HeaderEnd: Color get() = accent.palette.headerEnd
    val CardTop: Color   get() = accent.palette.cardTop
    val CardBottom: Color get() = accent.palette.cardBottom
    val Chip: Color      get() = accent.palette.chip
    val StoreText: Color get() = accent.palette.storeText
}
