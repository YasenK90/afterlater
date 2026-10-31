package com.afterlater.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRetryCadenceTest {

    @Test
    fun `window spans about three and a half seconds`() {
        assertTrue(CaptureRetryCadence.WINDOW_MILLIS in 2_000..4_000)
        assertEquals(
            CaptureRetryCadence.WINDOW_MILLIS,
            CaptureRetryCadence.FIRST_READ_DELAY_MILLIS + CaptureRetryCadence.RETRY_DELAYS_MILLIS.sum(),
        )
    }

    @Test
    fun `delays grow from fast to slow`() {
        assertEquals(100L, CaptureRetryCadence.retryDelayFor(0))
        assertEquals(250L, CaptureRetryCadence.retryDelayFor(1))
        assertEquals(1500L, CaptureRetryCadence.retryDelayFor(CaptureRetryCadence.RETRY_DELAYS_MILLIS.size - 1))
    }

    @Test
    fun `out-of-range retries fall back to the slowest delay`() {
        assertEquals(1500L, CaptureRetryCadence.retryDelayFor(99))
        assertEquals(1500L, CaptureRetryCadence.retryDelayFor(-1))
    }

    @Test
    fun `there are at least five attempts`() {
        assertTrue(CaptureRetryCadence.ATTEMPTS >= 5)
    }
}
