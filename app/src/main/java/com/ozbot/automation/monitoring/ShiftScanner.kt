package com.ozbot.automation.monitoring

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.NodeTreeHelper
import com.ozbot.bot.DomUtils
import com.ozbot.data.UserPreferences
import kotlinx.coroutines.CoroutineScope

class ShiftScanner(
    private val prefs: UserPreferences,
    private val logger: Logger,
    private val shiftMonitor: ShiftMonitor,
    private val scope: CoroutineScope?
) {

    fun scanProcessScreen(root: AccessibilityNodeInfo) {
        try {
            logger.d("🔍 Scanning process screen for available dates...")

            val myTargetDates = prefs.targetDates.toSet()
            val myProcess = prefs.process

            if (myProcess.isBlank()) {
                logger.w("No target process configured")
                return
            }

            logger.d("Looking for available dates in: $myProcess")

            try {
                scanProcessDates(root, myProcess, myTargetDates)
            } catch (e: Exception) {
                logger.e("Error scanning $myProcess: ${e.message}")
            }

            shiftMonitor.cleanup()

        } catch (e: Exception) {
            logger.e("scanProcessScreen error: ${e.message}", e)
        }
    }

    private fun scanProcessDates(
        root: AccessibilityNodeInfo,
        processName: String,
        myTargetDates: Set<String>
    ) {
        val processNodes = DomUtils.findAllNodesByText(root, processName)
        if (processNodes.isEmpty()) return

        for (processNode in processNodes) {
            try {
                val processRect = Rect()
                processNode.getBoundsInScreen(processRect)

                val datesLabel = findDatesLabelNear(root, processRect)
                if (datesLabel != null) {
                    val datesRect = Rect()
                    datesLabel.getBoundsInScreen(datesRect)
                    val availableDates = findGreenDatesNear(root, datesRect)
                    if (availableDates.isNotEmpty()) {
                        logger.d("    Available dates for $processName: $availableDates")
                    }
                }
            } catch (e: Exception) {
                logger.e("Error processing $processName node: ${e.message}")
            }
        }
    }

    private fun findDatesLabelNear(root: AccessibilityNodeInfo, processRect: Rect): AccessibilityNodeInfo? {
        val datesNodes = DomUtils.findAllNodesByText(root, "Даты:")
        for (node in datesNodes) {
            try {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (kotlin.math.abs(rect.centerY() - processRect.centerY()) < 200) {
                    return node
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun findGreenDatesNear(root: AccessibilityNodeInfo, datesRect: Rect): List<String> {
        val result = mutableListOf<String>()
        NodeTreeHelper.withNodeTree(root, maxDepth = 20) { node ->
            try {
                val text = node.text?.toString() ?: ""
                if (text.matches(Regex("\\d{2}\\.\\d{2}"))) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val sameRow = kotlin.math.abs(rect.centerY() - datesRect.centerY()) < 50
                    val isRight = rect.left > datesRect.right
                    if (sameRow && isRight) result.add(text)
                }
            } catch (_: Exception) {}
            null
        }
        return result
    }

    private fun convertToFullDate(shortDate: String): String {
        return try {
            val parts = shortDate.split(".")
            if (parts.size == 2) {
                val day = parts[0]
                val month = parts[1]
                val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                "$day.$month.$year"
            } else shortDate
        } catch (e: Exception) {
            shortDate
        }
    }

    fun notifyAboutShifts(dates: List<String>) {
        for (date in dates) {
            val fullDate = convertToFullDate(date)
            val shift = AvailableShift(
                process = prefs.process,
                date = fullDate,
                warehouse = prefs.warehouse,
                firstSeenTime = System.currentTimeMillis(),
                lastSeenTime = System.currentTimeMillis()
            )
            shiftMonitor.updateShift(shift)
        }
    }
}