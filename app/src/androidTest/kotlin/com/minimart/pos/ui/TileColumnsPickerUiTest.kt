package com.minimart.pos.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minimart.pos.ui.screen.DashCard_
import com.minimart.pos.ui.screen.QuickActionGrid
import com.minimart.pos.ui.screen.TileColumnsPicker
import com.minimart.pos.ui.theme.MiniMartTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Choosing how many Home tiles sit side by side. */
@RunWith(AndroidJUnit4::class)
class TileColumnsPickerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingAChoiceReportsIt() {
        val picked = mutableListOf<Int>()
        compose.setContent { MiniMartTheme { TileColumnsPicker(selected = 4, onPick = { picked += it }) } }
        compose.onNodeWithContentDescription("2 tiles per row").performClick()
        compose.onNodeWithContentDescription("1 tile per row").performClick()
        assertEquals(listOf(2, 1), picked)
    }

    @Test fun theCurrentChoiceIsMarkedSelected() {
        compose.setContent { MiniMartTheme { TileColumnsPicker(selected = 3, onPick = {}) } }
        compose.onNodeWithContentDescription("3 tiles per row").assertIsSelected()
    }

    private fun cards() = listOf(
        DashCard_("today", "Today", "Takings", Icons.Default.Today, Color.DarkGray, Color.Green) {},
        DashCard_("history", "History", "Past sales", Icons.Default.History, Color.DarkGray, Color.Blue) {},
    )

    private fun pos(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot

    @Test fun oneTilePerRowStacksTheTiles() {
        compose.setContent { MiniMartTheme { QuickActionGrid(cards(), "", false, onHide = {}, onSaveOrder = {}, columns = 1) } }
        assertEquals(pos("Today").x, pos("History").x, 1f)
        assertTrue(pos("History").y > pos("Today").y)
    }

    @Test fun fourTilesPerRowPutsTwoTilesSideBySide() {
        compose.setContent { MiniMartTheme { QuickActionGrid(cards(), "", false, onHide = {}, onSaveOrder = {}, columns = 4) } }
        assertEquals(pos("Today").y, pos("History").y, 1f)
        assertTrue(pos("History").x > pos("Today").x)
    }

    @Test fun roomyTilesShowTheirShortDescription() {
        compose.setContent { MiniMartTheme { QuickActionGrid(cards(), "", false, onHide = {}, onSaveOrder = {}, columns = 2) } }
        compose.onNodeWithText("Takings").assertExists()
    }
}
