package com.afterlater.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveSearchTest {

    @Test
    fun `percent underscores and backslashes become literal search text`() {
        assertEquals("100\\%", ArchiveSearch.escapeLikePattern("100%"))
        assertEquals("snake\\_case", ArchiveSearch.escapeLikePattern("snake_case"))
        assertEquals("a\\\\b", ArchiveSearch.escapeLikePattern("a\\b"))
    }

    @Test
    fun `plain search text is unchanged`() {
        assertEquals("android links", ArchiveSearch.escapeLikePattern("android links"))
        assertEquals("", ArchiveSearch.escapeLikePattern(""))
    }
}
