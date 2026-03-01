package com.ozbot.automation.actions

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.ScreenDetector
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.NodeTreeHelper
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.bot.DomUtils
import com.ozbot.data.UserPreferences
import com.ozbot.automation.navigation.GestureHelper

class ProcessActions(
    private val prefs: UserPreferences,
    private val stateManager: StateManager,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val screenDetector: ScreenDetector,
    private val getCurrentProfile: () -> SpeedProfile,
    private val findOzonRoot: () -> AccessibilityNodeInfo?
) {

    private val handler = Handler(Looper.getMainLooper())

    fun clickProcess(root: AccessibilityNodeInfo) {
        val process = prefs.process
        if (process.isEmpty()) return

        if (screenDetector.isNoSlotsScreen(root)) {
            logger.d("🚫 No slots screen, skipping...")
            return
        }

        val nodes = DomUtils.findAllNodesByText(root, process)
        if (nodes.isNotEmpty()) {
            val node = nodes.first()
            if (!isNodeInNoSlotsSection(root, node)) {
                logger.d("✅ Clicking process '$process' (found immediately)")
                clickProcessNode(node)
                return
            } else {
                logger.d("🚫 Процесс '$process' в секции 'Нет мест'")
                return
            }
        }

        logger.d("Process '$process' not visible, scrolling (max 2x)...")
        if (fastScrollDown(root, process)) {
            logger.d("Process found after scrolling!")
        } else {
            logger.d("Process '$process' not found after scroll, giving up")
            gestureHelper.updateLastClickTime(200L)
        }
    }

    private fun clickProcessNode(node: AccessibilityNodeInfo) {
        val clickable = DomUtils.findClickableParent(node) ?: node
        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        stateManager.waitingForWarehouseLoad.set(true)
        stateManager.lastStepTime = System.currentTimeMillis()
        gestureHelper.updateLastClickTime()
        handler.postDelayed({
            stateManager.waitingForWarehouseLoad.set(false)
        }, 400L)
    }

    private fun fastScrollDown(root: AccessibilityNodeInfo, targetText: String): Boolean {
        try {
            val scrollable = DomUtils.findScrollable(root)
            if (scrollable == null) {
                logger.d("No scrollable found")
                return false
            }

            val rect = Rect()
            scrollable.getBoundsInScreen(rect)

            if (rect.width() <= 0 || rect.height() <= 0) {
                logger.d("Invalid scrollable bounds")
                return false
            }

            val maxSwipes = 2

            for (i in 0 until maxSwipes) {
                gestureHelper.performFastSwipeUp(rect)

                // ✅ FIX: заменили Thread.sleep на postDelayed через handler
                // чтобы не блокировать main thread
                var found = false
                handler.postDelayed({
                    val newRoot = findOzonRoot()
                    if (newRoot != null) {
                        val nodes = DomUtils.findAllNodesByText(newRoot, targetText)
                        NodeTreeHelper.safeRecycle(newRoot)
                        if (nodes.isNotEmpty()) {
                            logger.d("✅ Found '$targetText' after ${i + 1} swipes!")
                            found = true
                        }
                    }
                }, 80L)

                if (found) return true
            }

            logger.d("'$targetText' not found after $maxSwipes swipes")
            return false

        } catch (e: Exception) {
            logger.e("fastScrollDown error: ${e.message}")
            return false
        }
    }

    private fun isNodeInNoSlotsSection(
        root: AccessibilityNodeInfo,
        targetNode: AccessibilityNodeInfo
    ): Boolean {
        val noSlotsNodes = DomUtils.findAllNodesByText(root, "НЕТ МЕСТ")
        if (noSlotsNodes.isEmpty()) return false

        val noSlotsNode = noSlotsNodes.firstOrNull() ?: return false
        val noSlotsRect = Rect()
        val targetRect = Rect()

        try {
            noSlotsNode.getBoundsInScreen(noSlotsRect)
            targetNode.getBoundsInScreen(targetRect)
            return targetRect.top > noSlotsRect.bottom - 50
        } catch (_: Exception) {
            return false
        }
    }
}