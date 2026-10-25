package com.minimart.pos.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minimart.pos.ui.screen.DashCard_
import com.minimart.pos.ui.screen.QuickActionGrid
import com.minimart.pos.ui.theme.MiniMartTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Home quick-action tiles as a cashier or owner uses them: open, edit, hide, remembered order. */
@RunWith(AndroidJUnit4::class)
class QuickActionGridUiTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private fun cards() = listOf(
        DashCard_("today", "Today", "", Icons.Default.Today, Color.DarkGray, Color.Green) { opened += "today" },
        DashCard_("history", "History", "", Icons.Default.History, Color.DarkGray, Color.Blue) { opened += "history" },
    )

    private fun show(editing: Boolean, saved: String = "", hidden: MutableList<String> = mutableListOf(), order: MutableList<String> = mutableListOf()) =
        compose.setContent {
            MiniMartTheme { QuickActionGrid(cards(), saved, editing, onHide = { hidden += it }, onSaveOrder = { order += it }, onStartEditing = {}) }
        }

    @Test fun tappingATileOpensIt() {
        show(editing = false)
        compose.onNodeWithText("History").performClick()
        assertEquals(listOf("history"), opened)
    }

    @Test fun tilesFollowTheSavedOrder() {
        show(editing = false, saved = "history,today")
        val left = { t: String -> compose.onNodeWithText(t).fetchSemanticsNode().positionInRoot.x }
        assertTrue(left("History") < left("Today"))
    }

    @Test fun tapOneTileThenAnotherMovesIt() {
        val order = mutableListOf<String>()
        show(editing = true, order = order)
        compose.onNodeWithText("Today").performClick()      // pick up
        compose.onNodeWithText("History").performClick()    // put down in History's place
        assertEquals(listOf("history,today"), order)
        assertTrue(opened.isEmpty())
    }

    @Test fun whileEditingATapDoesNotOpenTheTile() {
        show(editing = true)
        compose.onNodeWithText("Today").performClick()
        assertTrue(opened.isEmpty())
    }

    @Test fun theRedCrossHidesOnlyThatTile() {
        val hidden = mutableListOf<String>()
        show(editing = true, hidden = hidden)
        compose.onAllNodesWithContentDescription("Hide card")[0].performClick()
        assertEquals(1, hidden.size)
    }

    @Test fun noCrossesOutsideEditMode() {
        show(editing = false)
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Hide card").assertCountEquals(0)
    }
}
