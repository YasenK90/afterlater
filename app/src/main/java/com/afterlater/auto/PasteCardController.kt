package com.afterlater.auto

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.afterlater.R
import com.afterlater.core.model.ParsedLink
import com.afterlater.core.model.SurfaceHint
import com.afterlater.domain.review.ExpiryChoice
import kotlin.math.roundToInt

/**
 * The clipboard prompt has two shapes:
 *
 * 1. **Collapsed** — a small glassy pill holding only a SAVE button, so a copy
 *    interrupts as little as possible.
 * 2. **Expanded** — after SAVE is tapped (or a result message arrives), the
 *    window grows to the full card with the link details and the SURFACE IT? /
 *    EXPIRES? / TAG IT? stages. Save behavior is identical in both shapes.
 */
class PasteCardController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    private var cardView: View? = null
    private var cardLayout: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var expandedWidth = 0
    private var stageA: View? = null
    private var stageB: View? = null
    private var tagsColumn: View? = null
    private var tagsRows: LinearLayout? = null
    private var statusView: TextView? = null
    private var surfaceChips: View? = null
    private var expiryChips: View? = null
    private var surfaceStatus: TextView? = null
    private var expiryStatus: TextView? = null
    private var headerViews: List<View> = emptyList()
    private var slide: ValueAnimator? = null
    private var targetX = 0
    private var dismissRunnable: Runnable? = null
    private var selection = PasteCardSelection()
    private var tagsStageRevealed = false
    private var tagsFinished = false

    private var onSurface: ((SurfaceHint) -> Unit)? = null
    private var onExpiry: ((ExpiryChoice) -> Unit)? = null
    private var onTags: ((List<String>) -> Unit)? = null
    private var onDismissCallback: (() -> Unit)? = null

    val isShowing: Boolean
        get() = cardView != null

    fun show(
        link: ParsedLink,
        preselectedTags: List<String> = emptyList(),
        onSave: () -> Unit,
        onDismiss: () -> Unit,
        onSurface: (SurfaceHint) -> Unit,
        onExpiry: (ExpiryChoice) -> Unit,
        onTags: (List<String>) -> Unit,
    ): Boolean {
        dismiss()
        val manager = windowManager ?: return false
        if (!Settings.canDrawOverlays(appContext)) return false

        this.onSurface = onSurface
        this.onExpiry = onExpiry
        this.onTags = onTags
        this.onDismissCallback = onDismiss
        selection = PasteCardSelection(tags = preselectedTags)

        val params = buildLayoutParams()
        val content = buildContent(link, onSave)

        return try {
            manager.addView(content, params)
            cardView = content
            layoutParams = params
            content.post {
                if (cardView !== content) return@post
                var width = content.width
                if (width <= 0) {
                    // Laid out later than expected — give it one more frame.
                    content.post {
                        if (cardView !== content) return@post
                        showPillAt(manager, content, params)
                    }
                    return@post
                }
                showPillAt(manager, content, params)
            }
            scheduleDismiss(PILL_DISMISS_MILLIS)
            true
        } catch (error: Exception) {
            AutoDetectLog.warning("Could not add the clipboard card", error)
            dismiss()
            false
        }
    }

    private fun showPillAt(
        manager: WindowManager,
        content: View,
        params: WindowManager.LayoutParams,
    ) {
        targetX = (appContext.resources.displayMetrics.widthPixels - content.width).coerceAtLeast(0)
        AutoDetectLog.debug("pill: laid out w=${content.width} h=${content.height} targetX=$targetX")
        slideIn(manager, content, params)
    }

    fun revealAnnotation() {
        statusView?.apply {
            text = appContext.getString(R.string.paste_card_saved)
            visibility = View.VISIBLE
        }
        stageA?.visibility = View.GONE
        stageB?.visibility = View.VISIBLE
        scheduleDismiss(ANNOTATION_DISMISS_MILLIS)
    }

    fun showResult(message: String) {
        if (tagsStageRevealed) finishTags()
        collapse()
        stageA?.visibility = View.GONE
        stageB?.visibility = View.GONE
        tagsColumn?.visibility = View.GONE
        statusView?.apply {
            text = message
            visibility = View.VISIBLE
        }
        scheduleDismiss(RESULT_DISMISS_MILLIS)
    }

    fun dismiss() {
        if (cardView != null) onDismissCallback?.invoke()
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        slide?.cancel()
        slide = null
        cardView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
                .onFailure { AutoDetectLog.warning("Could not remove the clipboard card", it) }
        }
        cardView = null
        cardLayout = null
        layoutParams = null
        expandedWidth = 0
        stageA = null
        stageB = null
        tagsColumn = null
        tagsRows = null
        statusView = null
        surfaceChips = null
        expiryChips = null
        surfaceStatus = null
        expiryStatus = null
        headerViews = emptyList()
        selection = PasteCardSelection()
        tagsStageRevealed = false
        tagsFinished = false
        onSurface = null
        onExpiry = null
        onTags = null
        onDismissCallback = null
    }

    private fun buildContent(link: ParsedLink, onSave: () -> Unit): LinearLayout {
        val wrapper = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(10), 0)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            // Collapsed padding keeps the pill slim; expand() switches to the
            // roomier padding for the full card.
            setPadding(dp(6), dp(6), dp(10), dp(6))
            background = roundedDrawable(fill = COLOR_CARD_GLASS, stroke = COLOR_ACCENT)
            elevation = dp(12).toFloat()
        }
        cardLayout = card
        wrapper.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val label = TextView(appContext).apply {
            text = appContext.getString(R.string.paste_card_label)
            setTextColor(COLOR_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.12f
            visibility = View.GONE
        }
        card.addView(label)

        val domain = TextView(appContext).apply {
            text = link.domain
            setTextColor(COLOR_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(5), 0, 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        card.addView(domain)

        val url = TextView(appContext).apply {
            text = link.canonicalUrl
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            visibility = View.GONE
        }
        card.addView(url)

        statusView = TextView(appContext).apply {
            text = appContext.getString(R.string.paste_card_body)
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        card.addView(statusView)
        headerViews = listOf(label, domain, url)

        stageA = buildStageA(onSave).also { card.addView(it) }
        stageB = buildStageB().also {
            it.visibility = View.GONE
            card.addView(it)
        }
        tagsColumn = buildStageC().also {
            it.visibility = View.GONE
            card.addView(it)
        }
        return wrapper
    }

    /** The whole collapsed prompt: one small glassy SAVE pill. */
    private fun buildStageA(onSave: () -> Unit): LinearLayout {
        val save = TextView(appContext).apply {
            text = appContext.getString(R.string.paste_card_save)
            gravity = Gravity.CENTER
            isAllCaps = true
            setTextColor(COLOR_ACCENT_INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.08f
            background = roundedDrawable(fill = COLOR_ACCENT, stroke = COLOR_ACCENT)
            setPadding(dp(20), 0, dp(20), 0)
            minHeight = dp(40)
            contentDescription = appContext.getString(R.string.paste_card_save_description)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener {
                isEnabled = false
                expand()
                statusView?.text = appContext.getString(R.string.paste_card_saving)
                onSave()
            }
        }
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(save, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
        }
    }

    /** Grows the window to the full card and reveals the link details. */
    private fun expand() {
        cardLayout?.setPadding(dp(16), dp(16), dp(14), dp(14))
        headerViews.forEach { it.visibility = View.VISIBLE }
        statusView?.visibility = View.VISIBLE
        val manager = windowManager
        val params = layoutParams
        val view = cardView
        if (manager != null && params != null && view != null &&
            params.width != expandedWidth && expandedWidth > 0
        ) {
            params.width = expandedWidth
            runCatching { manager.updateViewLayout(view, params) }
                .onFailure { AutoDetectLog.warning("Could not expand the clipboard card", it) }
        }
    }

    /** Shrinks the window back to its pill size for the result message. */
    private fun collapse() {
        cardLayout?.setPadding(dp(6), dp(6), dp(10), dp(6))
        headerViews.forEach { it.visibility = View.GONE }
        val manager = windowManager
        val params = layoutParams
        val view = cardView
        if (manager != null && params != null && view != null &&
            params.width != WindowManager.LayoutParams.WRAP_CONTENT
        ) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            runCatching { manager.updateViewLayout(view, params) }
                .onFailure { AutoDetectLog.warning("Could not collapse the clipboard card", it) }
        }
    }

    private fun buildStageB(): LinearLayout {
        val column = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, 0)
        }

        column.addView(
            caption(appContext.getString(R.string.paste_card_surface_label)),
        )
        surfaceChips = chipRow(
            listOf(
                appContext.getString(R.string.paste_card_surface_today) to { emitSurface(SurfaceHint.TODAY) },
                appContext.getString(R.string.paste_card_surface_queue) to { emitSurface(SurfaceHint.QUEUE) },
                appContext.getString(R.string.paste_card_surface_muted) to { emitSurface(SurfaceHint.MUTED) },
            ),
        ).also { column.addView(it) }
        surfaceStatus = annotationStatus().also {
            it.visibility = View.GONE
            column.addView(it)
        }

        column.addView(
            caption(appContext.getString(R.string.paste_card_expiry_label)),
        )
        expiryChips = chipRow(
            listOf(
                appContext.getString(R.string.paste_card_expiry_today) to { emitExpiry(ExpiryChoice.TODAY) },
                appContext.getString(R.string.paste_card_expiry_week) to { emitExpiry(ExpiryChoice.THIS_WEEK) },
                appContext.getString(R.string.paste_card_expiry_none) to { emitExpiry(ExpiryChoice.NONE) },
            ),
        ).also { column.addView(it) }
        expiryStatus = annotationStatus().also {
            it.visibility = View.GONE
            column.addView(it)
        }

        return column
    }

    private fun buildStageC(): LinearLayout {
        val column = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, 0)
            contentDescription = appContext.getString(R.string.paste_card_tags_description)
        }

        column.addView(caption(appContext.getString(R.string.paste_card_tags_title)))
        tagsRows = buildTagRows().also { column.addView(it) }

        val skip = chip(
            label = appContext.getString(R.string.paste_card_tags_skip),
            fill = Color.TRANSPARENT,
            stroke = COLOR_BORDER,
            textColor = COLOR_TEXT,
        ).apply {
            setOnClickListener {
                finishTags()
                dismiss()
            }
        }
        column.addView(
            skip,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
                .apply { topMargin = dp(8) },
        )
        return column
    }

    private fun buildTagRows(): LinearLayout {
        val presets = listOf(
            R.string.tag_useful,
            R.string.tag_offer_ending_today,
            R.string.tag_watch_later,
            R.string.tag_recipe,
            R.string.tag_buy_it,
        )
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        presets.chunked(TAGS_PER_ROW).forEachIndexed { rowIndex, rowNames ->
            if (rowIndex > 0) {
                container.addView(Space(appContext), LinearLayout.LayoutParams(1, dp(6)))
            }
            val row = LinearLayout(appContext).apply { orientation = LinearLayout.HORIZONTAL }
            rowNames.forEachIndexed { index, nameRes ->
                val name = appContext.getString(nameRes)
                if (index > 0) row.addView(Space(appContext), LinearLayout.LayoutParams(dp(6), 1))
                val selected = name in selection.tags
                row.addView(
                    chip(
                        label = name,
                        fill = if (selected) COLOR_ACCENT else Color.TRANSPARENT,
                        stroke = if (selected) COLOR_ACCENT else COLOR_BORDER,
                        textColor = if (selected) COLOR_ACCENT_INK else COLOR_TEXT,
                    ).apply {
                        setOnClickListener { toggleTag(name) }
                    },
                    LinearLayout.LayoutParams(0, dp(38), 1f),
                )
            }
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        return container
    }

    private fun toggleTag(name: String) {
        selection = selection.withTag(name)
        val rows = tagsRows ?: return
        val parent = rows.parent as? LinearLayout ?: return
        val index = parent.indexOfChild(rows)
        parent.removeView(rows)
        val rebuilt = buildTagRows()
        tagsRows = rebuilt
        parent.addView(rebuilt, index)
    }

    private fun revealTagsStage() {
        tagsColumn?.visibility = View.VISIBLE
        tagsStageRevealed = true
        scheduleDismiss(ANNOTATION_DISMISS_MILLIS)
    }

    private fun finishTags() {
        if (tagsFinished) return
        tagsFinished = true
        onTags?.invoke(selection.tags)
    }

    private fun annotationStatus(): TextView = TextView(appContext).apply {
        setTextColor(COLOR_ACCENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.06f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun caption(text: String): TextView = TextView(appContext).apply {
        this.text = text
        setTextColor(COLOR_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.12f
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun chipRow(choices: List<Pair<String, () -> Unit>>): LinearLayout =
        LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            choices.forEachIndexed { index, (label, action) ->
                if (index > 0) addView(Space(appContext), LinearLayout.LayoutParams(dp(6), 1))
                addView(
                    chip(
                        label = label,
                        fill = Color.TRANSPARENT,
                        stroke = COLOR_BORDER,
                        textColor = COLOR_TEXT,
                    ).apply {
                        setOnClickListener { action() }
                    },
                    LinearLayout.LayoutParams(0, dp(38), 1f),
                )
            }
        }

    private fun emitSurface(hint: SurfaceHint) {
        selection = selection.withSurface(hint)
        surfaceChips?.visibility = View.GONE
        surfaceStatus?.apply {
            text = confirmSurface(hint)
            visibility = View.VISIBLE
        }
        onSurface?.invoke(hint)
        scheduleDismissAfterAnswer()
    }

    private fun emitExpiry(choice: ExpiryChoice) {
        selection = selection.withExpiry(choice)
        expiryChips?.visibility = View.GONE
        expiryStatus?.apply {
            text = confirmExpiry(choice)
            visibility = View.VISIBLE
        }
        onExpiry?.invoke(choice)
        if (selection.isComplete) {
            revealTagsStage()
        } else {
            scheduleDismiss(SECOND_ANSWER_MILLIS)
        }
    }

    private fun scheduleDismissAfterAnswer() {
        scheduleDismiss(
            if (selection.isComplete) RESULT_DISMISS_MILLIS else SECOND_ANSWER_MILLIS,
        )
    }

    private fun confirmSurface(hint: SurfaceHint): String = appContext.getString(
        when (hint) {
            SurfaceHint.TODAY -> R.string.paste_card_surface_today_done
            SurfaceHint.QUEUE -> R.string.paste_card_surface_queue_done
            SurfaceHint.MUTED -> R.string.paste_card_surface_muted_done
        },
    )

    private fun confirmExpiry(choice: ExpiryChoice): String = appContext.getString(
        when (choice) {
            ExpiryChoice.TODAY -> R.string.paste_card_expiry_today_done
            ExpiryChoice.THIS_WEEK -> R.string.paste_card_expiry_week_done
            ExpiryChoice.NONE -> R.string.paste_card_expiry_none_done
        },
    )

    private fun chip(
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
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.06f
        background = roundedDrawable(fill = fill, stroke = stroke)
        setPadding(dp(6), 0, dp(6), 0)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun roundedDrawable(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        expandedWidth = (screenWidth * WIDTH_FRACTION).roundToInt()
            .coerceAtMost(dp(340))
            .coerceAtLeast(dp(240))
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = screenWidth
            title = appContext.getString(R.string.paste_card_window_title)
        }
    }

    private fun slideIn(
        manager: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        val start = params.x
        if (start == targetX) return
        slide?.cancel()
        slide = ValueAnimator.ofInt(start, targetX).apply {
            duration = SLIDE_IN_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                runCatching { manager.updateViewLayout(view, params) }
            }
            start()
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
        const val WIDTH_FRACTION = 0.86f
        const val TAGS_PER_ROW = 2

        /** How long the collapsed pill lingers when ignored. */
        const val PILL_DISMISS_MILLIS = 8_000L
        const val ANNOTATION_DISMISS_MILLIS = 40_000L
        const val SECOND_ANSWER_MILLIS = 20_000L
        const val RESULT_DISMISS_MILLIS = 2_200L
        const val SLIDE_IN_MILLIS = 220L
        val COLOR_SURFACE = Color.parseColor("#181A18")

        /** The card's glass: gray ink at ~40% opacity for a faint gray tint. */
        const val COLOR_CARD_GLASS = 0x663A3D3A.toInt()
        val COLOR_BORDER = Color.parseColor("#4A4F46")
        val COLOR_TEXT = Color.parseColor("#F1F0E9")
        val COLOR_MUTED = Color.parseColor("#9A9E96")
        val COLOR_ACCENT = Color.parseColor("#C5F56A")
        val COLOR_ACCENT_INK = Color.parseColor("#101110")
    }
}
