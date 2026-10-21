package com.afterlater.domain.url

import com.afterlater.core.model.ParsedLink
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Parses untrusted shared or pasted text into a safe web link.
 *
 * Security invariants: only http/https is accepted, sentence punctuation is trimmed,
 * and no scheme other than http/https can ever be produced. The canonical form drops
 * fragments and known tracking parameters but preserves every other query parameter,
 * because social platforms encode post identity there. The original URL is never
 * rewritten for storage.
 */
object LinkParser {

    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val ZERO_WIDTH = Regex("""[\u200B-\u200D\uFEFF]""")
    private val UNICODE_SPACES = Regex("""[\u00A0\u2007\u202F]""")
    private val TRAILING_JUNK = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '"', '\'')

    private val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid",
        "igshid", "si", "vero_id", "wickedid", "oly_anon_id", "oly_enc_id",
    )

    fun fromSharedContent(text: String?, subject: String?): ParsedLink? {
        val normalizedText = text?.normalizeSharedText()
        val normalizedSubject = subject?.normalizeSharedText()
        val raw = normalizedText?.let(::extractFirstUrl)
            ?: normalizedSubject?.let(::extractFirstUrl)
            ?: normalizedText?.let(::extractBareSharedUrl)
            ?: return null
        val canonical = canonicalize(raw) ?: return null
        val host = hostOf(canonical) ?: return null
        val title = normalizedSubject
            ?.cleanTitle()
            ?.takeIf { it.isNotBlank() && extractFirstUrl(it) == null }
        return ParsedLink(
            originalUrl = raw,
            canonicalUrl = canonical,
            domain = host,
            title = title,
        )
    }

    /**
     * Quick Add accepts a bare host and assumes https, while still rejecting any
     * explicit non-web scheme. A whole shared text that is only a bare host is
     * treated the same way; a sentence containing "example.com" is not.
     */
    fun validateManualInput(input: String): ManualInputResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ManualInputResult.Invalid("Paste a link to save it.")
        if (trimmed.any { it.isWhitespace() }) return ManualInputResult.Invalid("A link cannot contain spaces.")

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed

            trimmed.contains("://") -> return ManualInputResult.Invalid("Only http and https links can be saved.")
            looksLikeHost(trimmed) -> "https://$trimmed"
            else -> return ManualInputResult.Invalid("That does not look like a web address.")
        }

        val canonical = canonicalize(withScheme)
            ?: return ManualInputResult.Invalid("That link is not valid. Check it and try again.")
        val host = hostOf(canonical)
            ?: return ManualInputResult.Invalid("That link has no usable domain.")

        return ManualInputResult.Valid(
            ParsedLink(
                originalUrl = withScheme,
                canonicalUrl = canonical,
                domain = host,
                title = null,
            ),
        )
    }

    fun extractFirstUrl(text: String): String? {
        val normalizedText = text.normalizeSharedText()
        for (match in URL_IN_TEXT.findAll(normalizedText)) {
            var candidate = match.value
            while (candidate.isNotEmpty() && candidate.last() in TRAILING_JUNK) {
                val balancedParenthesis = candidate.last() == ')' &&
                    candidate.count { it == '(' } >= candidate.count { it == ')' }
                if (balancedParenthesis) break
                candidate = candidate.dropLast(1)
            }
            if (candidate.length > 8 && canonicalize(candidate) != null) return candidate
        }
        return null
    }

    fun canonicalize(raw: String): String? {
        val uri = try {
            URI(raw.trim())
        } catch (_: URISyntaxException) {
            return null
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.rawUserInfo != null) return null

        val host = uri.host?.lowercase(Locale.ROOT)
            ?: rawHost(uri.rawAuthority)
            ?: return null
        if (host.isBlank() || host.contains(' ') || host.startsWith('.') || host.endsWith('.')) return null
        if (host.length > 253) return null

        val port = uri.port
        val portPart = when {
            port == -1 -> ""
            scheme == "http" && port == 80 -> ""
            scheme == "https" && port == 443 -> ""
            port in 1..65535 -> ":$port"
            else -> return null
        }

        val path = (uri.rawPath ?: "").let { p ->
            when {
                p.isEmpty() -> "/"
                p.length > 1 && p.endsWith("/") -> p.dropLast(1)
                else -> p
            }
        }

        val query = uri.rawQuery
            ?.split('&')
            ?.filter { it.isNotBlank() }
            ?.filterNot { param ->
                val name = param.substringBefore('=').lowercase(Locale.ROOT)
                name.startsWith("utm_") || name in TRACKING_PARAMS
            }
            ?.joinToString("&")
            ?.takeIf { it.isNotEmpty() }

        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(portPart)
            append(path)
            if (query != null) {
                append('?')
                append(query)
            }
        }
    }

    fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    } catch (_: URISyntaxException) {
        null
    }

    private fun extractBareSharedUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        return when (val result = validateManualInput(trimmed)) {
            is ManualInputResult.Valid -> result.link.originalUrl
            is ManualInputResult.Invalid -> null
        }
    }

    private fun rawHost(authority: String?): String? {
        val value = authority ?: return null
        if (value.any { it == ':' || it == '_' }) return null
        return value.lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
    }

    private fun looksLikeHost(value: String): Boolean =
        !value.startsWith(".") &&
            value.contains('.') &&
            !value.contains(' ') &&
            value.substringAfterLast('.').length >= 2

    private fun String.cleanTitle(): String =
        replace(Regex("\\s+"), " ").trim().take(300)

    /**
     * Share sheets are inconsistent: some send HTML-escaped URLs, some pad text
     * with zero-width characters, and some use non-breaking spaces around links.
     * Normalize those transport quirks before extraction without changing the
     * URL's own query parameters.
     */
    private fun String.normalizeSharedText(): String =
        Parser.unescapeEntities(this, false)
            .replace(ZERO_WIDTH, "")
            .replace(UNICODE_SPACES, " ")

    sealed interface ManualInputResult {
        data class Valid(val link: ParsedLink) : ManualInputResult
        data class Invalid(val message: String) : ManualInputResult
    }
}
