package com.afterlater.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokVideoDetectorTest {

    private val detector = TikTokVideoDetector()

    @Test
    fun `full video url is detected from a share sheet`() {
        val original = "https://www.tiktok.com/@creator/video/7391234567890123456" +
            "?is_from_webapp=1&sender_device=pc"
        val nodes = listOf(
            node(text = "Share to"),
            node(text = "Copy link"),
            node(text = original),
        )

        val detected = detector.detect(nodes)

        requireNotNull(detected)
        assertEquals(original, detected.originalUrl)
        assertEquals(
            "https://www.tiktok.com/@creator/video/7391234567890123456",
            detected.canonicalUrl,
        )
        assertEquals("www.tiktok.com", detected.domain)
    }

    @Test
    fun `short vm link is normalized without guessing a video id`() {
        val nodes = listOf(
            node(contentDescription = "Copy link"),
            node(text = "https://vm.tiktok.com/ZMabc123/"),
        )

        val detected = detector.detect(nodes)

        requireNotNull(detected)
        assertEquals("https://vm.tiktok.com/ZMabc123", detected.canonicalUrl)
    }

    @Test
    fun `short tiktok t link is accepted`() {
        val nodes = listOf(
            node(text = "Share to"),
            node(text = "https://www.tiktok.com/t/ZTRabc123/?lang=en"),
        )

        val detected = detector.detect(nodes)

        requireNotNull(detected)
        assertEquals("https://www.tiktok.com/t/ZTRabc123", detected.canonicalUrl)
    }

    @Test
    fun `content description can carry the link`() {
        val nodes = listOf(
            node(text = "Copy link"),
            node(contentDescription = "https://www.tiktok.com/@creator/video/7391234567890123456"),
        )

        requireNotNull(detector.detect(nodes))
    }

    @Test
    fun `a link without a share sheet is not detected`() {
        val nodes = listOf(
            node(text = "https://www.tiktok.com/@creator/video/7391234567890123456"),
        )

        assertFalse(detector.hasShareSheet(nodes))
        assertNull(detector.detect(nodes))
    }

    @Test
    fun `profile and photo urls are rejected`() {
        val nodes = listOf(
            node(text = "Copy link"),
            node(text = "https://www.tiktok.com/@creator"),
            node(text = "https://www.tiktok.com/@creator/photo/7391234567890123456"),
        )

        assertNull(detector.detect(nodes))
    }

    @Test
    fun `non tiktok hosts are rejected`() {
        val nodes = listOf(
            node(text = "Copy link"),
            node(text = "https://not-tiktok.com/@creator/video/7391234567890123456"),
            node(text = "https://www.tiktok.com.evil.test/@creator/video/7391234567890123456"),
        )

        assertNull(detector.detect(nodes))
    }

    @Test
    fun `links inside comments are rejected`() {
        val comment = node(
            viewId = "com.zhiliaoapp.musically:id/comment_content",
            children = listOf(
                node(text = "https://www.tiktok.com/@creator/video/7391234567890123456"),
            ),
        )
        val nodes = listOf(node(text = "Copy link"), comment)

        assertNull(detector.detect(nodes))
    }

    @Test
    fun `invisible links are ignored`() {
        val nodes = listOf(
            node(text = "Copy link"),
            node(
                text = "https://www.tiktok.com/@creator/video/7391234567890123456",
                isVisibleToUser = false,
            ),
        )

        assertNull(detector.detect(nodes))
    }

    @Test
    fun `two different links are treated as ambiguous`() {
        val nodes = listOf(
            node(text = "Copy link"),
            node(text = "https://www.tiktok.com/@one/video/7391234567890123456"),
            node(text = "https://www.tiktok.com/@two/video/7391234567890123457"),
        )

        assertNull(detector.detect(nodes))
    }

    @Test
    fun `visible text includes extracted labels for debug logs`() {
        val nodes = listOf(
            node(
                text = "Copy link",
                viewId = "com.zhiliaoapp.musically:id/share_copy",
            ),
        )

        val text = detector.visibleText(nodes)

        assertTrue(text.contains("text=Copy link"))
        assertTrue(text.contains("id=com.zhiliaoapp.musically:id/share_copy"))
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null,
        isVisibleToUser: Boolean = true,
        children: List<TikTokNodeSnapshot> = emptyList(),
    ) = TikTokNodeSnapshot(
        text = text,
        contentDescription = contentDescription,
        viewId = viewId,
        isVisibleToUser = isVisibleToUser,
        children = children,
    )
}
