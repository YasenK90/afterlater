package com.afterlater.auto

import com.afterlater.core.model.ParsedLink
import com.afterlater.domain.url.LinkParser
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

data class TikTokNodeSnapshot(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val isVisibleToUser: Boolean = true,
    val children: List<TikTokNodeSnapshot> = emptyList(),
)

data class DetectedTikTokVideo(
    val originalUrl: String,
    val canonicalUrl: String,
    val domain: String,
) {
    fun toParsedLink(): ParsedLink = ParsedLink(
        originalUrl = originalUrl,
        canonicalUrl = canonicalUrl,
        domain = domain,
        title = null,
    )
}

/**
 * Conservative detector for a TikTok video URL that TikTok itself has exposed in
 * its share sheet.
 *
 * It never invents a URL from a video id. A prompt is only possible when the
 * accessibility tree contains a share-sheet marker and a complete URL that passes
 * host, path, and comment-context checks.
 */
class TikTokVideoDetector {

    fun detect(nodes: List<TikTokNodeSnapshot>): DetectedTikTokVideo? {
        if (!hasShareSheet(nodes)) return null

        val candidates = mutableListOf<DetectedTikTokVideo>()
        nodes.forEach { node -> collect(node, emptyList(), candidates) }
        return candidates.distinctBy { it.canonicalUrl }.singleOrNull()
    }

    fun hasShareSheet(nodes: List<TikTokNodeSnapshot>): Boolean {
        val visible = mutableListOf<TikTokNodeSnapshot>()
        flatten(nodes, visible)
        val text = visible
            .asSequence()
            .filter { it.isVisibleToUser }
            .flatMap { node ->
                sequenceOf(node.text, node.contentDescription, node.viewId)
                    .filterNotNull()
            }
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return SHARE_SHEET_MARKERS.any(text::contains)
    }

    fun visibleText(nodes: List<TikTokNodeSnapshot>): String {
        val visible = mutableListOf<TikTokNodeSnapshot>()
        flatten(nodes, visible)
        val values = visible
            .asSequence()
            .filter { it.isVisibleToUser }
            .mapNotNull { node ->
                val parts = listOfNotNull(
                    node.text?.takeIf { it.isNotBlank() }?.let { "text=$it" },
                    node.contentDescription?.takeIf { it.isNotBlank() }?.let { "desc=$it" },
                    node.viewId?.takeIf { it.isNotBlank() }?.let { "id=$it" },
                )
                parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
            }
            .distinct()
            .joinToString(" | ")
        return values.take(MAX_LOG_TEXT)
    }

    private fun collect(
        node: TikTokNodeSnapshot,
        ancestors: List<TikTokNodeSnapshot>,
        out: MutableList<DetectedTikTokVideo>,
    ) {
        if (!node.isVisibleToUser) return
        if (!isCommentNode(node, ancestors)) {
            sequenceOf(node.text, node.contentDescription)
                .filterNotNull()
                .forEach { value ->
                    val raw = LinkParser.extractFirstUrl(value) ?: return@forEach
                    toVideo(raw)?.let(out::add)
                }
        }
        node.children.forEach { child -> collect(child, ancestors + node, out) }
    }

    private fun isCommentNode(
        node: TikTokNodeSnapshot,
        ancestors: List<TikTokNodeSnapshot>,
    ): Boolean = (ancestors + node).any { hint ->
        val id = hint.viewId?.lowercase(Locale.ROOT).orEmpty()
        COMMENT_VIEW_MARKERS.any(id::contains)
    }

    private fun toVideo(raw: String): DetectedTikTokVideo? {
        val canonical = LinkParser.canonicalize(raw) ?: return null
        val uri = try {
            URI(canonical)
        } catch (_: URISyntaxException) {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host !in TIKTOK_HOSTS) return null

        val path = uri.rawPath ?: return null
        val normalizedPath = path.trimTrailingSlash()
        if (!isVideoPath(host, normalizedPath)) return null

        return DetectedTikTokVideo(
            originalUrl = raw,
            canonicalUrl = "https://$host$normalizedPath",
            domain = host,
        )
    }

    private fun isVideoPath(host: String, path: String): Boolean = when (host) {
        "vm.tiktok.com", "vt.tiktok.com" -> SHORT_CODE.matches(path.removePrefix("/"))
        "tiktok.com", "www.tiktok.com", "m.tiktok.com" ->
            VIDEO_PATH.matches(path) || SHORT_T_PATH.matches(path)
        else -> false
    }

    private fun String.trimTrailingSlash(): String =
        if (length > 1 && endsWith('/')) dropLast(1) else this

    private fun flatten(
        nodes: List<TikTokNodeSnapshot>,
        out: MutableList<TikTokNodeSnapshot>,
    ) {
        nodes.forEach { node ->
            out += node
            flatten(node.children, out)
        }
    }

    companion object {
        val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
        )

        private val TIKTOK_HOSTS = setOf(
            "tiktok.com",
            "www.tiktok.com",
            "m.tiktok.com",
            "vm.tiktok.com",
            "vt.tiktok.com",
        )

        private val SHARE_SHEET_MARKERS = listOf(
            "copy link",
            "copy url",
            "share to",
            "send to",
            "share with",
            "add to story",
        )

        private val COMMENT_VIEW_MARKERS = listOf("comment", "reply")
        private val VIDEO_PATH = Regex("""^/@[^/]+/video/\d+$""")
        private val SHORT_T_PATH = Regex("""^/t/[A-Za-z0-9]{5,}$""")
        private val SHORT_CODE = Regex("""^[A-Za-z0-9]{5,}$""")
        private const val MAX_LOG_TEXT = 4_000
    }
}
