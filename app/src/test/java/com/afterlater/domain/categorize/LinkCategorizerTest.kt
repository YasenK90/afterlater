package com.afterlater.domain.categorize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkCategorizerTest {

    @Test
    fun `domains pin their category outright`() {
        assertEquals("CODING", LinkCategorizer.suggest("github.com", "https://github.com/x/y", null, null))
        assertEquals("DESIGN", LinkCategorizer.suggest("dribbble.com", "https://dribbble.com/shots/1", null, null))
        assertEquals("OFFER", LinkCategorizer.suggest("amazon.de", "https://amazon.de/dp/B0", null, null))
        assertEquals("AI", LinkCategorizer.suggest("openai.com", "https://openai.com/blog", null, null))
    }

    @Test
    fun `page keywords sharpen the suggestion once metadata arrives`() {
        assertEquals(
            "CODING",
            LinkCategorizer.suggest(
                "example.com",
                "https://example.com/post",
                title = "Building a REST API with Python",
                description = "A tutorial for backend developers",
            ),
        )
        assertEquals(
            "OFFER",
            LinkCategorizer.suggest(
                "example.com",
                "https://example.com/post",
                title = "Summer Sale — 50% off everything",
                description = null,
            ),
        )
        assertEquals(
            "AI",
            LinkCategorizer.suggest(
                "example.com",
                "https://example.com/post",
                title = "ChatGPT changed how I write",
                description = "Thoughts on large language models",
            ),
        )
        assertEquals(
            "DESIGN",
            LinkCategorizer.suggest(
                "example.com",
                "https://example.com/post",
                title = "10 typography tips",
                description = "A UI design inspiration roundup",
            ),
        )
    }

    @Test
    fun `a domain match beats a single keyword for another category`() {
        assertEquals(
            "CODING",
            LinkCategorizer.suggest(
                "github.com",
                "https://github.com/x",
                title = "A great design deal",
                description = null,
            ),
        )
    }

    @Test
    fun `ambiguous or plain links suggest nothing`() {
        assertNull(LinkCategorizer.suggest("example.com", "https://example.com/article", null, null))
        assertNull(
            LinkCategorizer.suggest(
                "example.com",
                "https://example.com/a",
                title = "My weekend",
                description = "A quiet day",
            ),
        )
    }

    @Test
    fun `ties break in category order`() {
        // "design" and "coding"-adjacent keyword absent; "sale" vs "design" single hits
        // would tie at 1 — DESIGN is listed first and wins.
        assertEquals(
            "DESIGN",
            LinkCategorizer.suggest(
                "example.com",
                "https://example.com/x",
                title = "design sale",
                description = null,
            ),
        )
    }
}
