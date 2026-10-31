package com.afterlater.core.database

/**
 * Turns raw user search text into a safe FTS4 MATCH expression.
 *
 * Only word characters survive, and each token becomes a prefix query
 * ("watchlat" matches "watch later"), so arbitrary punctuation can never
 * break MATCH syntax. A [null] result means the input had no usable tokens
 * and the caller should report no matches.
 */
object FtsQueryBuilder {

    private val TOKEN = Regex("[\\p{L}\\p{N}_]+")

    fun matchQuery(raw: String): String? =
        TOKEN.findAll(raw)
            .map { it.value }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { "$it*" }
}
