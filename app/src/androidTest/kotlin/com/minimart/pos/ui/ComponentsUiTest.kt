package com.minimart.pos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.minimart.pos.ui.screen.GradientHeader
import com.minimart.pos.ui.screen.HeaderPillButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minimart.pos.ui.components.BarChart
import com.minimart.pos.ui.screen.FeedbackBanner
import com.minimart.pos.ui.screen.UndoBar
import com.minimart.pos.ui.theme.MiniMartTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders real composables on a device or emulator and drives them the way a cashier would. */
@RunWith(AndroidJUnit4::class)
class ComponentsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun feedbackBannerShowsItsMessage() {
        compose.setContent { MiniMartTheme { FeedbackBanner("Customer saved", isError = false) } }
        compose.onNodeWithText("Customer saved").assertIsDisplayed()
    }

    @Test fun feedbackBannerRendersNothingWithoutAMessage() {
        compose.setContent { MiniMartTheme { FeedbackBanner(null, isError = true) } }
        compose.onNodeWithText("Customer saved").assertDoesNotExist()
    }

    @Test fun undoBarCallsBackWhenTapped() {
        var undone = 0
        compose.setContent { MiniMartTheme { UndoBar("Removed Milk", onUndo = { undone++ }) } }
        compose.onNodeWithText("Removed Milk").assertIsDisplayed()
        compose.onNodeWithText("UNDO").performClick()
        assertEquals(1, undone)
    }

    @Test fun barChartDescribesItselfForScreenReaders() {
        compose.setContent {
            MiniMartTheme {
                BarChart(
                    values = listOf(1f, 5f, 3f), highlight = 1,
                    labels = listOf("Mon", "Tue", "Wed"),
                    modifier = Modifier.width(300.dp).height(120.dp)
                )
            }
        }
        compose.onNodeWithContentDescription("Bar chart, 3 bars", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Highest: Tue", substring = true).assertIsDisplayed()
    }

    @Test fun headerButtonsMeetTheMinimumTouchSize() {
        compose.setContent {
            MiniMartTheme { GradientHeader("Reports", "Today", onBack = {}) { HeaderPillButton("Refresh list", Icons.Default.Refresh) {} } }
        }
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
        compose.onNodeWithContentDescription("Refresh list").assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
