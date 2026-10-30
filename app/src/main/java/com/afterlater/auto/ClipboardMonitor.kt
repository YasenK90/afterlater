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
        baseline = null
        lastDelivered = null
        focusWindow.release()
        AutoDetectLog.debug("clip: listener detached")
    }

    fun onInteraction() {
        if (!isEnabled() || readScheduled) return
        readScheduled = true
        handler.postDelayed({
            readScheduled = false
            if (isEnabled()) readNow(forced = false)
        }, INTERACTION_DELAY_MILLIS)
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

    private fun readNow(forced: Boolean) {
        if (!isEnabled()) return
        val now = SystemClock.elapsedRealtime()
        if (!forced && now - lastReadAt < MIN_READ_INTERVAL_MILLIS) return
        lastReadAt = now
        val direct = currentText()
        if (direct != null) {
            deliver(direct)
            return
        }
        focusWindow.readWithFocus(
            read = ::currentText,
            deliver = { value -> if (value != null) deliver(value) },
        )
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
        const val INTERACTION_DELAY_MILLIS = 350L
        const val MIN_READ_INTERVAL_MILLIS = 600L
        const val SAME_CLIP_QUIET_MILLIS = 30_000L
    }
}
