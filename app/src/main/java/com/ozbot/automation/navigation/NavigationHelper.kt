package com.ozbot.automation.navigation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.bot.DomUtils

class NavigationHelper(
    private val service: AccessibilityService,
    private val stateManager: StateManager,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val getCurrentProfile: () -> SpeedProfile
) {

    fun findWarehouseNodeAnywhere(): AccessibilityNodeInfo? {
        try {
            for (w in service.windows) {
                val r = w.root ?: continue
                try {
                    DomUtils.findNodeByDesc(r, "warehouseTab")?.let { return it }
                    val nodes = r.findAccessibilityNodeInfosByViewId("ru.ozon.hire:id/warehouseTab")
                    if (!nodes.isNullOrEmpty()) return nodes.first()
                } catch (_: Exception) {}
            }

            val active = service.rootInActiveWindow ?: return null
            DomUtils.findNodeByDesc(active, "warehouseTab")?.let { return it }
            val nodes = active.findAccessibilityNodeInfosByViewId("ru.ozon.hire:id/warehouseTab")
            if (!nodes.isNullOrEmpty()) return nodes.first()

        } catch (e: Exception) {
            logger.e("findWarehouseNodeAnywhere error: ${e.message}")
        }

        return null
    }

    fun clickWarehousesTab(root: AccessibilityNodeInfo): Boolean {
        try {
            // Способ 1: По resource-id
            val nodes = root.findAccessibilityNodeInfosByViewId("ru.ozon.hire:id/warehouseTab")
            if (!nodes.isNullOrEmpty()) {
                val node = nodes.first()
                if (node.isSelected) {
                    logger.d("✅ warehouseTab already selected")
                    return true
                }
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    gestureHelper.updateLastClickTime()
                    logger.d("✅ Clicked warehouseTab by resource-id")
                    return true
                }
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (gestureHelper.gestureTap(rect.centerX().toFloat(), rect.centerY().toFloat())) {
                    logger.d("✅ Tapped warehouseTab by coords")
                    return true
                }
            }

            // Способ 2: По content-desc
            val nodeByDesc = DomUtils.findNodeByDesc(root, "warehouseTab")
            if (nodeByDesc != null) {
                if (gestureHelper.tryClickNode(nodeByDesc)) {
                    logger.d("✅ Clicked warehouseTab by desc")
                    return true
                }
            }

            // Способ 3: Хардкод координаты
            if (gestureHelper.gestureTap(540f, 2205f)) {
                logger.d("✅ Tapped warehouseTab by hardcoded coords")
                return true
            }

            logger.w("❌ clickWarehousesTab: all methods failed")
            return false

        } catch (e: Exception) {
            logger.e("clickWarehousesTab error: ${e.message}", e)
            return false
        }
    }

    fun goToWarehousesSmart(root: AccessibilityNodeInfo) {
        // ✅ Убрана проверка goingToWarehouses — она никогда не сбрасывалась
        // и блокировала навигацию навсегда
        try {
            // Способ 1: По resource-id warehouseTab
            val node = findWarehouseNodeAnywhere()
            if (node != null) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    gestureHelper.gestureTap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    stateManager.markNavigation()
                    gestureHelper.updateLastClickTime()
                    logger.d("✅ goToWarehousesSmart: tapped warehouseTab by bounds")
                    return
                }
            }

            // Способ 2: clickWarehousesTab
            if (clickWarehousesTab(root)) {
                stateManager.markNavigation()
                logger.d("✅ goToWarehousesSmart: clicked warehouseTab")
                return
            }

            // Способ 3: BACK если глубоко в стеке
            logger.w("goToWarehousesSmart: tabs not found, pressing BACK")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            gestureHelper.updateLastClickTime()
            stateManager.markNavigation()

        } catch (e: Exception) {
            logger.e("goToWarehousesSmart error: ${e.message}")
        }
    }

    fun isOnBookingsTab(root: AccessibilityNodeInfo): Boolean {
        val hasBookingElements = DomUtils.hasText(root, "Предстоящие") ||
                DomUtils.hasText(root, "Завершенные") ||
                DomUtils.hasText(root, "Отмененные") ||
                DomUtils.hasText(root, "У вас пока нет записей")

        if (hasBookingElements) {
            logger.d("🚫 Detected BOOKINGS tab")
            return true
        }

        return false
    }
}