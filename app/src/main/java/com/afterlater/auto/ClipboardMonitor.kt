package com.afterlater.auto

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.afterlater.core.model.ParsedLink
import com.afterlater.domain.url.LinkParser

class ClipboardMonitor(
    context: Context,
    private val isEnabled: () -> Boolean,
    private val isKeyboardVisible: () -> Boolean,
    private val onLink: (ParsedLink) -> Unit,
) {

    private val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    private val focusWindow = ClipboardFocusWindow(context)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var baseline: String? = null
    private var lastDelivered: String? = null
    private var lastDeliveredAt = 0L
    private var lastReadAt = 0L
    private var readScheduled = false
    private var deferredReadScheduled = false
    private var focusReadActive = false
    private var queuedFollowUp = false
    private var lastFocusReadAt = 0L
    private var copyAffordance = false
    private var copyWatchUntil = 0L
    private var copyWatchRunning = false

    fun start() {
        val manager = clipboardManager ?: return
        if (listener != null) return

        val registered = ClipboardManager.OnPrimaryClipChangedListener { readNow(forced = true) }
        runCatching { manager.addPrimaryClipChangedListener(registered) }
            .onSuccess {
                listener = registered
                AutoDetectLog.debug("clip: listener attached")
                handler.postDelayed(::establishBaseline, BASELINE_DELAY_MILLIS)
            }
            .onFailure { AutoDetectLog.warning("clip: listener could not attach", it) }
    }

    fun stop() {
        clipboardManager?.let { manager ->
            listener?.let { registered ->
                runCatching { manager.removePrimaryClipChangedListener(registered) }
            }
        }
        listener = null
        handler.removeCallbacksAndMessages(null)
        readScheduled = false
        deferredReadScheduled = false
        focusReadActive = false
        queuedFollowUp = false
        lastFocusReadAt = 0L
        copyAffordance = false
        copyWatchUntil = 0L
        copyWatchRunning = false
        baseline = null
        lastDelivered = null
        focusWindow.release()
        AutoDetectLog.debug("clip: listener detached")
    }

    fun onInteraction(looksLikeCopy: Boolean = false) {
        if (!isEnabled() || readScheduled) return
        AutoDetectLog.debug("clip: interaction detected${if (looksLikeCopy) " (copy affordance)" else ""}")
        scheduleRead(INTERACTION_DELAY_MILLIS, looksLikeCopy, deferRateLimit = false)
    }

    /** Fires on share-sheet closes and app switches — apps whose copy buttons
     * emit no click events still change windows when copying. */
    fun onWindowChange() {
        if (!isEnabled() || readScheduled) return
        AutoDetectLog.debug("clip: window changed")
        scheduleRead(WINDOW_CHANGE_DELAY_MILLIS, looksLikeCopy = false, deferRateLimit = true)
    }

    /**
     * While a TikTok share sheet is open, poll the clipboard briefly: TikTok
     * writes the link the moment its Copy link button is tapped, and polling
     * catches it within about a second without needing a click event.
     */
    fun beginCopyWatch() {
        if (!isEnabled()) return
        copyWatchUntil = SystemClock.elapsedRealtime() + COPY_WATCH_MILLIS
        if (copyWatchRunning) return
        copyWatchRunning = true
        AutoDetectLog.debug("clip: copy watch started")
        postCopyWatchRead()
    }

    private fun postCopyWatchRead() {
        handler.postDelayed({
            if (!copyWatchRunning) return@postDelayed
            if (!isEnabled() || SystemClock.elapsedRealtime() > copyWatchUntil) {
                copyWatchRunning = false
                return@postDelayed
            }
            readNow(forced = true)
            postCopyWatchRead()
        }, COPY_WATCH_INTERVAL_MILLIS)
    }

    private fun scheduleRead(
        delayMillis: Long,
        looksLikeCopy: Boolean,
        deferRateLimit: Boolean,
    ) {
        copyAffordance = looksLikeCopy
        readScheduled = true
        handler.postDelayed(
            {
                readScheduled = false
                if (isEnabled()) readNow(forced = false, deferRateLimit = deferRateLimit)
            },
            delayMillis,
        )
    }

    private fun establishBaseline() {
        if (!isEnabled()) return
        val direct = currentText()
        if (direct != null) {
            baseline = direct
            return
        }
        focusWindow.readWithFocus(
            read = ::currentText,
            deliver = { value ->
                baseline = value
                AutoDetectLog.debug("clip: baseline ${if (value == null) "unknown" else "set"}")
            },
        )
    }

    private fun readNow(forced: Boolean, deferRateLimit: Boolean = false) {
        if (!isEnabled()) return
        val now = SystemClock.elapsedRealtime()
        if (!forced && now - lastReadAt < MIN_READ_INTERVAL_MILLIS) {
            // Never drop the request: defer it to the earliest moment the
            // interval allows, so a click-driven read always happens.
            scheduleDeferredRead(lastReadAt + MIN_READ_INTERVAL_MILLIS - now)
            return
        }
        lastReadAt = now
        val direct = currentText()
        if (direct != null) {
            deliver(direct)
            return
        }
        if (focusReadActive) {
            queuedFollowUp = true
            AutoDetectLog.debug("clip: read queued behind active focus read")
            return
        }
        if (isKeyboardVisible()) {
            // A focus read steals input focus; while the user is typing that
            // breaks the keyboard connection. The clipboard is read at the
            // next trigger after the keyboard closes instead.
            AutoDetectLog.debug("clip: keyboard up, skipping focus read")
            return
        }
        if (!forced && now - lastFocusReadAt < FOCUS_READ_MIN_INTERVAL_MILLIS) {
            // Every focus read steals input focus from the user's app for a
            // moment. Clicks without copy affordances are skipped outright;
            // window changes defer to the next free slot so a copy is never
            // lost, just up to ~4 seconds late.
            if (deferRateLimit) {
                scheduleDeferredRead(lastFocusReadAt + FOCUS_READ_MIN_INTERVAL_MILLIS - now)
            } else {
                AutoDetectLog.debug("clip: focus read skipped (rate limit)")
            }
            return
        }
        focusReadActive = true
        lastFocusReadAt = now
        AutoDetectLog.debug("clip: direct read null, trying focus window")
        focusWindow.readWithFocus(
            read = ::currentText,
            deliver = ::onFocusRead,
        )
    }

    private fun scheduleDeferredRead(delayMillis: Long) {
        if (deferredReadScheduled) return
        deferredReadScheduled = true
        AutoDetectLog.debug("clip: focus read deferred")
        handler.postDelayed(
            {
                deferredReadScheduled = false
                if (isEnabled()) readNow(forced = true)
            },
            delayMillis.coerceAtLeast(0),
        )
    }

    private fun onFocusRead(value: String?) {
        focusReadActive = false
        if (value != null) {
            deliver(value)
        } else {
            AutoDetectLog.debug("clip: focus read found nothing")
        }
        if (queuedFollowUp) {
            queuedFollowUp = false
            handler.post { if (isEnabled()) readNow(forced = true) }
        }
    }

    private fun deliver(text: String) {
        if (text == baseline) {
            baseline = null
            lastDelivered = text
            lastDeliveredAt = SystemClock.elapsedRealtime()
            AutoDetectLog.debug("clip: baseline clip, not delivered")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (text == lastDelivered && now - lastDeliveredAt < SAME_CLIP_QUIET_MILLIS) {
            AutoDetectLog.debug("clip: unchanged")
            return
        }
        lastDelivered = text
        lastDeliveredAt = now
        baseline = null
        consume(text)
    }

    private fun currentText(): String? {
        val manager = clipboardManager ?: return null
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun consume(value: String) {
        val text = value.trim()
        if (text.length > MAX_CLIP_LENGTH) {
            AutoDetectLog.debug("clip: too long (${text.length})")
            return
        }
        val link = LinkParser.fromSharedContent(text, null)
        if (link == null) {
            AutoDetectLog.debug("clip: not a link (${text.length} chars)")
            return
        }
        AutoDetectLog.debug("clip: link ${link.canonicalUrl}")
        onLink(link)
    }

    private companion object {
        const val MAX_CLIP_LENGTH = 2_000
        const val BASELINE_DELAY_MILLIS = 1_500L
        const val INTERACTION_DELAY_MILLIS = 150L
        const val WINDOW_CHANGE_DELAY_MILLIS = 250L
        const val MIN_READ_INTERVAL_MILLIS = 600L
        const val SAME_CLIP_QUIET_MILLIS = 30_000L
        const val FOCUS_READ_MIN_INTERVAL_MILLIS = 4_000L
        const val COPY_WATCH_MILLIS = 6_000L
        const val COPY_WATCH_INTERVAL_MILLIS = 800L
    }
}
