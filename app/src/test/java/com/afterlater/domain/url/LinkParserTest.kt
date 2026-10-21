package com.afterlater.domain.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    @Test
    fun `shared text keeps the original url and trims sentence punctuation`() {
        val parsed = LinkParser.fromSharedContent(
            text = "Worth reading: https://example.com/watch?v=abc&utm_source=newsletter.",
            subject = "  A   shared   title  ",
        )

        requireNotNull(parsed)
        assertEquals("https://example.com/watch?v=abc&utm_source=newsletter", parsed.originalUrl)
        assertEquals("https://example.com/watch?v=abc", parsed.canonicalUrl)
        assertEquals("example.com", parsed.domain)
        assertEquals("A shared title", parsed.title)
    }

    @Test
    fun `canonical form drops fragments tracking and trailing slashes`() {
        assertEquals(
            "https://www.example.com/watch?v=abc",
            LinkParser.canonicalize("https://www.Example.com/watch/?utm_source=x&v=abc&fbclid=z#t=10"),
        )
    }

    @Test
    fun `query parameters needed by social platforms are preserved`() {
        val canonical = LinkParser.canonicalize("https://example.com/p/123?igsh=abc&utm_medium=social")

        assertEquals("https://example.com/p/123?igsh=abc", canonical)
    }

    @Test
    fun `only http and https schemes are accepted`() {
        assertNull(LinkParser.canonicalize("javascript:alert(1)"))
        assertNull(LinkParser.canonicalize("ftp://example.com/file"))
        assertNull(LinkParser.canonicalize("https://"))
        assertNull(LinkParser.fromSharedContent("no link here", null))
    }

    @Test
    fun `manual input accepts a bare host and assumes https`() {
        val result = LinkParser.validateManualInput("example.com/post")

        assertTrue(result is LinkParser.ManualInputResult.Valid)
        result as LinkParser.ManualInputResult.Valid
        assertEquals("https://example.com/post", result.link.originalUrl)
        assertEquals("https://example.com/post", result.link.canonicalUrl)
        assertEquals("example.com", result.link.domain)
    }

    @Test
    fun `manual input rejects whitespace and non-web schemes`() {
        assertTrue(LinkParser.validateManualInput("two words") is LinkParser.ManualInputResult.Invalid)
        assertTrue(LinkParser.validateManualInput("ftp://example.com") is LinkParser.ManualInputResult.Invalid)
        assertTrue(LinkParser.validateManualInput("") is LinkParser.ManualInputResult.Invalid)
    }

    @Test
    fun `balanced parentheses inside a url survive extraction`() {
        val url = "https://example.com/wiki/Foo_(bar)"

        assertEquals(url, LinkParser.extractFirstUrl("See $url)."))
    }

    @Test
    fun `a url in the share subject is used when the text has none`() {
        val parsed = LinkParser.fromSharedContent(
            text = "Read this later",
            subject = "https://example.com/post?utm_source=email",
        )

        requireNotNull(parsed)
        assertEquals("https://example.com/post?utm_source=email", parsed.originalUrl)
        assertEquals("https://example.com/post", parsed.canonicalUrl)
        assertNull(parsed.title)
    }

    @Test
    fun `an invalid first url does not hide a later valid url`() {
        val parsed = LinkParser.fromSharedContent(
            text = "Broken http://example.com:99999/path then https://example.com/good?utm_source=x",
            subject = null,
        )

        requireNotNull(parsed)
        assertEquals("https://example.com/good?utm_source=x", parsed.originalUrl)
        assertEquals("https://example.com/good", parsed.canonicalUrl)
    }

    @Test
    fun `a scheme-less url is accepted only when it is the whole shared text`() {
        val parsed = LinkParser.fromSharedContent("youtu.be/abc123", null)

        requireNotNull(parsed)
        assertEquals("https://youtu.be/abc123", parsed.originalUrl)
        assertEquals("https://youtu.be/abc123", parsed.canonicalUrl)
        assertEquals("youtu.be", parsed.domain)

        assertNull(LinkParser.fromSharedContent("watch youtu.be/abc123", null))
    }

    @Test
    fun `html share text yields the embedded link and preserves its identity`() {
        val parsed = LinkParser.fromSharedContent(
            text = """<a href="https://example.com/watch?v=abc&utm_source=share">Watch</a>""",
            subject = "A video",
        )

        requireNotNull(parsed)
        assertEquals("https://example.com/watch?v=abc&utm_source=share", parsed.originalUrl)
        assertEquals("https://example.com/watch?v=abc", parsed.canonicalUrl)
        assertEquals("A video", parsed.title)
    }

    @Test
    fun `youtube and reddit share links keep identity and drop tracking`() {
        val youtube = LinkParser.fromSharedContent("https://youtu.be/abc123?si=tracking", "A video")
        requireNotNull(youtube)
        assertEquals("https://youtu.be/abc123?si=tracking", youtube.originalUrl)
        assertEquals("https://youtu.be/abc123", youtube.canonicalUrl)

        val reddit = LinkParser.fromSharedContent(
            "https://www.reddit.com/r/android/comments/abc/title/?utm_source=share&utm_medium=android_app&share_id=123",
            "A thread",
        )
        requireNotNull(reddit)
        assertEquals(
            "https://www.reddit.com/r/android/comments/abc/title?share_id=123",
            reddit.canonicalUrl,
        )
    }

    @Test
    fun `the same link with different trackers has one canonical form`() {
        assertEquals(
            LinkParser.canonicalize("https://example.com/p?utm_source=a"),
            LinkParser.canonicalize("https://example.com/p/?fbclid=b#section"),
        )
    }

    @Test
    fun `invalid ports and credentials are rejected`() {
        assertNull(LinkParser.canonicalize("https://example.com:abc/path"))
        assertNull(LinkParser.canonicalize("https://user:pass@example.com/path"))
        assertTrue(LinkParser.validateManualInput("user@example.com") is LinkParser.ManualInputResult.Invalid)
    }

    @Test
    fun `html escaped and padded share text is normalized before parsing`() {
        val parsed = LinkParser.fromSharedContent(
            text = "\u200B<a href=\"https://www.instagram.com/reel/abc?igsh=xyz&amp;utm_source=share\">Watch</a>\u00A0",
            subject = "A reel",
        )

        requireNotNull(parsed)
        assertEquals(
            "https://www.instagram.com/reel/abc?igsh=xyz&utm_source=share",
            parsed.originalUrl,
        )
        assertEquals("https://www.instagram.com/reel/abc?igsh=xyz", parsed.canonicalUrl)
        assertEquals("www.instagram.com", parsed.domain)
    }

    @Test
    fun `zero width characters around a shared link do not hide it`() {
        val parsed = LinkParser.fromSharedContent(
            text = "Look\u00A0at this\u200B https://youtu.be/abc123\u200B",
            subject = null,
        )

        requireNotNull(parsed)
        assertEquals("https://youtu.be/abc123", parsed.originalUrl)
        assertEquals("https://youtu.be/abc123", parsed.canonicalUrl)
    }

    @Test
    fun `markdown and quoted links are extracted without sentence punctuation`() {
        val parsed = LinkParser.fromSharedContent(
            text = "**Watch this** (https://www.youtube.com/watch?v=abc&feature=share).",
            subject = "A video",
        )

        requireNotNull(parsed)
        assertEquals("https://www.youtube.com/watch?v=abc&feature=share", parsed.originalUrl)
        assertEquals("https://www.youtube.com/watch?v=abc&feature=share", parsed.canonicalUrl)
    }

    @Test
    fun `a title in the text does not hide a url in the subject`() {
        val parsed = LinkParser.fromSharedContent(
            text = "A post worth returning to",
            subject = "https://www.reddit.com/r/android/comments/abc/title/?utm_source=share",
        )

        requireNotNull(parsed)
        assertEquals(
            "https://www.reddit.com/r/android/comments/abc/title/?utm_source=share",
            parsed.originalUrl,
        )
        assertEquals(
            "https://www.reddit.com/r/android/comments/abc/title",
            parsed.canonicalUrl,
        )
    }
}
