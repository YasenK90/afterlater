package com.afterlater.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryBuilderTest {

    @Test
    fun `every word becomes a prefix token`() {
        assertEquals("watchlat* later*", FtsQueryBuilder.matchQuery("watchlat later"))
    }

    @Test
    fun `punctuation is dropped instead of breaking MATCH syntax`() {
        assertEquals("recipe* best*", FtsQueryBuilder.matchQuery("\"recipe\" (best)!"))
        assertEquals("youtube* com* watch* v* 1*", FtsQueryBuilder.matchQuery("youtube.com/watch?v=1"))
    }

    @Test
    fun `input without word characters yields no match`() {
        assertNull(FtsQueryBuilder.matchQuery("!!! ... ---"))
        assertNull(FtsQueryBuilder.matchQuery(""))
        assertNull(FtsQueryBuilder.matchQuery("   "))
    }

    @Test
    fun `unicode letters and digits are kept`() {
        assertEquals("café* 42*", FtsQueryBuilder.matchQuery("café 42"))
    }
}
