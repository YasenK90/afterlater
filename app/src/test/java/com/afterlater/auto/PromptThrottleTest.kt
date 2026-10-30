package com.afterlater.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptThrottleTest {

    private val throttle = PromptThrottle()

    @Test
    fun `the first prompt is allowed`() {
        assertTrue(throttle.canPrompt(PromptHistory(), FIRST_URL, NOW))
    }

    @Test
    fun `the same video is suppressed for ten minutes`() {
        val history = PromptHistory(lastPromptAtMillis = NOW, lastPromptUrl = FIRST_URL)

        assertFalse(throttle.canPrompt(history, FIRST_URL, NOW + 9 * 60 * 1_000L))
        assertTrue(throttle.canPrompt(history, FIRST_URL, NOW + 10 * 60 * 1_000L))
    }

    @Test
    fun `a different video is suppressed for the global cooldown`() {
        val history = PromptHistory(lastPromptAtMillis = NOW, lastPromptUrl = FIRST_URL)

        assertFalse(throttle.canPrompt(history, SECOND_URL, NOW + 59 * 1_000L))
        assertTrue(throttle.canPrompt(history, SECOND_URL, NOW + 60 * 1_000L))
    }

    @Test
    fun `a clock that moves backwards does not unlock prompts`() {
        val history = PromptHistory(lastPromptAtMillis = NOW, lastPromptUrl = FIRST_URL)

        assertFalse(throttle.canPrompt(history, FIRST_URL, NOW - 1))
    }

    @Test
    fun `recording stores the url and time`() {
        val history = throttle.record(FIRST_URL, NOW)

        assertEquals(NOW, history.lastPromptAtMillis)
        assertEquals(FIRST_URL, history.lastPromptUrl)
    }

    @Test
    fun `a clipboard throttle uses its own global cooldown`() {
        val clipboard = PromptThrottle(globalCooldownMillis = 3_000L)
        val history = PromptHistory(lastPromptAtMillis = NOW, lastPromptUrl = FIRST_URL)

        assertFalse(clipboard.canPrompt(history, SECOND_URL, NOW + 2_999L))
        assertTrue(clipboard.canPrompt(history, SECOND_URL, NOW + 3_000L))
    }

    @Test
    fun `a shorter global cooldown keeps the same url cooldown`() {
        val clipboard = PromptThrottle(globalCooldownMillis = 3_000L)
        val history = PromptHistory(lastPromptAtMillis = NOW, lastPromptUrl = FIRST_URL)

        assertFalse(clipboard.canPrompt(history, FIRST_URL, NOW + 30_000L))
        assertTrue(clipboard.canPrompt(history, FIRST_URL, NOW + 10 * 60 * 1_000L))
    }

    private companion object {
        const val NOW = 1_000_000L
        const val FIRST_URL = "https://www.tiktok.com/@one/video/7391234567890123456"
        const val SECOND_URL = "https://www.tiktok.com/@two/video/7391234567890123457"
    }
}
