package com.ozbot.automation.utils

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Утилиты для поиска и работы с AccessibilityNodeInfo
 */
object NodeSearchHelper {

    /**
     * Поиск ноды по resource ID
     */
    fun findByResourceId(root: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        if (root.viewIdResourceName == resourceId) return root

        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findByResourceId(child, resourceId)?.let { return it }
                child.recycle()
            }
        }
        return null
    }

    /**
     * Поиск ноды по тексту
     */
    fun findByText(root: AccessibilityNodeInfo, text: String, exactMatch: Boolean = false): AccessibilityNodeInfo? {
        val nodeText = root.text?.toString()
        val matches = if (exactMatch) {
            nodeText == text
        } else {
            nodeText?.contains(text, ignoreCase = true) == true
        }

        if (matches) return root

        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findByText(child, text, exactMatch)?.let { return it }
                child.recycle()
            }
        }
        return null
    }

    /**
     * Поиск всех нод по className
     */
    fun findAllByClassName(root: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findAllByClassNameRecursive(root, className, results)
        return results
    }

    private fun findAllByClassNameRecursive(
        node: AccessibilityNodeInfo,
        className: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className == className) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAllByClassNameRecursive(child, className, results)
            }
        }
    }

    /**
     * Поиск кликабельного родителя
     */
    fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable && current.isEnabled) {
                return current
            }
            current = current.parent
        }

        return null
    }

    /**
     * Получение всех кликабельных элементов
     */
    fun findAllClickable(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findAllClickableRecursive(root, results)
        return results
    }

    private fun findAllClickableRecursive(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isClickable && node.isEnabled) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAllClickableRecursive(child, results)
            }
        }
    }

    /**
     * Логирование структуры дерева нод
     */
    fun logNodeTree(node: AccessibilityNodeInfo, indent: String = "", maxDepth: Int = 5) {
        if (maxDepth <= 0) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        android.util.Log.d("NodeTree", "$indent${node.className} | " +
                "text: ${node.text} | " +
                "id: ${node.viewIdResourceName} | " +
                "clickable: ${node.isClickable} | " +
                "bounds: $bounds")

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                logNodeTree(child, "$indent  ", maxDepth - 1)
                child.recycle()
            }
        }
    }

    /**
     * Безопасный recycle списка нод
     */
    fun recycleNodes(nodes: List<AccessibilityNodeInfo>) {
        nodes.forEach { it.recycle() }
    }
}