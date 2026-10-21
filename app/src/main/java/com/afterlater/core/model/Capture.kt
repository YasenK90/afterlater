package com.afterlater.core.model

/** The result of trying to save something the user shared or pasted. */
sealed interface CaptureResult {
    data class Saved(val item: SavedItem) : CaptureResult
    data class Duplicate(val existing: SavedItem) : CaptureResult
    data class Invalid(val reason: String) : CaptureResult
    data class Failed(val reason: String) : CaptureResult
}

/** Text handed to us by the Android share sheet. */
data class SharedContent(
    val text: String?,
    val subject: String?,
)

/** A validated, conservatively normalized link ready to persist. */
data class ParsedLink(
    val originalUrl: String,
    val canonicalUrl: String,
    val domain: String,
    val title: String?,
)
