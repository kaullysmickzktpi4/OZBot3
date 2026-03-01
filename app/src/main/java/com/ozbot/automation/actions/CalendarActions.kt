package com.ozbot.automation.actions

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.ScreenDetector
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.NodeTreeHelper
import com.ozbot.bot.DomUtils
import com.ozbot.data.UserPreferences
import com.ozbot.data.repository.BookingRepository
import com.ozbot.automation.navigation.GestureHelper
import com.ozbot.automation.navigation.NavigationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarActions(
    private val prefs: UserPreferences,
    private val stateManager: StateManager,
    private val repo: BookingRepository,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val navigationHelper: NavigationHelper,
    private val screenDetector: ScreenDetector,
    private val findOzonRoot: () -> AccessibilityNodeInfo?,
    private val scope: CoroutineScope? = null
) {

    companion object {
        private const val RES_AVAILABLE = "HireContainer[name=availableShift]"
        private const val RES_QUEUE     = "HireContainer[name=queueShift]"
        private const val RES_DAY_TAKEN = "HireContainer[name=dayShift]"
        private const val BOOKED_DATES_CACHE_MS = 30_000L
    }

    // ✅ FIX: кэш вместо runBlocking — поля внутри класса
    @Volatile private var cachedBookedDates: List<String> = emptyList()
    @Volatile private var lastBookedDatesFetch = 0L

    fun handleCalendar(root: AccessibilityNodeInfo): List<String> {
        try {
            val targetDates = prefs.targetDates
            if (targetDates.isEmpty()) {
                logger.d("No target dates set, exiting calendar")
                exitCalendarAndRefresh(root)
                return emptyList()
            }

            val targetDaysByMonth = buildTargetDaysByMonth()
            if (targetDaysByMonth.isEmpty()) {
                logger.w("No valid target dates parsed: $targetDates")
                exitCalendarAndRefresh(root)
                return emptyList()
            }

            val currentMonth = getCurrentCalendarMonth(root)
            if (currentMonth == null) {
                logger.w("Cannot determine current calendar month")
                exitCalendarAndRefresh(root)
                return emptyList()
            }

            logger.d("📅 Calendar month: ${currentMonth + 1}, target months: ${targetDaysByMonth.keys.map { it + 1 }}")

            if (!targetDaysByMonth.containsKey(currentMonth)) {
                logger.d("Month ${currentMonth + 1} not in targets, navigating forward...")
                if (!navigateToNextMonth(root)) {
                    logger.w("Cannot navigate to next month, exiting")
                    exitCalendarAndRefresh(root)
                }
                return emptyList()
            }

            val targetDays = targetDaysByMonth[currentMonth] ?: emptySet()
            logger.d("Target days in month ${currentMonth + 1}: $targetDays")

            val takenDaysInUI = findTakenDaysInUI(root)
            if (takenDaysInUI.isNotEmpty()) {
                logger.d("⏭ Days already taken in UI (dayShift): $takenDaysInUI — will skip")
            }

            // ✅ FIX: не блокируем main thread — обновляем кэш в IO потоке
            val now = System.currentTimeMillis()
            if (now - lastBookedDatesFetch > BOOKED_DATES_CACHE_MS) {
                scope?.launch(Dispatchers.IO) {
                    cachedBookedDates = try {
                        repo.getBookedDates()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    lastBookedDatesFetch = System.currentTimeMillis()
                }
            }
            val alreadyBookedDates = cachedBookedDates
            logger.d("Already booked in DB (cached): $alreadyBookedDates")

            val availableNodes = findAvailableShiftNodes(root)
            logger.d("Found ${availableNodes.size} available/queue shift nodes")

            if (availableNodes.isEmpty()) {
                logger.d("No available shifts in month ${currentMonth + 1}")
                val hasNextTarget = targetDaysByMonth.keys.any { it > currentMonth }
                if (hasNextTarget) {
                    logger.d("Navigating to next month for more targets...")
                    if (!navigateToNextMonth(root)) exitCalendarAndRefresh(root)
                } else {
                    exitCalendarAndRefresh(root)
                }
                return emptyList()
            }

            val availableDays = mutableMapOf<Int, AccessibilityNodeInfo>()
            val availableDateStrings = mutableListOf<String>()

            for (node in availableNodes) {
                val day = extractDayFromNode(node)
                if (day != null) {
                    availableDays[day] = node
                    availableDateStrings.add(
                        String.format(java.util.Locale.US, "%02d.%02d", day, currentMonth + 1)
                    )
                    logger.d("✅ Available day: $day")
                }
            }

            logger.d("Available days: ${availableDays.keys.sorted()}")
            logger.d("Target days: $targetDays")

            val daysToBook = targetDays.intersect(availableDays.keys).filter { day ->
                if (takenDaysInUI.contains(day)) {
                    logger.d("⏭ Skipping day $day — already taken in UI (dayShift)")
                    return@filter false
                }
                val dateStr = String.format(java.util.Locale.US, "%02d.%02d", day, currentMonth + 1)
                if (alreadyBookedDates.contains(dateStr)) {
                    logger.d("⏭ Skipping $dateStr — already in DB (SUCCESS/PENDING)")
                    return@filter false
                }
                true
            }.toSet()

            logger.d("Days to book (after filter): $daysToBook")

            if (daysToBook.isEmpty()) {
                logger.d("No matching days after filter, checking next month...")
                val hasNextTarget = targetDaysByMonth.keys.any { it > currentMonth }
                if (hasNextTarget) {
                    if (!navigateToNextMonth(root)) exitCalendarAndRefresh(root)
                } else {
                    exitCalendarAndRefresh(root)
                }
                NodeTreeHelper.safeRecycleAll(availableNodes)
                return availableDateStrings
            }

            for (day in daysToBook.sorted()) {
                val node = availableDays[day] ?: continue
                logger.d("🎯 Clicking day $day in month ${currentMonth + 1}")

                if (gestureHelper.tryClickNode(node)) {
                    val selectedDate = String.format(
                        java.util.Locale.US, "%02d.%02d", day, currentMonth + 1
                    )
                    stateManager.lastSelectedBookingDate = selectedDate
                    logger.d("✅ Clicked day $day ($selectedDate)")
                    gestureHelper.updateLastClickTime()
                    NodeTreeHelper.safeRecycleAll(availableNodes)
                    return availableDateStrings
                }
            }

            logger.w("Failed to click any target day")
            exitCalendarAndRefresh(root)
            NodeTreeHelper.safeRecycleAll(availableNodes)
            return availableDateStrings

        } catch (e: Exception) {
            logger.e("handleCalendar error: ${e.message}", e)
            exitCalendarAndRefresh(root)
            return emptyList()
        }
    }

    private fun findTakenDaysInUI(root: AccessibilityNodeInfo): Set<Int> {
        val taken = mutableSetOf<Int>()
        NodeTreeHelper.withNodeTree(root, maxDepth = 20) { node ->
            val resId = node.viewIdResourceName ?: ""
            if (resId == RES_DAY_TAKEN) {
                val day = extractDayFromNode(node)
                if (day != null) taken.add(day)
            }
            null
        }
        return taken
    }

    private fun findAvailableShiftNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val allowQueue = prefs.allowQueue

        NodeTreeHelper.withNodeTree(root, maxDepth = 20) { node ->
            val resId = node.viewIdResourceName ?: ""
            when {
                resId == RES_AVAILABLE && node.isClickable -> result.add(node)
                resId == RES_QUEUE && node.isClickable && allowQueue -> result.add(node)
            }
            null
        }

        logger.d("findAvailableShiftNodes: found ${result.size} (queue allowed: $allowQueue)")

        if (result.isEmpty()) {
            logger.d("Fallback: searching clickable calendar cells...")
            val takenDays = findTakenDaysInUI(root)
            NodeTreeHelper.withNodeTree(root, maxDepth = 20) { node ->
                if (node.isClickable && node.isEnabled) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val isCalendarCell = rect.width() in 100..200 && rect.height() in 100..200
                    if (isCalendarCell) {
                        val day = extractDayFromNode(node)
                        if (day != null && day in 1..31 && !takenDays.contains(day)) {
                            result.add(node)
                        }
                    }
                }
                null
            }
            logger.d("Fallback found ${result.size} calendar cells")
        }

        return result
    }

    private fun extractDayFromNode(node: AccessibilityNodeInfo): Int? {
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            try {
                val text = child.text?.toString()?.trim()
                if (text != null && text.matches(Regex("^\\d{1,2}$"))) {
                    val day = text.toIntOrNull()
                    if (day != null && day in 1..31) return day
                }
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        val text = node.text?.toString()?.trim()
        if (text != null && text.matches(Regex("^\\d{1,2}$"))) {
            return text.toIntOrNull()
        }
        return null
    }

    private fun navigateToNextMonth(root: AccessibilityNodeInfo): Boolean {
        if (findAndClickNextMonthButton(root)) {
            gestureHelper.updateLastClickTime(800L)
            logger.d("➡️ Navigated to next month")
            return true
        }
        return false
    }

    private fun exitCalendarAndRefresh(root: AccessibilityNodeInfo) {
        logger.d("🚪 Exiting calendar, going to warehouses...")
        stateManager.exitingCalendar.set(true)
        gestureHelper.updateLastClickTime()
        stateManager.lastStepTime = System.currentTimeMillis()
        navigationHelper.goToWarehousesSmart(root)
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

    // ✅ FIX: один обход дерева вместо 12
    private fun findMonthTextNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val monthPatterns = listOf(
            "январ", "феврал", "март", "апрел", "май", "июн",
            "июл", "август", "сентябр", "октябр", "ноябр", "декабр"
        )
        var found: AccessibilityNodeInfo? = null
        NodeTreeHelper.withNodeTree(root, maxDepth = 10) { node ->
            val text = node.text?.toString()?.lowercase() ?: ""
            if (monthPatterns.any { text.contains(it) }) {
                found = node
                return@withNodeTree true
            }
            null
        }
        return found
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

    private fun findAndClickNextMonthButton(root: AccessibilityNodeInfo): Boolean {
        try {
            val monthNode = findMonthTextNode(root) ?: return false
            val monthRect = Rect()
            monthNode.getBoundsInScreen(monthRect)

            val parent = try { monthNode.parent } catch (_: Exception) { null } ?: return false

            for (i in 0 until parent.childCount) {
                val child = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
                try {
                    if (child.isClickable) {
                        val rect = Rect()
                        child.getBoundsInScreen(rect)
                        if (rect.left >= monthRect.right - 8) {
                            if (DomUtils.clickNode(child)) return true
                        }
                    }
                } finally {
                    try { child.recycle() } catch (_: Exception) {}
                }
            }

            val rootRect = Rect()
            root.getBoundsInScreen(rootRect)

            var found = false
            NodeTreeHelper.withNodeTree(root, maxDepth = 10) { node ->
                if (node.isClickable) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val inTopArea = rect.top < rootRect.height() / 2
                    val onRight = rect.centerX() > rootRect.centerX()
                    if (inTopArea && onRight) {
                        if (DomUtils.clickNode(node)) {
                            found = true
                            return@withNodeTree true
                        }
                    }
                }
                null
            }
            return found

        } catch (e: Exception) {
            logger.e("findAndClickNextMonthButton error: ${e.message}", e)
            return false
        }
    }
}