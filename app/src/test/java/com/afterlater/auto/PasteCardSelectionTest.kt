package com.afterlater.auto

import com.afterlater.core.model.SurfaceHint
import com.afterlater.domain.review.ExpiryChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteCardSelectionTest {

    @Test
    fun `answering one question leaves the card open for the other`() {
        val selection = PasteCardSelection().withSurface(SurfaceHint.TODAY)

        assertFalse(selection.isComplete)
        assertEquals(SurfaceHint.TODAY, selection.surface)
        assertNull(selection.expiry)
    }

    @Test
    fun `answering both questions completes the selection`() {
        val selection = PasteCardSelection()
            .withSurface(SurfaceHint.MUTED)
            .withExpiry(ExpiryChoice.THIS_WEEK)

        assertTrue(selection.isComplete)
    }

    @Test
    fun `a changed answer replaces the earlier one without completing the selection`() {
        val selection = PasteCardSelection()
            .withSurface(SurfaceHint.TODAY)
            .withSurface(SurfaceHint.QUEUE)

        assertEquals(SurfaceHint.QUEUE, selection.surface)
        assertFalse(selection.isComplete)
    }

    @Test
    fun `expiry can be answered before the surface hint`() {
        val selection = PasteCardSelection()
            .withExpiry(ExpiryChoice.TODAY)
            .withSurface(SurfaceHint.QUEUE)

        assertTrue(selection.isComplete)
        assertEquals(ExpiryChoice.TODAY, selection.expiry)
        assertEquals(SurfaceHint.QUEUE, selection.surface)
    }

    @Test
    fun `tapping a tag toggles it on and off`() {
        val selection = PasteCardSelection().withTag("RECIPE")

        assertEquals(listOf("RECIPE"), selection.tags)

        val toggled = selection.withTag("RECIPE")

        assertTrue(toggled.tags.isEmpty())
    }

    @Test
    fun `tags never make the selection complete`() {
        val selection = PasteCardSelection().withTag("BUY IT")

        assertFalse(selection.isComplete)
    }

    @Test
    fun `clearing tags removes every selection`() {
        val cleared = PasteCardSelection()
            .withTag("RECIPE")
            .withTag("USEFUL")
            .clearTags()

        assertTrue(cleared.tags.isEmpty())

        val rebuilt = cleared.withTag("RECIPE")

        assertEquals(listOf("RECIPE"), rebuilt.tags)
    }
}
