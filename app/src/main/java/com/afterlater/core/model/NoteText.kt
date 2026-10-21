package com.afterlater.core.model

object NoteText {
    const val MAX_LENGTH = 4_000

    fun limit(value: String): String = value.take(MAX_LENGTH)

    fun normalize(value: String?): String? =
        value?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }
}
