package com.afterlater.auto

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Android 10 and newer refuse clipboard reads from an app that does not have
 * input focus. An accessibility service is not exempt, so this holds a one-pixel
 * focusable accessibility overlay for the few frames the read needs, then removes
 * it. [WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM] keeps the IME aimed at
 * the app underneath, so the user's keyboard does not close.
 */
class ClipboardFocusWindow(private val serviceContext: Context) {

    private val windowManager = serviceContext.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var reading = false
    private var focusView: View? = null

    fun readWithFocus(read: () -> String?, deliver: (String?) -> Unit) {
        val manager = windowManager
        if (manager == null || reading) {
            AutoDetectLog.debug("focus: unavailable (manager=${manager != null}, reading=$reading)")
            deliver(null)
            return
        }

        val view = FrameLayout(serviceContext)
        val params = WindowManager.LayoutParams(
            SIZE_PX,
            SIZE_PX,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = WINDOW_TITLE
        }

        val added = runCatching { manager.addView(view, params) }
        if (added.isFailure) {
            AutoDetectLog.warning("focus: window rejected", added.exceptionOrNull())
            deliver(null)
            return
        }
        AutoDetectLog.debug("focus: window added")

        focusView = view
        reading = true
        var finished = false
        fun finish(result: String?) {
            if (finished) return
            finished = true
            reading = false
            detach()
            deliver(result)
        }

        fun attempt(remaining: Int) {
            if (finished) return
            val value = read()
            AutoDetectLog.debug("focus: attempt $remaining result=${if (value == null) "null" else "text"}")
            if (!value.isNullOrBlank()) {
                finish(value)
                return
            }
            if (remaining <= 0) {
                finish(null)
                return
            }
            handler.postDelayed({ attempt(remaining - 1) }, RETRY_DELAY_MILLIS)
        }

        handler.postDelayed({ attempt(MAX_ATTEMPTS) }, FIRST_READ_DELAY_MILLIS)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        reading = false
        detach()
    }

    private fun detach() {
        focusView?.let { view -> runCatching { windowManager?.removeViewImmediate(view) } }
        focusView = null
    }

    private companion object {
        const val SIZE_PX = 1
        const val FIRST_READ_DELAY_MILLIS = 40L
        const val RETRY_DELAY_MILLIS = 60L
        const val MAX_ATTEMPTS = 3
        const val WINDOW_TITLE = "afterlater-clipboard-focus"
    }
}
