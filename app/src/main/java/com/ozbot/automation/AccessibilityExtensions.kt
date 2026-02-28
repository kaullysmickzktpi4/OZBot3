package com.ozbot.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Поиск ноды по resource ID
 */
fun AccessibilityNodeInfo.findNodeByResourceId(resourceId: String): AccessibilityNodeInfo? {
    if (this.viewIdResourceName == resourceId) return this

    for (i in 0 until childCount) {
        getChild(i)?.findNodeByResourceId(resourceId)?.let { return it }
    }
    return null
}

/**
 * Поиск ноды по тексту
 */
fun AccessibilityNodeInfo.findNodeByText(text: String): AccessibilityNodeInfo? {
    if (this.text?.toString()?.contains(text) == true) return this

    for (i in 0 until childCount) {
        getChild(i)?.findNodeByText(text)?.let { return it }
    }
    return null
}

/**
 * Поиск ноды по атрибуту
 */
fun AccessibilityNodeInfo.findNodeByAttribute(attribute: String, value: String): AccessibilityNodeInfo? {
    val matches = when (attribute) {
        "selected" -> this.isSelected == value.toBoolean()
        "clickable" -> this.isClickable == value.toBoolean()
        "enabled" -> this.isEnabled == value.toBoolean()
        else -> false
    }

    if (matches) return this

    for (i in 0 until childCount) {
        getChild(i)?.findNodeByAttribute(attribute, value)?.let { return it }
    }
    return null
}

/**
 * Получение границ ноды на экране
 */
val AccessibilityNodeInfo.boundsInScreen: Rect
    get() {
        val rect = Rect()
        getBoundsInScreen(rect)
        return rect
    }