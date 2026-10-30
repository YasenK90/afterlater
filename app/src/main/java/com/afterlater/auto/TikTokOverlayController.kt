package com.afterlater.auto

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.afterlater.R
import kotlin.math.roundToInt

class TikTokOverlayController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    private var overlayView: View? = null
    private var statusView: TextView? = null
    private var buttonRow: View? = null
    private var saveButton: TextView? = null
    private var dismissRunnable: Runnable? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun show(
        onSave: () -> Unit,
        onDismiss: () -> Unit,
    ): Boolean {
        dismiss()
        if (!Settings.canDrawOverlays(appContext)) return false

        val content = buildContent(onSave, onDismiss)
        val params = buildLayoutParams()
        return try {
            windowManager.addView(content, params)
            overlayView = content
            scheduleDismiss(AUTO_DISMISS_MILLIS)
            true
        } catch (error: Exception) {
            AutoDetectLog.warning("Could not add the auto-detect overlay", error)
            false
        }
    }

    fun showResult(message: String) {
        val status = statusView ?: return
        status.text = message
        buttonRow?.visibility = View.GONE
        scheduleDismiss(RESULT_DISMISS_MILLIS)
    }

    fun dismiss() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        overlayView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
                .onFailure { AutoDetectLog.warning("Could not remove the auto-detect overlay", it) }
        }
        overlayView = null
        statusView = null
        buttonRow = null
        saveButton = null
    }

    private fun buildContent(
        onSave: () -> Unit,
        onDismiss: () -> Unit,
    ): LinearLayout {
        val wrapper = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = roundedDrawable(
                fill = COLOR_SURFACE,
                stroke = COLOR_ACCENT,
            )
            elevation = dp(10).toFloat()
        }
        wrapper.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        card.addView(
            TextView(appContext).apply {
                text = appContext.getString(R.string.auto_detect_overlay_label)
                setTextColor(COLOR_ACCENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                letterSpacing = 0.12f
            },
        )

        card.addView(
            TextView(appContext).apply {
                text = appContext.getString(R.string.auto_detect_overlay_title)
                setTextColor(COLOR_TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(5), 0, 0)
            },
        )

        statusView = TextView(appContext).apply {
            text = appContext.getString(R.string.auto_detect_overlay_body)
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(5), 0, dp(12))
        }
        card.addView(statusView)

        saveButton = actionButton(
            label = appContext.getString(R.string.auto_detect_overlay_save),
            fill = COLOR_ACCENT,
            stroke = COLOR_ACCENT,
            textColor = COLOR_ACCENT_INK,
        ).apply {
            contentDescription = appContext.getString(R.string.auto_detect_overlay_save_description)
            setOnClickListener {
                isEnabled = false
                statusView?.text = appContext.getString(R.string.auto_detect_overlay_saving)
                onSave()
            }
        }

        val dismissButton = actionButton(
            label = appContext.getString(R.string.auto_detect_overlay_dismiss),
            fill = Color.TRANSPARENT,
            stroke = COLOR_BORDER,
            textColor = COLOR_TEXT,
        ).apply {
            contentDescription = appContext.getString(R.string.auto_detect_overlay_dismiss_description)
            setOnClickListener {
                onDismiss()
                dismiss()
            }
        }

        buttonRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                saveButton,
                LinearLayout.LayoutParams(0, dp(44), 1f),
            )
            addView(Space(appContext), LinearLayout.LayoutParams(dp(8), 1))
            addView(
                dismissButton,
                LinearLayout.LayoutParams(0, dp(44), 1f),
            )
        }
        card.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return wrapper
    }

    private fun actionButton(
        label: String,
        fill: Int,
        stroke: Int,
        textColor: Int,
    ): TextView = TextView(appContext).apply {
        text = label
        gravity = Gravity.CENTER
        isAllCaps = true
        isClickable = true
        isFocusable = true
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.08f
        background = roundedDrawable(fill = fill, stroke = stroke)
        setPadding(dp(8), 0, dp(8), 0)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun roundedDrawable(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(3).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val width = (screenWidth - dp(32)).coerceAtMost(dp(420)).coerceAtLeast(dp(260))
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
            title = appContext.getString(R.string.auto_detect_overlay_window_title)
        }
    }

    private fun scheduleDismiss(delayMillis: Long) {
        dismissRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, delayMillis)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val AUTO_DISMISS_MILLIS = 20_000L
        const val RESULT_DISMISS_MILLIS = 2_500L
        val COLOR_SURFACE = Color.parseColor("#181A18")
        val COLOR_BORDER = Color.parseColor("#4A4F46")
        val COLOR_TEXT = Color.parseColor("#F1F0E9")
        val COLOR_MUTED = Color.parseColor("#9A9E96")
        val COLOR_ACCENT = Color.parseColor("#C5F56A")
        val COLOR_ACCENT_INK = Color.parseColor("#101110")
    }
}
