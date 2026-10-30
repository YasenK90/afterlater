package com.afterlater.auto

import com.afterlater.core.model.SurfaceHint
import com.afterlater.domain.review.ExpiryChoice

data class PasteCardSelection(
    val surface: SurfaceHint? = null,
    val expiry: ExpiryChoice? = null,
    val tags: List<String> = emptyList(),
) {
    val isComplete: Boolean
        get() = surface != null && expiry != null

    fun withSurface(hint: SurfaceHint): PasteCardSelection = copy(surface = hint)

    fun withExpiry(choice: ExpiryChoice): PasteCardSelection = copy(expiry = choice)

    fun withTag(name: String): PasteCardSelection =
        copy(tags = if (name in tags) tags - name else tags + name)

    fun clearTags(): PasteCardSelection = copy(tags = emptyList())
}
