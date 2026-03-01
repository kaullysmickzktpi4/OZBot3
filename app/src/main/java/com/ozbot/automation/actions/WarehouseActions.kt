package com.ozbot.automation.actions

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.bot.DomUtils
import com.ozbot.automation.navigation.GestureHelper

class WarehouseActions(
    private val stateManager: StateManager,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val getCurrentProfile: () -> SpeedProfile
) {

    // ✅ FIX: один Handler на весь класс вместо создания нового каждый раз
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastWarehouseClickTime = 0L
    private val WAREHOUSE_CLICK_COOLDOWN = 3000L

    fun clickWarehouse(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()

        if (now - lastWarehouseClickTime < WAREHOUSE_CLICK_COOLDOWN) {
            logger.d("⏳ Warehouse click cooldown, skipping...")
            return
        }

        val nodes = DomUtils.findAllNodesByText(root, "Записаться")
        if (nodes.isEmpty()) {
            logger.d("❌ 'Записаться' not found")
            return
        }

        val btn = nodes.first()
        val clickable = DomUtils.findClickableParent(btn) ?: btn

        if (!clickable.isClickable) {
            logger.d("❌ 'Записаться' node not clickable")
            return
        }

        logger.d("🎯 Clicking 'Записаться' on warehouse")
        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        gestureHelper.updateLastClickTime()

        lastWarehouseClickTime = now
        stateManager.waitingForWarehouseLoad.set(true)
        stateManager.lastStepTime = now
        stateManager.markNavigation()

        val waitTime = when (getCurrentProfile()) {
            SpeedProfile.FAST -> 1500L
            SpeedProfile.NORMAL -> 2000L
            SpeedProfile.SLOW -> 2500L
        }

        // ✅ FIX: используем поле handler, а не новый объект
        handler.postDelayed({
            stateManager.waitingForWarehouseLoad.set(false)
            logger.d("✅ Ready to check process screen")
        }, waitTime)
    }

    fun reset() {
        lastWarehouseClickTime = 0L
        // ✅ FIX: отменяем pending callbacks при сбросе
        handler.removeCallbacksAndMessages(null)
    }
}