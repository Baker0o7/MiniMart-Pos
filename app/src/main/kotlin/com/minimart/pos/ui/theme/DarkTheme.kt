package com.minimart.pos.ui.theme

import androidx.compose.runtime.derivedStateOf
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

/** [neutral] keeps the greys almost colourless (the Graphite theme); otherwise surfaces take the accent's hue. */
private fun tinted(hue: Float, accent: Color, light: Color, neutral: Boolean = false): Palette {
    val k = if (neutral) 0.18f else 1f
    fun c(s: Float, l: Float) = hslColor(hue, s * k, l)
    return Palette(
        bg = c(.45f, .07f), surface = c(.35f, .105f),
        surface2 = c(.36f, .14f), border = c(.34f, .19f),
        accent = accent, accentLight = light, accentDim = c(.36f, .18f),
        glow = light, subText = c(.14f, .58f), onSurface = c(.45f, .93f),
        dashBg = c(.45f, .04f), dashTop = c(.5f, .11f),
        navBg = c(.4f, .09f), navSurface = c(.3f, .14f),
        // Unselected bottom-bar icons and labels: bright enough to read (WCAG 3:1 for icons, 4.5:1 for labels).
        navUnsel = c(.16f, .62f), navBorder = c(.3f, .22f),
        headerMid = lerp(accent, Color.Black, .22f), headerEnd = lerp(accent, Color.Black, .42f),
        cardTop = c(.5f, .14f), cardBottom = c(.55f, .07f),
        chip = c(.5f, .08f), storeText = c(.7f, .78f)
    )
}

enum class AppAccent(val label: String, val palette: Palette) {
    TEAL("Teal", Palette(
        bg = Color(0xFF0A1A18), surface = Color(0xFF112420), surface2 = Color(0xFF163028), border = Color(0xFF1E4040),
        accent = Color(0xFF008376), accentLight = Color(0xFF4DB6AC), accentDim = Color(0xFF1E3D38),
        glow = Color(0xFF00C9A7), subText = Color(0xFF80A09C), onSurface = Color(0xFFE0F2F1),
        dashBg = Color(0xFF060C0B), dashTop = Color(0xFF0A2623),
        navBg = Color(0xFF0F1E1D), navSurface = Color(0xFF1A2E2C), navUnsel = Color(0xFF8DAAA6), navBorder = Color(0xFF1F4A42),
        headerMid = Color(0xFF006B5E), headerEnd = Color(0xFF004D40),
        cardTop = Color(0xFF0E3A33), cardBottom = Color(0xFF06201C), chip = Color(0xFF0A1F1C), storeText = Color(0xFF8FF0E0)
    )),
    BLUE("Blue", tinted(212f, Color(0xFF1777CB), Color(0xFF64B5F6))),
    PURPLE("Purple", tinted(262f, Color(0xFF7E57C2), Color(0xFFB39DDB))),
    ORANGE("Orange", tinted(28f, Color(0xFFBE5600), Color(0xFFFFB74D))),
    ROSE("Rose", tinted(340f, Color(0xFFD81B60), Color(0xFFF06292))),
    GREEN("Green", tinted(135f, Color(0xFF2E7D32), Color(0xFF81C784))),
    GRAPHITE("Graphite", tinted(205f, Color(0xFF546E7A), Color(0xFFB0BEC5), neutral = true));

    companion object {
        fun fromName(name: String?): AppAccent = entries.firstOrNull { it.name == name } ?: TEAL
    }
}

/**
 * Display options layered on any accent. Both only ever increase contrast against the text, so a
 * palette that passes the contrast test passes in every combination.
 *  - [amoled] ("Deep black"): backgrounds go almost black, which saves battery on OLED screens.
 *  - [highContrast]: pure white text, brighter secondary text, borders and unselected icons.
 */
fun Palette.adapt(amoled: Boolean, highContrast: Boolean): Palette {
    var p = this
    if (amoled) p = p.copy(
        bg = lerp(p.bg, Color.Black, .85f), dashBg = lerp(p.dashBg, Color.Black, .85f),
        navBg = lerp(p.navBg, Color.Black, .8f), surface = lerp(p.surface, Color.Black, .5f),
        surface2 = lerp(p.surface2, Color.Black, .3f), cardBottom = lerp(p.cardBottom, Color.Black, .7f),
        chip = lerp(p.chip, Color.Black, .6f), navSurface = lerp(p.navSurface, Color.Black, .45f),
        dashTop = lerp(p.dashTop, Color.Black, .4f)
    )
    if (highContrast) p = p.copy(
        onSurface = Color.White, subText = lerp(p.subText, Color.White, .4f),
        border = lerp(p.border, Color.White, .35f), navBorder = lerp(p.navBorder, Color.White, .35f),
        navUnsel = lerp(p.navUnsel, Color.White, .35f), accentLight = lerp(p.accentLight, Color.White, .2f)
    )
    return p
}

// ── Shared dark theme palette used across all screens ──────────────────────
// The colours below read the chosen accent from Compose state, so changing it in Settings
// repaints every screen that uses them straight away.
object DT {
    var accent by mutableStateOf(AppAccent.TEAL)
    /** "Deep black" backgrounds (see [adapt]). */
    var amoled by mutableStateOf(false)
    /** Whiter text, stronger borders (see [adapt]). */
    var highContrast by mutableStateOf(false)

    /** The accent's palette with the display options applied; recomputed only when one of them changes. */
    private val pal: Palette by derivedStateOf { accent.palette.adapt(amoled, highContrast) }

    val Bg: Color        get() = pal.bg          // deepest background
    val Surface: Color   get() = pal.surface     // card/surface
    val Surface2: Color  get() = pal.surface2    // elevated surface
    val Border: Color    get() = pal.border      // subtle border
    val Teal: Color      get() = pal.accent      // primary accent (name kept from the original teal theme)
    val TealLight: Color get() = pal.accentLight // lighter accent
    val TealDim: Color   get() = pal.accentDim   // accent background tint
    val OnSurface: Color get() = pal.onSurface   // primary text
    val SubText: Color   get() = pal.subText     // secondary text
    val Green      = Color(0xFF4CAF50)
    val Red        = Color(0xFFEF5350)
    val Amber      = Color(0xFFFFC107)

    val Glow: Color      get() = pal.glow
    val DashBg: Color    get() = pal.dashBg
    val DashTop: Color   get() = pal.dashTop
    val NavBg: Color     get() = pal.navBg
    val NavSurface: Color get() = pal.navSurface
    val NavUnsel: Color  get() = pal.navUnsel
    val NavBorder: Color get() = pal.navBorder
    val HeaderMid: Color get() = pal.headerMid
    val HeaderEnd: Color get() = pal.headerEnd
    val CardTop: Color   get() = pal.cardTop
    val CardBottom: Color get() = pal.cardBottom
    val Chip: Color      get() = pal.chip
    val StoreText: Color get() = pal.storeText
}
