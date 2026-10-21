package com.afterlater.core.database

object ArchiveSearch {

    fun escapeLikePattern(value: String): String =
        value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
