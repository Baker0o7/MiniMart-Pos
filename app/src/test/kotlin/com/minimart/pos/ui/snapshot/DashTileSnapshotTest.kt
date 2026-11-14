package com.minimart.pos.ui.snapshot

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.minimart.pos.ui.screen.DashActionCard
import com.minimart.pos.ui.theme.MiniMartTheme
import com.minimart.pos.util.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Home tile is the smallest thing on screen that has to survive long Arabic names and big system fonts. These tests
 * guard the bug seen on a phone ("مخزون منخفض" cut to "…"): the title must never be cut off, in either language, at a
 * narrow width, with the text size turned up. A small colour snapshot also catches unintended look changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class DashTileSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() { Lang.arabic = false }

    private val title = mutableStateOf("")

    /** Composes the tile once (a Compose test rule allows a single setContent); [title] then changes the text. */
    private fun start(widthDp: Int, fontScale: Float) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                MiniMartTheme {
                    Box(Modifier.width(widthDp.dp)) {
                        DashActionCard(title.value, "", Icons.Default.Warning, Color(0xFF2A0F0F), Color(0xFFEF5350),
                            editing = false, dragging = false, selected = false, onClick = {}, onLongClick = {})
                    }
                }
            }
        }
    }

    private fun show(newTitle: String) { rule.runOnUiThread { title.value = newTitle }; rule.waitForIdle() }

    /** Cut off = some line ends in "…" (the bug seen on a phone). `hasVisualOverflow` is not used: it is also true for a short word in a tile that reserves two lines. */
    private fun cutOff(l: TextLayoutResult) = (0 until l.lineCount).any { l.isLineEllipsized(it) }

    private fun layoutOf(title: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = rule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertTrue("no text layout for $title", results.isNotEmpty())
        val l = results.first()
        // Written next to the snapshots so CI can print it when a check fails (the log only shows the line number).
        runCatching {
            java.io.File("build/snapshots-actual").apply { mkdirs() }.resolve("layout-diagnostics.txt")
                .appendText("text=${title.length} chars, lines=${l.lineCount}, overflow=${l.hasVisualOverflow}, size=${l.size}, arabic=${Lang.arabic}, id=${title.hashCode()}\n")
        }
        return l
    }

    @Test fun `arabic tile names fit in two lines at a narrow width`() {
        Lang.arabic = true
        start(widthDp = 80, fontScale = 1.0f)
        listOf("مخزون منخفض", "تذكيرات إعادة الطلب", "مدفوعات إم-بيسا", "سجل النشاط").forEach { title ->
            show(title)
            val l = layoutOf(title)
            assertFalse("'$title' is cut off", cutOff(l))
            assertTrue("'$title' needs ${l.lineCount} lines", l.lineCount <= 2)
        }
    }

    @Test fun `english tile names fit in two lines at a narrow width`() {
        start(widthDp = 80, fontScale = 1.0f)
        listOf("Low stock", "Reorder reminders", "M-Pesa payments", "Activity log").forEach { title ->
            show(title)
            val l = layoutOf(title)
            assertFalse("'$title' is cut off", cutOff(l))
            assertTrue("'$title' needs ${l.lineCount} lines", l.lineCount <= 2)
        }
    }

    @Test fun `a short arabic name stays whole with larger system text`() {
        Lang.arabic = true
        start(widthDp = 96, fontScale = 1.3f); show("المبيعات")
        assertFalse(cutOff(layoutOf("المبيعات")))
    }

    @Test fun `low stock tile looks the same as the saved snapshot`() = snapshot("dash_tile_low_stock_ar") {
        Lang.arabic = true
        start(widthDp = 96, fontScale = 1.0f); show("مخزون منخفض")
    }

    @Test fun `low stock tile in english looks the same as the saved snapshot`() = snapshot("dash_tile_low_stock_en") {
        start(widthDp = 96, fontScale = 1.0f); show("Low stock")
    }

    private fun snapshot(name: String, draw: () -> Unit) {
        draw()
        val actual = Fingerprint.of(rule.onRoot().captureToImage().asAndroidBitmap())
        Fingerprint.saveActual(name, actual)
        val golden = Fingerprint.golden(name)
        assertNotNull("No saved snapshot. Save this as app/src/test/resources/snapshots/$name.txt:\n" + actual.joinToString("\n"), golden)
        assertEquals("$name differs from the saved snapshot", null, Fingerprint.difference(golden!!, actual))
    }
}
