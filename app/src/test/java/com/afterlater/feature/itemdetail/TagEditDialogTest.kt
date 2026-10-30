package com.afterlater.feature.itemdetail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afterlater.core.designsystem.AfterLaterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TagEditDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every available tag is offered`() {
        compose.setContent {
            AfterLaterTheme {
                TagEditDialog(
                    available = listOf("RECIPE", "USEFUL", "BUY IT"),
                    selected = emptySet(),
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        compose.onNodeWithText("RECIPE").assertExists()
        compose.onNodeWithText("USEFUL").assertExists()
        compose.onNodeWithText("BUY IT").assertExists()
    }

    @Test
    fun `confirm returns the toggled selection in offer order`() {
        var confirmed: List<String>? = null
        compose.setContent {
            AfterLaterTheme {
                TagEditDialog(
                    available = listOf("RECIPE", "USEFUL", "BUY IT"),
                    selected = setOf("RECIPE"),
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }

        // The preset selection starts on; tap it off, tap two others on, confirm.
        compose.onNodeWithText("RECIPE").performClick()
        compose.onNodeWithText("USEFUL").performClick()
        compose.onNodeWithText("BUY IT").performClick()
        compose.onNodeWithText("SAVE TAGS").performClick()

        assertEquals(listOf("USEFUL", "BUY IT"), confirmed)
    }

    @Test
    fun `confirming with nothing selected clears every tag`() {
        var confirmed: List<String>? = null
        compose.setContent {
            AfterLaterTheme {
                TagEditDialog(
                    available = listOf("RECIPE", "USEFUL"),
                    selected = setOf("RECIPE", "USEFUL"),
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }

        compose.onNodeWithText("RECIPE").performClick()
        compose.onNodeWithText("USEFUL").performClick()
        compose.onNodeWithText("SAVE TAGS").performClick()

        assertTrue(confirmed?.isEmpty() == true)
    }
}
