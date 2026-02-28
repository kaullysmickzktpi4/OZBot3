package com.ozbot.automation.actions

import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.bot.DomUtils
import com.ozbot.automation.navigation.GestureHelper
import com.ozbot.automation.navigation.NavigationHelper

class WarehouseActions(
    private val stateManager: StateManager,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val getCurrentProfile: () -> SpeedProfile
) {

    fun clickWarehouse(root: AccessibilityNodeInfo) {
        val nodes = DomUtils.findAllNodesByText(root, "Записаться")
        if (nodes.isEmpty()) {
            return
        }

        val btn = nodes.first()
        val clickable = DomUtils.findClickableParent(btn) ?: btn

        logger.d("🎯 Clicking 'Записаться' on warehouse")

        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        stateManager.waitingForWarehouseLoad.set(true)
        stateManager.lastStepTime = System.currentTimeMillis()

        val waitTime = when (getCurrentProfile()) {
            SpeedProfile.FAST -> 280L
            SpeedProfile.NORMAL -> 380L
            SpeedProfile.SLOW -> 520L
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stateManager.waitingForWarehouseLoad.set(false)
            logger.d("✅ Ready to click process")
        }, waitTime)
    }
}