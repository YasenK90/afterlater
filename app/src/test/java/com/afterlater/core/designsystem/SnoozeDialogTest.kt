package com.afterlater.core.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterlater.domain.review.SnoozeWindow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SnoozeDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tomorrow preset is offered and selectable`() {
        var selected: SnoozeWindow? = null
        compose.setContent {
            AfterLaterTheme { SnoozeDialog(onDismiss = {}, onSelect = { selected = it }) }
        }

        compose.onNodeWithText("TOMORROW").performClick()

        assertEquals(SnoozeWindow.TOMORROW, selected)
    }

    @Test
    fun `next week preset is selectable`() {
        var selected: SnoozeWindow? = null
        compose.setContent {
            AfterLaterTheme { SnoozeDialog(onDismiss = {}, onSelect = { selected = it }) }
        }

        compose.onNodeWithText("NEXT WEEK").performClick()

        assertEquals(SnoozeWindow.NEXT_WEEK, selected)
    }

    @Test
    fun `pick a date opens the date picker with its own confirm`() {
        compose.setContent {
            AfterLaterTheme { SnoozeDialog(onDismiss = {}, onSelect = {}) }
        }

        compose.onNodeWithText("PICK A DATE").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("SET DATE").assertExists()
    }
}
