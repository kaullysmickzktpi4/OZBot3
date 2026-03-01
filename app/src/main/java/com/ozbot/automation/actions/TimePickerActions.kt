package com.ozbot.automation.actions

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.NodeTreeHelper
import com.ozbot.bot.DomUtils
import com.ozbot.data.UserPreferences
import com.ozbot.data.repository.BookingRepository
import com.ozbot.data.database.BookingStatus
import com.ozbot.automation.navigation.GestureHelper
import com.ozbot.telegram.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TimePickerActions(
    private val prefs: UserPreferences,
    private val stateManager: StateManager,
    private val repo: BookingRepository,
    private val scope: CoroutineScope?,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val findOzonRoot: () -> AccessibilityNodeInfo?
) {

    private val handler = Handler(Looper.getMainLooper())

    fun handleTimePicker(root: AccessibilityNodeInfo) {
        val times = prefs.timeSlots
        if (times.isEmpty()) {
            clickCloseButtonIfExists(root)
            return
        }

        val allCheckboxes = findAllCheckboxes(root)

        try {
            if (allCheckboxes.isEmpty()) return

            for (timeSlot in times) {
                val timeStr = timeSlot.toDisplayString()
                val timeVariants = generateTimeVariants(timeStr)

                for (variant in timeVariants) {
                    var nodes = DomUtils.findAllNodesByText(root, variant)
                    if (nodes.isEmpty()) {
                        nodes = findNodesByPartialTimeMatch(root, variant)
                    }

                    if (nodes.isNotEmpty()) {
                        for (node in nodes) {
                            val checkbox = findNearestCheckbox(node, allCheckboxes)

                            if (checkbox == null) continue

                            if (checkbox.isChecked) {
                                // ✅ FIX: recycle перед return
                                NodeTreeHelper.safeRecycleAll(nodes)
                                clickBookButton(root)
                                return
                            }

                            if (tryClickCheckbox(checkbox)) {
                                logger.d("✅ Checkbox clicked")
                                // ✅ FIX: recycle перед return
                                NodeTreeHelper.safeRecycleAll(nodes)
                                handler.postDelayed({
                                    val postRoot = findOzonRoot()
                                    try {
                                        if (postRoot != null) clickBookButton(postRoot)
                                    } finally {
                                        NodeTreeHelper.safeRecycle(postRoot)
                                    }
                                }, 200L)
                                return
                            }
                        }
                        // ✅ FIX: recycle если никто не кликнул
                        NodeTreeHelper.safeRecycleAll(nodes)
                    }
                }
            }

            // Если не нашли нужное время — кликаем первый свободный чекбокс
            val firstUnchecked = allCheckboxes.firstOrNull { !it.isChecked }
            if (firstUnchecked != null && tryClickCheckbox(firstUnchecked)) {
                handler.postDelayed({
                    val postRoot = findOzonRoot()
                    try {
                        if (postRoot != null) clickBookButton(postRoot)
                    } finally {
                        NodeTreeHelper.safeRecycle(postRoot)
                    }
                }, 200L)
            }

        } finally {
            // ✅ allCheckboxes всегда recycle
            NodeTreeHelper.safeRecycleAll(allCheckboxes)
        }
    }

    private fun clickBookButton(root: AccessibilityNodeInfo) {
        val bookNodes = DomUtils.findAllNodesByText(root, "Записаться")
        if (bookNodes.isNotEmpty()) {
            val btn = bookNodes.first()
            val clickable = DomUtils.findClickableParent(btn) ?: btn

            logger.d("🎉 BOOKING!")
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // ✅ FIX: recycle ноды текста после использования
            NodeTreeHelper.safeRecycleAll(bookNodes)

            val process = prefs.process
            val date = stateManager.lastSelectedBookingDate
                ?: prefs.targetDates.firstOrNull()
                ?: "Unknown"
            val time = prefs.timeSlots.firstOrNull()?.toDisplayString() ?: "Unknown"

            TelegramBot.sendBookingSuccess(process, date, time)

            scope?.launch {
                try {
                    repo.recordBooking(prefs.warehouse, process, date, time, BookingStatus.SUCCESS)
                } catch (_: Exception) {}
            }
        }
    }

    private fun clickCloseButtonIfExists(root: AccessibilityNodeInfo) {
        NodeTreeHelper.withNodeTree(root, maxDepth = 10) { node ->
            val cls = node.className?.toString() ?: ""
            if (cls.contains("Button", ignoreCase = true) && node.isClickable) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.right > 900 && rect.top < 1100) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return@withNodeTree true
                }
            }
            null
        }
    }

    private fun generateTimeVariants(timeStr: String): List<String> {
        val variants = mutableSetOf(timeStr)

        variants.add(timeStr.replace(Regex("(^|[^0-9])0(\\d:)"), "$1$2"))
        variants.add(timeStr.replace(Regex("(^|[^0-9])(\\d:)"), "$10$2"))

        val bases = variants.toList()
        val dashes = listOf("-", "–", "—", " - ", " – ", " — ")

        for (base in bases) {
            for (d1 in dashes) {
                for (d2 in dashes) {
                    variants.add(base.replace(d1, d2))
                }
            }
        }

        return variants.take(20)
    }

    private fun findNodesByPartialTimeMatch(
        root: AccessibilityNodeInfo,
        timeStr: String
    ): List<AccessibilityNodeInfo> {
        val timeParts = timeStr.replace(Regex("[–—-]"), "-")
            .split("-")
            .map { it.trim() }
            .filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }

        if (timeParts.size < 2) return emptyList()

        val startTime = timeParts[0].trimStart('0').let {
            if (it.startsWith(":")) "0$it" else it
        }
        val endTime = timeParts[1].trimStart('0').let {
            if (it.startsWith(":")) "0$it" else it
        }

        return NodeTreeHelper.collectNodes(root, maxResults = 5) { node ->
            val text = node.text?.toString()?.replace(Regex("[–—]"), "-") ?: ""
            (text.contains(startTime) || text.contains(startTime.padStart(5, '0'))) &&
                    (text.contains(endTime) || text.contains(endTime.padStart(5, '0')))
        }
    }

    private fun findAllCheckboxes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        return NodeTreeHelper.collectNodes(root, maxResults = 10) { node ->
            node.className?.toString()?.contains("CheckBox", ignoreCase = true) == true &&
                    node.isClickable
        }
    }

    private fun findNearestCheckbox(
        timeNode: AccessibilityNodeInfo,
        checkboxes: List<AccessibilityNodeInfo>
    ): AccessibilityNodeInfo? {
        if (checkboxes.isEmpty()) return null

        val timeRect = Rect()
        try {
            timeNode.getBoundsInScreen(timeRect)
        } catch (_: Exception) {
            return null
        }

        val timeCenterY = timeRect.centerY()
        var best: AccessibilityNodeInfo? = null
        var bestDistance = Int.MAX_VALUE

        for (checkbox in checkboxes) {
            try {
                val cbRect = Rect()
                checkbox.getBoundsInScreen(cbRect)
                val distance = kotlin.math.abs(cbRect.centerY() - timeCenterY)
                if (distance <= 200 && distance < bestDistance) {
                    bestDistance = distance
                    best = checkbox
                }
            } catch (_: Exception) {}
        }

        return best
    }

    private fun tryClickCheckbox(checkbox: AccessibilityNodeInfo): Boolean {
        try {
            if (checkbox.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        } catch (_: Exception) {}

        try {
            val rect = Rect()
            checkbox.getBoundsInScreen(rect)
            if (rect.width() > 4 && rect.height() > 4) {
                if (gestureHelper.gestureTap(rect.centerX().toFloat(), rect.centerY().toFloat())) {
                    return true
                }
            }
        } catch (_: Exception) {}

        try {
            return gestureHelper.tryClickNode(checkbox)
        } catch (_: Throwable) {}

        return false
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
    }
}