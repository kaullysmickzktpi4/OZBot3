package com.ozbot.bot

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object DomUtils {

    fun hasText(root: AccessibilityNodeInfo, text: String): Boolean {
        return findNodeByText(root, text) != null
    }

    fun findNodeByText(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeByText(child, text)?.let { return it }
            }
        }

        return null
    }

    fun findNodeByDesc(
        node: AccessibilityNodeInfo,
        desc: String
    ): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeByDesc(child, desc)?.let { return it }
            }
        }

        return null
    }

    fun findAllNodesByText(
        node: AccessibilityNodeInfo,
        text: String,
        results: MutableList<AccessibilityNodeInfo> = mutableListOf()
    ): List<AccessibilityNodeInfo> {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAllNodesByText(child, text, results)
            }
        }

        return results
    }

    fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node

        var current = node.parent
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findScrollable(child)?.let { return it }
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

        // Gesture для клика
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path()
            path.moveTo(x, y)

            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
                .build()

            service.dispatchGesture(gesture, null, null)
        }
    }

    // ---------- Поиск по Regex текста ----------
    fun findNodeByTextRegex(
        node: AccessibilityNodeInfo?,
        regex: Regex
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.let { regex.matches(it) } == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodeByTextRegex(child, regex)?.let { return it }
        }
        return null
    }

    // ---------- Gesture-клик: сдвиг вправо для Next, двойной тап и лог ----------
    fun clickNodeByGesture(service: AccessibilityService, node: AccessibilityNodeInfo, shift: Int = 20) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // На Next/Prev чаще всего нужно кликать не по центру, а ближе к краю кнопки!
        val x = (rect.right - shift).coerceAtLeast(rect.left + shift).toFloat()
        val y = ((rect.top + rect.bottom) / 2f)

        Log.d("DomUtils", "Gesture tap coords: x=$x, y=$y, bounds=$rect")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path()
            path.moveTo(x, y)
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 180))
                .build()

            service.dispatchGesture(gesture, null, null)
            Thread.sleep(180)
            // Делаем повторный клик (иногда только второй срабатывает)
            service.dispatchGesture(gesture, null, null)
        }
    }

    // ---------- Поиск кнопки “следующий месяц” справа от monthNode ----------
    fun findNextMonthButton(root: AccessibilityNodeInfo, monthNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val monthRect = Rect()
        monthNode.getBoundsInScreen(monthRect)

        // Сначала среди братьев
        val parent = try { monthNode.parent } catch (_: Exception) { null }
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                if ("android.widget.Button" == child.className?.toString() && child.isClickable) {
                    val childRect = Rect()
                    child.getBoundsInScreen(childRect)
                    if (childRect.left > monthRect.right - 15) {
                        return child
                    }
                }
            }
        }
        // fallback: вглубь
        for (i in 0 until root.childCount) {
            val node = root.getChild(i) ?: continue
            val found = findNextMonthButton(node, monthNode)
            if (found != null) return found
        }
        return null
    }

    // ---------- Поиск кнопки “предыдущий месяц” слева от monthNode ----------
    fun findPrevMonthButton(root: AccessibilityNodeInfo, monthNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val monthRect = Rect()
        monthNode.getBoundsInScreen(monthRect)

        val parent = try { monthNode.parent } catch (_: Exception) { null }
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                if ("android.widget.Button" == child.className?.toString() && child.isClickable) {
                    val childRect = Rect()
                    child.getBoundsInScreen(childRect)
                    if (childRect.right < monthRect.left + 15) {
                        return child
                    }
                }
            }
        }
        for (i in 0 until root.childCount) {
            val node = root.getChild(i) ?: continue
            val found = findPrevMonthButton(node, monthNode)
            if (found != null) return found
        }
        return null
    }
}