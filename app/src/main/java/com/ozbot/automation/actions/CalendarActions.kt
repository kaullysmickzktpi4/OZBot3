package com.ozbot.automation.actions

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.ScreenDetector
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.NodeTreeHelper
import com.ozbot.bot.DomUtils
import com.ozbot.data.UserPreferences
import com.ozbot.automation.navigation.GestureHelper
import com.ozbot.automation.navigation.NavigationHelper

class CalendarActions(
    private val prefs: UserPreferences,
    private val stateManager: StateManager,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val navigationHelper: NavigationHelper,
    private val screenDetector: ScreenDetector,
    private val findOzonRoot: () -> AccessibilityNodeInfo?
) {

    private val handler = Handler(Looper.getMainLooper())

    fun handleCalendar(root: AccessibilityNodeInfo): List<String> {
        val availableDates = mutableListOf<String>()

        try {
            val targetDates = prefs.targetDates
            if (targetDates.isEmpty()) {
                exitCalendarAndRefresh(root)
                return emptyList()
            }

            val targetDaysByMonth = buildTargetDaysByMonth()
            if (targetDaysByMonth.isEmpty()) {
                logger.w("No valid target dates parsed from settings: $targetDates")
                exitCalendarAndRefresh(root)
                return emptyList()
            }

            if (!handleMonthNavigation(root, targetDaysByMonth.keys)) return emptyList()

            val currentMonth = getCurrentCalendarMonth(root)
            val targetDays = currentMonth?.let { targetDaysByMonth[it] }.orEmpty()

            if (targetDays.isEmpty()) {
                logger.d("No target days in current month, trying to move forward")
                if (currentMonth != null && tryNavigateToNextTargetMonth(root, currentMonth, targetDaysByMonth.keys)) {
                    return emptyList()
                }
                exitCalendarAndRefresh(root)
                return emptyList()
            }

            val (availableContainers, foundDates) = findAvailableShiftContainers(root)
            availableDates.addAll(foundDates)

            try {
                val availableDays = availableContainers.mapNotNull {
                    extractDayFromContainer(it)
                }.toSet()

                val daysToBook = targetDays.intersect(availableDays)

                if (daysToBook.isEmpty()) {
                    logger.d("No target days in current month. Target: $targetDays, Available: $availableDays")

                    if (currentMonth != null && tryNavigateToNextTargetMonth(root, currentMonth, targetDaysByMonth.keys)) {
                        return availableDates
                    }

                    exitCalendarAndRefresh(root)
                    return availableDates
                }

                for (day in daysToBook.sorted()) {
                    val container = availableContainers.firstOrNull {
                        extractDayFromContainer(it) == day
                    }

                    if (container != null && gestureHelper.tryClickNode(container)) {
                        val month = (currentMonth ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) + 1
                        val selectedDate = String.format(java.util.Locale.US, "%02d.%02d", day, month)
                        stateManager.lastSelectedBookingDate = selectedDate
                        logger.d("✅ Clicked day $day ($selectedDate)")

                        handler.postDelayed({
                            val postRoot = findOzonRoot()
                            try {
                                if (postRoot != null && screenDetector.isTimePickerModal(postRoot)) {
                                    logger.d("🎉 Time picker opened!")
                                }
                            } finally {
                                NodeTreeHelper.safeRecycle(postRoot)
                            }
                        }, 100L)

                        NodeTreeHelper.safeRecycleAll(availableContainers)
                        return availableDates
                    }
                }

                exitCalendarAndRefresh(root)
                return availableDates

            } finally {
                NodeTreeHelper.safeRecycleAll(availableContainers)
            }

        } catch (e: Exception) {
            logger.e("handleCalendar error: ${e.message}")
            exitCalendarAndRefresh(root)
            return emptyList()
        }
    }

    private fun findAvailableShiftContainers(root: AccessibilityNodeInfo): Pair<List<AccessibilityNodeInfo>, List<String>> {
        val availableDates = mutableListOf<String>()
        val today = java.util.Calendar.getInstance()
        val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH)
        val todayMonth = today.get(java.util.Calendar.MONTH) // 0-11
        val todayYear = today.get(java.util.Calendar.YEAR)

        val currentCalendarMonth = getCurrentCalendarMonth(root) ?: todayMonth

        logger.d("📅 Today: $todayDay.${todayMonth + 1}.$todayYear | Calendar showing: ${currentCalendarMonth + 1}")

        val containers = NodeTreeHelper.collectNodes(root, maxResults = 31) { node ->
            val resourceId = node.viewIdResourceName ?: ""

            if (!resourceId.contains("name=availableShift]") ||
                resourceId.contains("unavailable", ignoreCase = true) ||
                !node.isClickable) {
                return@collectNodes false
            }

            // ✅ ПРОВЕРЯЕМ: это прошедший день?
            val day = extractDayFromContainer(node)
            if (day != null) {
                val isPast = isPastDate(day, currentCalendarMonth, todayYear, todayDay, todayMonth, todayYear)
                val isToday = (day == todayDay && currentCalendarMonth == todayMonth)

                when {
                    isPast -> {
                        logger.d("🚫 Skipping PAST date: $day (gray, unclickable)")
                        false
                    }
                    isToday -> {
                        logger.d("✅ Found TODAY date: $day")
                        availableDates.add(String.format("%02d.%02d", day, currentCalendarMonth + 1))
                        true
                    }
                    else -> {
                        logger.d("✅ Found future date: $day")
                        availableDates.add(String.format("%02d.%02d", day, currentCalendarMonth + 1))
                        true
                    }
                }
            } else {
                false
            }
        }

        logger.d("Found ${containers.size} clickable available shifts: $availableDates")
        return containers to availableDates
    }

    /**
     * Проверка: является ли дата прошедшей
     */
    private fun isPastDate(
        day: Int,
        month: Int,
        year: Int,
        todayDay: Int,
        todayMonth: Int,
        todayYear: Int
    ): Boolean {
        return when {
            year < todayYear -> true
            year > todayYear -> false
            month < todayMonth -> true
            month > todayMonth -> false
            day < todayDay -> true
            else -> false
        }
    }

    private fun extractDayFromContainer(container: AccessibilityNodeInfo): Int? {
        val dayRegex = Regex("^\\d{1,2}$")

        for (i in 0 until container.childCount) {
            val child = try { container.getChild(i) } catch (_: Exception) { null } ?: continue
            try {
                val text = child.text?.toString()?.trim()
                if (text != null && dayRegex.matches(text)) {
                    return text.toIntOrNull()
                }
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }

        val text = container.text?.toString()?.trim()
        if (text != null && dayRegex.matches(text)) {
            return text.toIntOrNull()
        }

        return null
    }

    private fun handleMonthNavigation(root: AccessibilityNodeInfo, targetMonths: Set<Int>): Boolean {
        if (!stateManager.monthClicked.get()) return true

        val currentMonth = getCurrentCalendarMonth(root)
        if (currentMonth == null) return false

        stateManager.pendingMonthTarget?.let { pending ->
            if (currentMonth == pending) {
                stateManager.pendingMonthTarget = null
                stateManager.monthClicked.set(false)
                stateManager.lastMonthText = null
                return true
            }

            return false
        }

        val previousText = stateManager.lastMonthText
        val currentText = findMonthTextNode(root)?.text?.toString()
        if (previousText != null && currentText != null && previousText != currentText) {
            stateManager.monthClicked.set(false)
            stateManager.lastMonthText = null
            return true
        }

        val nextTarget = getNextTargetMonth(currentMonth, targetMonths) ?: return true

        stateManager.pendingMonthTarget?.let { pending ->
            if (currentMonth == pending) {
                stateManager.pendingMonthTarget = null
                stateManager.monthClicked.set(false)
                stateManager.lastMonthText = null
            } else {
                return false
            }
        }

        stateManager.pendingMonthTarget = (currentMonth + 1) % 12
        stateManager.lastMonthText = findMonthTextNode(root)?.text?.toString()

        if (findAndClickNextMonthButton(root)) {
            stateManager.monthClicked.set(true)
            gestureHelper.updateLastClickTime()
            logger.d("Switched calendar month while searching target month. Current=$currentMonth target=$nextTarget")
            return false
        }

        stateManager.pendingMonthTarget = null
        return true
    }

    private fun tryNavigateToNextTargetMonth(root: AccessibilityNodeInfo, currentMonth: Int, targetMonths: Set<Int>): Boolean {
        val nextTarget = getNextTargetMonth(currentMonth, targetMonths) ?: return false

        stateManager.pendingMonthTarget = (currentMonth + 1) % 12
        stateManager.lastMonthText = findMonthTextNode(root)?.text?.toString()

        if (findAndClickNextMonthButton(root)) {
            stateManager.monthClicked.set(true)
            gestureHelper.updateLastClickTime()
            logger.d("Switched month forward to continue scan. Current=$currentMonth target=$nextTarget")
            return true
        }

        stateManager.pendingMonthTarget = null
        return false
    }

    private fun exitCalendarAndRefresh(root: AccessibilityNodeInfo) {
        stateManager.exitingCalendar.set(true)
        gestureHelper.updateLastClickTime()
        stateManager.lastStepTime = System.currentTimeMillis()
        navigationHelper.goToWarehousesSmart(root)
        stateManager.monthClicked.set(false)
        stateManager.lastMonthText = null
        stateManager.pendingMonthTarget = null
    }

    private fun findMonthTextNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val months = listOf(
            "январ", "феврал", "март", "апрел", "май", "июн",
            "июл", "август", "сентябр", "октябр", "ноябр", "декабр"
        )

        for (m in months) {
            DomUtils.findNodeByText(root, m)?.let { return it }
        }

        return null
    }

    private fun getCurrentCalendarMonth(root: AccessibilityNodeInfo): Int? {
        val text = findMonthTextNode(root)?.text?.toString()?.lowercase() ?: return null

        return when {
            text.contains("январ") -> 0
            text.contains("феврал") -> 1
            text.contains("март") -> 2
            text.contains("апрел") -> 3
            text.contains("май") -> 4
            text.contains("июн") -> 5
            text.contains("июл") -> 6
            text.contains("август") -> 7
            text.contains("сентябр") -> 8
            text.contains("октябр") -> 9
            text.contains("ноябр") -> 10
            text.contains("декабр") -> 11
            else -> null
        }
    }

    private fun buildTargetDaysByMonth(): Map<Int, Set<Int>> {
        val byMonth = linkedMapOf<Int, MutableSet<Int>>()

        prefs.targetDates.forEach { date ->
            val parts = date.split(".")
            val day = parts.getOrNull(0)?.toIntOrNull()
            val month = parts.getOrNull(1)?.toIntOrNull()?.minus(1)

            if (day == null || month == null || month !in 0..11 || day !in 1..31) return@forEach

            byMonth.getOrPut(month) { linkedSetOf() }.add(day)
        }

        return byMonth
    }

    private fun getNextTargetMonth(currentMonth: Int, targetMonths: Set<Int>): Int? {
        if (targetMonths.isEmpty()) return null

        return targetMonths
            .map { target -> target to ((target - currentMonth + 12) % 12) }
            .filter { (_, distance) -> distance > 0 }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    private fun findAndClickNextMonthButton(root: AccessibilityNodeInfo): Boolean {
        try {
            val monthNode = findMonthTextNode(root)
            if (monthNode != null) {
                val parent = try { monthNode.parent } catch (_: Exception) { null }

                if (parent != null) {
                    val monthRect = Rect()
                    try { monthNode.getBoundsInScreen(monthRect) } catch (_: Exception) {}

                    for (i in 0 until parent.childCount) {
                        var child: AccessibilityNodeInfo? = null
                        try {
                            child = parent.getChild(i)
                            if (child != null && child.isClickable) {
                                val childRect = Rect()
                                child.getBoundsInScreen(childRect)

                                if (childRect.left >= monthRect.right - 8 || monthRect.right == 0) {
                                    if (DomUtils.clickNode(child)) return true
                                }
                            }
                        } finally {
                            NodeTreeHelper.safeRecycle(child)
                        }
                    }
                }
            }

            val rootRect = Rect()
            try {
                root.getBoundsInScreen(rootRect)
            } catch (_: Exception) {
                rootRect.set(0, 0, 1080, 1920)
            }

            NodeTreeHelper.withNodeTree(root, maxDepth = 15) { node ->
                val cls = node.className?.toString() ?: ""
                if (cls.contains("Button", ignoreCase = true) || node.isClickable) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)

                    val inTopArea = rect.top in 0..(rootRect.height() / 3)
                    val onRight = rect.centerX() > rootRect.centerX()

                    if (inTopArea && onRight && node.isClickable) {
                        if (DomUtils.clickNode(node)) {
                            return@withNodeTree true
                        }
                    }
                }
                null
            }?.let { return true }

        } catch (e: Exception) {
            logger.e("findAndClickNextMonthButton error: ${e.message}")
        }

        return false
    }
}