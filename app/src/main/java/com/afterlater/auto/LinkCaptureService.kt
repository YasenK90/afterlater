package com.afterlater.auto

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.afterlater.AfterLaterApplication
import com.afterlater.AppContainer
import com.afterlater.R
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.ParsedLink
import com.afterlater.core.model.SurfaceHint
import com.afterlater.domain.review.ExpiryChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinkCaptureService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = TikTokVideoDetector()
    private val throttle = PromptThrottle()
    private val clipboardThrottle = PromptThrottle(
        globalCooldownMillis = CLIPBOARD_GLOBAL_COOLDOWN_MILLIS,
    )

    private lateinit var container: AppContainer
    private lateinit var overlay: TikTokOverlayController
    private lateinit var pasteCard: PasteCardController
    private lateinit var clipboardMonitor: ClipboardMonitor
    private lateinit var stateStore: AutoDetectStateStore
    private var settings = AppSettings()
    private var scanJob: Job? = null
    private var pendingItemId: String? = null
    private var initialized = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!initialized) {
            container = (application as AfterLaterApplication).container
            overlay = TikTokOverlayController(this)
            pasteCard = PasteCardController(this)
            stateStore = container.autoDetectStateStore
            clipboardMonitor = ClipboardMonitor(
                context = this,
                isEnabled = { settings.clipboardCaptureEnabled },
                onLink = ::onClipboardLink,
            )
            initialized = true
            serviceScope.launch {
                container.settingsRepository.settings.collect { updated ->
                    settings = updated
                    applySettings(updated)
                }
            }
        }
        AutoDetectLog.debug("service connected")
    }

    private fun applySettings(updated: AppSettings) {
        if (!updated.tiktokAutoDetectEnabled) {
            scanJob?.cancel()
            overlay.dismiss()
        }
        if (!updated.clipboardCaptureEnabled) {
            pasteCard.dismiss()
            pendingItemId = null
        }
        if (updated.clipboardCaptureEnabled) clipboardMonitor.start() else clipboardMonitor.stop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (!initialized) return

        val eventPackage = current.packageName?.toString() ?: return
        if (eventPackage == packageName) return

        if (
            settings.clipboardCaptureEnabled &&
            current.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            !isTextEntryTap(current)
        ) {
            clipboardMonitor.onInteraction()
        }

        if (!settings.tiktokAutoDetectEnabled) return
        if (eventPackage !in TikTokVideoDetector.TIKTOK_PACKAGES) {
            if (current.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                overlay.dismiss()
            }
            return
        }
        if (current.eventType !in WANTED_EVENTS) return
        scheduleScan(current.eventType)
    }

    private fun isTextEntryTap(event: AccessibilityEvent): Boolean =
        runCatching { event.source?.isEditable == true }.getOrDefault(false)

    private fun scheduleScan(eventType: Int) {
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            delay(SCAN_DEBOUNCE_MILLIS)
            scan(eventType)
        }
    }

    private suspend fun scan(eventType: Int) {
        if (!settings.tiktokAutoDetectEnabled || overlay.isShowing) return
        if (!AutoDetectPermissions.canDrawOverlays(this)) {
            AutoDetectLog.debug("overlay permission missing")
            return
        }

        val snapshots = readTikTokWindows()
        if (snapshots.isEmpty()) {
            AutoDetectLog.debug("no TikTok window for event=$eventType")
            return
        }

        val shareSheet = detector.hasShareSheet(snapshots)
        AutoDetectLog.debug(
            "event=$eventType shareSheet=$shareSheet text=${detector.visibleText(snapshots)}",
        )
        if (!shareSheet) return

        val video = detector.detect(snapshots) ?: return
        val now = container.clock.millis()
        val history = stateStore.history()
        if (!throttle.canPrompt(history, video.canonicalUrl, now)) {
            AutoDetectLog.debug("throttled ${video.canonicalUrl}")
            return
        }
        val shown = overlay.show(
            onSave = { save(video) },
            onDismiss = { AutoDetectLog.debug("prompt dismissed ${video.canonicalUrl}") },
        )
        if (shown) stateStore.saveHistory(throttle.record(video.canonicalUrl, now))
        AutoDetectLog.debug(
            if (shown) "prompt shown ${video.canonicalUrl}" else "prompt failed ${video.canonicalUrl}",
        )
    }

    private fun readTikTokWindows(): List<TikTokNodeSnapshot> {
        val roots = buildList {
            runCatching { rootInActiveWindow }.getOrNull()?.let(::add)
            runCatching { windows }.getOrNull().orEmpty().forEach { window ->
                runCatching { window.root }.getOrNull()?.let(::add)
            }
        }
        return roots
            .filter { it.packageName?.toString() in TikTokVideoDetector.TIKTOK_PACKAGES }
            .distinctBy { it.windowId }
            .map(AccessibilityTreeReader::read)
    }

    private fun save(video: DetectedTikTokVideo) {
        serviceScope.launch {
            val result = capture(video.toParsedLink())
            val message = when (result) {
                is CaptureResult.Saved -> getString(R.string.auto_detect_saved)
                is CaptureResult.Duplicate -> getString(R.string.auto_detect_duplicate)
                is CaptureResult.Invalid -> getString(R.string.auto_detect_invalid)
                is CaptureResult.Failed -> getString(R.string.auto_detect_failed)
            }
            AutoDetectLog.debug("capture ${result::class.simpleName} for ${video.canonicalUrl}")
            overlay.showResult(message)
        }
    }

    private suspend fun capture(link: ParsedLink): CaptureResult = try {
        withContext(Dispatchers.IO) {
            container.savedItemRepository.capture(link)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AutoDetectLog.warning("Capture failed for ${link.canonicalUrl}", error)
        CaptureResult.Failed("The link could not be saved.")
    }

    private fun onClipboardLink(link: ParsedLink) {
        if (!initialized || !settings.clipboardCaptureEnabled) {
            AutoDetectLog.debug("clipboard card skipped: capture off")
            return
        }
        if (overlay.isShowing) return
        if (isAnnotatingSavedLink()) return
        if (!AutoDetectPermissions.canDrawOverlays(this)) {
            AutoDetectLog.debug("clipboard card skipped: overlay permission missing")
            return
        }

        val now = container.clock.millis()
        if (!clipboardThrottle.canPrompt(stateStore.history(), link.canonicalUrl, now)) {
            AutoDetectLog.debug("clipboard card throttled ${link.canonicalUrl}")
            return
        }
        pendingItemId = null

        val shown = pasteCard.show(
            link = link,
            onSave = { saveFromClipboard(link) },
            onDismiss = { AutoDetectLog.debug("clipboard card dismissed ${link.canonicalUrl}") },
            onSurface = ::applySurfaceHint,
            onExpiry = ::applyExpiry,
            onTags = ::applyTags,
        )
        if (shown) stateStore.saveHistory(clipboardThrottle.record(link.canonicalUrl, now))
        AutoDetectLog.debug(
            if (shown) "clipboard card shown ${link.canonicalUrl}" else "clipboard card failed ${link.canonicalUrl}",
        )
    }

    private fun isAnnotatingSavedLink(): Boolean =
        pasteCard.isShowing && pendingItemId != null

    private fun saveFromClipboard(link: ParsedLink) {
        serviceScope.launch {
            val result = capture(link)
            when (result) {
                is CaptureResult.Saved -> {
                    pendingItemId = result.item.id
                    pasteCard.revealAnnotation()
                }
                is CaptureResult.Duplicate -> {
                    pendingItemId = result.existing.id
                    pasteCard.showResult(getString(R.string.paste_card_duplicate))
                }
                is CaptureResult.Invalid -> {
                    AutoDetectLog.debug(result.reason)
                    pasteCard.showResult(getString(R.string.paste_card_invalid))
                }
                is CaptureResult.Failed -> {
                    AutoDetectLog.debug(result.reason)
                    pasteCard.showResult(getString(R.string.paste_card_failed))
                }
            }
            AutoDetectLog.debug("clipboard capture ${result::class.simpleName} for ${link.canonicalUrl}")
        }
    }

    private fun applySurfaceHint(hint: SurfaceHint) {
        val id = pendingItemId ?: return
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when (hint) {
                        SurfaceHint.TODAY -> container.savedItemRepository.markUsefulAndReschedule(id)
                        else -> container.savedItemRepository.setSurfaceHint(id, hint)
                    }
                }
            }.onFailure { AutoDetectLog.warning("Surface hint failed for $id", it) }
        }
    }

    private fun applyExpiry(choice: ExpiryChoice) {
        val id = pendingItemId ?: return
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    container.savedItemRepository.setDeadline(id, choice)
                }
            }.onFailure { AutoDetectLog.warning("Expiry failed for $id", it) }
        }
    }

    private fun applyTags(tags: List<String>) {
        val id = pendingItemId ?: return
        if (tags.isEmpty()) return
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    container.savedItemRepository.applyTags(id, tags)
                }
            }.onFailure { AutoDetectLog.warning("Tags failed for $id", it) }
        }
    }

    override fun onInterrupt() {
        scanJob?.cancel()
        if (::overlay.isInitialized) overlay.dismiss()
        if (::pasteCard.isInitialized) pasteCard.dismiss()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        if (::overlay.isInitialized) overlay.dismiss()
        if (::pasteCard.isInitialized) pasteCard.dismiss()
        if (::clipboardMonitor.isInitialized) clipboardMonitor.stop()
        serviceScope.cancel()
        AutoDetectLog.debug("service destroyed")
        super.onDestroy()
    }

    private companion object {
        const val SCAN_DEBOUNCE_MILLIS = 350L
        const val CLIPBOARD_GLOBAL_COOLDOWN_MILLIS = 3_000L
        val WANTED_EVENTS = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
        )
    }
}
