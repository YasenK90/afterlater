package com.afterlater.domain.review

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryChoiceTest {

    private val zone = ZoneId.of("+02:00")
    private val now = Instant.parse("2026-03-10T09:00:00Z")

    @Test
    fun `no deadline resolves to null`() {
        assertNull(ExpiryChoice.NONE.resolve(now, zone))
    }

    @Test
    fun `tonight resolves to the end of the local day`() {
        assertEquals(
            Instant.parse("2026-03-10T22:00:00Z"),
            ExpiryChoice.TODAY.resolve(now, zone),
        )
    }

    @Test
    fun `this week resolves a full seven local days out`() {
        assertEquals(
            Instant.parse("2026-03-17T22:00:00Z"),
            ExpiryChoice.THIS_WEEK.resolve(now, zone),
        )
    }

    @Test
    fun `the week deadline is always later than tonight`() {
        val tonight = ExpiryChoice.TODAY.resolve(now, zone)
        val week = ExpiryChoice.THIS_WEEK.resolve(now, zone)

        assertEquals(true, week != null && tonight != null && week.isAfter(tonight))
    }
}
