package com.ozbot.bot

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

object DomUtils {

    fun hasText(root: AccessibilityNodeInfo, text: String): Boolean {
        return findNodeByText(root, text) != null
    }

    // ✅ FIX #1: recycle child-нод которые не вернули результат
    fun findNodeByText(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) {
                // child == found или child — предок found, не recycle
                return found
            } else {
                // child не нужен — освобождаем
                try { child.recycle() } catch (_: Exception) {}
            }
        }

        return null
    }

    // ✅ FIX #2: recycle child-нод которые не вернули результат
    fun findNodeByDesc(
        node: AccessibilityNodeInfo,
        desc: String
    ): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDesc(child, desc)
            if (found != null) {
                return found
            } else {
                try { child.recycle() } catch (_: Exception) {}
            }
        }

        return null
    }

    // ✅ FIX #3: recycle промежуточные child-ноды (не добавленные в results)
    fun findAllNodesByText(
        node: AccessibilityNodeInfo,
        text: String,
        results: MutableList<AccessibilityNodeInfo> = mutableListOf()
    ): List<AccessibilityNodeInfo> {
        val matches = node.text?.toString()?.contains(text, ignoreCase = true) == true
        if (matches) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val sizeBefore = results.size
            findAllNodesByText(child, text, results)
            // Если child сам не попал в results и не добавил ничего нового — recycle
            if (results.size == sizeBefore && !results.contains(child)) {
                try { child.recycle() } catch (_: Exception) {}
            }
        }

        return results
    }

    // findClickableParent — не вызывает getChild, идёт вверх по .parent, recycle не нужен
    fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node

        var current = node.parent
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    // ✅ FIX #4: recycle child-нод которые не scrollable
    fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child)
            if (found != null) {
                return found
            } else {
                try { child.recycle() } catch (_: Exception) {}
            }
        }

        return null
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun scrollDown(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        tapNodeByBounds(service, node)
    }

    fun tapNodeByBounds(service: AccessibilityService, node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val x = (rect.left + rect.right) / 2f
        val y = (rect.top + rect.bottom) / 2f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path()
            path.moveTo(x, y)

            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
                )
                .build()

            service.dispatchGesture(gesture, null, null)
        }
    }
}