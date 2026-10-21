package com.afterlater.data.metadata

import com.afterlater.core.model.ItemMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface MetadataResult {
    data class Success(val metadata: ItemMetadata) : MetadataResult
    data class Unavailable(val retryable: Boolean) : MetadataResult
}

/**
 * Best-effort Open Graph / HTML metadata fetch.
 *
 * A failure here is never a failed save. Redirects are followed manually so every hop
 * can be validated, DNS answers are filtered by [SafeDns], responses are capped at
 * [MAX_BYTES], and only HTML is accepted. No JavaScript is executed and no media is
 * downloaded.
 */
class MetadataExtractor(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun extract(rawUrl: String): MetadataResult = withContext(Dispatchers.IO) {
        extractBlocking(rawUrl)
    }

    private fun extractBlocking(rawUrl: String): MetadataResult {
        val start = rawUrl.toHttpUrlOrNull() ?: return MetadataResult.Unavailable(false)
        if (!isAllowedWebUrl(start)) return MetadataResult.Unavailable(false)

        var current = start
        var redirects = 0

        while (true) {
            when (val hop = request(current)) {
                is Hop.Redirect -> {
                    redirects++
                    if (redirects > MAX_REDIRECTS) return MetadataResult.Unavailable(false)
                    val next = current.resolve(hop.location)
                        ?: return MetadataResult.Unavailable(false)
                    if (!isAllowedWebUrl(next)) return MetadataResult.Unavailable(false)
                    current = next
                }

                is Hop.Failure -> return MetadataResult.Unavailable(hop.retryable)

                is Hop.Body -> {
                    val metadata = parse(hop.bytes, current)
                        ?: return MetadataResult.Unavailable(false)
                    return MetadataResult.Success(metadata)
                }
            }
        }
    }

    private fun request(url: HttpUrl): Hop {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
            .header("Accept-Language", "en;q=0.8")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (_: IOException) {
            return Hop.Failure(retryable = true)
        }

        response.use { resp ->
            if (resp.isRedirect) {
                val location = resp.header("Location")
                    ?: return Hop.Failure(retryable = false)
                return Hop.Redirect(location)
            }

            if (resp.code == 429 || resp.code >= 500) return Hop.Failure(retryable = true)
            if (!resp.isSuccessful) return Hop.Failure(retryable = false)

            val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
            val isHtml = contentType.contains("text/html") || contentType.contains("application/xhtml+xml")
            if (!isHtml) return Hop.Failure(retryable = false)

            val declaredLength = resp.header("Content-Length")?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_BYTES * 4L) {
                return Hop.Failure(retryable = false)
            }

            val body = resp.body ?: return Hop.Failure(retryable = false)
            val bytes = try {
                body.byteStream().use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (total < MAX_BYTES) {
                        val read = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total))
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                    out.toByteArray()
                }
            } catch (_: IOException) {
                return Hop.Failure(retryable = true)
            }

            if (bytes.isEmpty()) return Hop.Failure(retryable = false)
            return Hop.Body(bytes)
        }
    }

    private fun parse(bytes: ByteArray, baseUrl: HttpUrl): ItemMetadata? {
        return try {
            val document = Jsoup.parse(ByteArrayInputStream(bytes), null, baseUrl.toString())
            val title = firstNonBlank(
                metaContent(document, "og:title"),
                metaContent(document, "twitter:title"),
                document.title(),
            )?.truncate(300)

            val description = firstNonBlank(
                metaContent(document, "og:description"),
                metaContent(document, "description"),
                metaContent(document, "twitter:description"),
            )?.truncate(600)

            val image = firstNonBlank(
                metaContent(document, "og:image:secure_url"),
                metaContent(document, "og:image"),
                metaContent(document, "twitter:image"),
            )?.let { raw -> resolveImage(raw, baseUrl) }

            ItemMetadata(title = title, description = description, imageUrl = image)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun metaContent(document: Document, key: String): String? =
        document.selectFirst("meta[property=\"$key\"]")?.attr("content")
            ?: document.selectFirst("meta[name=\"$key\"]")?.attr("content")

    private fun resolveImage(raw: String, baseUrl: HttpUrl): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val resolved = baseUrl.resolve(trimmed) ?: return null
        if (!isAllowedWebUrl(resolved)) return null
        return resolved.toString()
    }

    private fun isAllowedWebUrl(url: HttpUrl): Boolean {
        if (url.scheme != "http" && url.scheme != "https") return false
        if (url.port != 80 && url.port != 443) return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        return HostSafety.isAllowedHostname(url.host)
    }

    private fun firstNonBlank(vararg values: String?): String? = values
        .asSequence()
        .mapNotNull { it?.cleanText() }
        .firstOrNull { it.isNotEmpty() }

    private fun String.cleanText(): String =
        replace(Regex("[\\u0000-\\u001f\\u007f]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.truncate(max: Int): String =
        if (length <= max) this else take(max - 1).trimEnd() + "\u2026"

    private sealed interface Hop {
        data class Redirect(val location: String) : Hop
        data class Body(val bytes: ByteArray) : Hop
        data class Failure(val retryable: Boolean) : Hop
    }

    private companion object {
        const val MAX_REDIRECTS = 4
        const val MAX_BYTES = 512 * 1024
        const val USER_AGENT = "AFTERLATER/1.0 (personal link archive; metadata preview)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(SafeDns)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
