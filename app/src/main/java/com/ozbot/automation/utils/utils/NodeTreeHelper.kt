package com.ozbot.automation.utils

import android.view.accessibility.AccessibilityNodeInfo

object NodeTreeHelper {

    inline fun <T> withNodeTree(
        root: AccessibilityNodeInfo,
        maxDepth: Int = 20,
        action: (node: AccessibilityNodeInfo) -> T?
    ): T? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        try {
            while (queue.isNotEmpty()) {
                val (node, depth) = queue.removeFirst()
                try {
                    val result = action(node)
                    if (result != null) return result

                    if (depth < maxDepth) {
                        for (i in 0 until node.childCount) {
                            node.getChild(i)?.let { queue.add(it to depth + 1) }
                        }
                    }
                } catch (_: Exception) {}
            }
        } finally {
            queue.forEach { (node, _) ->
                if (node != root) {
                    try { node.recycle() } catch (_: Exception) {}
                }
            }
        }

        return null
    }

    fun collectNodes(
        root: AccessibilityNodeInfo,
        maxResults: Int = 50,
        maxDepth: Int = 20,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()

        queue.add(root to 0)

        try {
            while (queue.isNotEmpty() && result.size < maxResults) {
                val (node, depth) = queue.removeFirst()
                try {
                    if (predicate(node)) {
                        result.add(node)
                    } else {
                        toRecycle.add(node)
                    }

                    if (depth < maxDepth) {
                        for (i in 0 until node.childCount) {
                            node.getChild(i)?.let { queue.add(it to depth + 1) }
                        }
                    }
                } catch (_: Exception) {
                    toRecycle.add(node)
                }
            }

            queue.forEach { (node, _) ->
                if (node !in result) toRecycle.add(node)
            }
        } finally {
            toRecycle.forEach {
                if (it != root) {
                    try { it.recycle() } catch (_: Exception) {}
                }
            }
        }

        return result
    }

    fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (_: Exception) {}
    }

    fun safeRecycleAll(nodes: List<AccessibilityNodeInfo>?) {
        nodes?.forEach { safeRecycle(it) }
    }
}