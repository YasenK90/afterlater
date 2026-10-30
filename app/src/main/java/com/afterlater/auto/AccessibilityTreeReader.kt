package com.afterlater.auto

import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityTreeReader {

    fun read(root: AccessibilityNodeInfo): TikTokNodeSnapshot {
        val remaining = intArrayOf(MAX_NODES)
        return readNode(root, remaining) ?: TikTokNodeSnapshot()
    }

    private fun readNode(
        node: AccessibilityNodeInfo,
        remaining: IntArray,
    ): TikTokNodeSnapshot? {
        if (remaining[0]-- <= 0) return null

        val children = buildList {
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                readNode(child, remaining)?.let(::add)
            }
        }
        val password = runCatching { node.isPassword }.getOrDefault(false)
        return TikTokNodeSnapshot(
            text = if (password) null else node.text?.toString(),
            contentDescription = if (password) null else node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            isVisibleToUser = node.isVisibleToUser,
            children = children,
        )
    }

    private const val MAX_NODES = 800
}
