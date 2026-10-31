package com.afterlater.auto

/**
 * How long a clipboard read may keep trying before giving up.
 *
 * Some apps (TikTok, Instagram, Twitter) write the link to the clipboard
 * well after their "Copy" button is tapped, and the focus window cannot read
 * until the system grants it focus. The old single 3-attempt burst (~220 ms)
 * missed those late writes entirely; this cadence spans ~3.4 s, enough for
 * the late writers while keeping the invisible focus overlay's hold on
 * input focus bounded.
 */
object CaptureRetryCadence {

    const val FIRST_READ_DELAY_MILLIS = 40L

    /** Delay before retry N (0-based) after a failed attempt. */
    val RETRY_DELAYS_MILLIS = longArrayOf(100L, 250L, 500L, 1000L, 1500L)

    val ATTEMPTS = RETRY_DELAYS_MILLIS.size + 1

    val WINDOW_MILLIS = FIRST_READ_DELAY_MILLIS + RETRY_DELAYS_MILLIS.sum()

    fun retryDelayFor(attemptIndex: Int): Long =
        RETRY_DELAYS_MILLIS.getOrElse(attemptIndex) { RETRY_DELAYS_MILLIS.last() }
}
