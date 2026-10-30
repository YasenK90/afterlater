package com.afterlater.auto

import android.view.accessibility.AccessibilityEvent

/**
 * Decides whether a generic accessibility click should be treated as a potential clipboard copy.
 *
 * No Android objects are exchanged across this boundary, so the decision stays directly testable:
 * the service turns the event into plain values and this object owns only the rule.
 *
 * @param eventType accessibility event type of the current event.
 * @param packageName package that emitted the event, or null when unknown.
 * @param ownPackage package of AfterLater itself; taps inside the app must never trigger capture.
 * @param clipboardCaptureEnabled user setting for the clipboard card feature.
 * @param isEditable true when the tapped node is a text-entry field.
 */
object CaptureTrigger {

    fun shouldTrigger(
        eventType: Int,
        packageName: String?,
        ownPackage: String,
        clipboardCaptureEnabled: Boolean,
        isEditable: Boolean,
    ): Boolean {
        if (!clipboardCaptureEnabled) return false
        if (packageName == null) return false
        if (packageName == ownPackage) return false
        if (eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        if (isEditable) return false
        return true
    }
}
