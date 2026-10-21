package com.afterlater.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteTextTest {

    @Test
    fun `blank notes are stored as absent`() {
        assertNull(NoteText.normalize(null))
        assertNull(NoteText.normalize(""))
        assertNull(NoteText.normalize(" \n\t "))
    }

    @Test
    fun `outer whitespace is removed and internal line breaks are kept`() {
        assertEquals(
            "Keep this for the methods section.\nCompare with the other paper.",
            NoteText.normalize("  Keep this for the methods section.\nCompare with the other paper.  "),
        )
    }

    @Test
    fun `normalization keeps notes within the editor limit`() {
        val oversized = "x".repeat(NoteText.MAX_LENGTH + 50)

        assertEquals(NoteText.MAX_LENGTH, NoteText.normalize(oversized)?.length)
    }

    @Test
    fun `leading whitespace does not consume the note limit`() {
        val text = " ".repeat(20) + "y".repeat(NoteText.MAX_LENGTH)

        assertEquals("y".repeat(NoteText.MAX_LENGTH), NoteText.normalize(text))
    }
}
