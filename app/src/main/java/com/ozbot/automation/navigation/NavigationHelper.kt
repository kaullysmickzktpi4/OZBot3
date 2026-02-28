package com.ozbot.navigation

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
            logger.d("🔍 Searching for Warehouses tab...")

            // Способ 1: По ID
            val nodeById = findWarehouseNodeAnywhere()
            if (nodeById != null) {
                val rect = Rect()
                nodeById.getBoundsInScreen(rect)
                logger.d("Found by ID at bounds: $rect")

                if (gestureHelper.tryClickNode(nodeById)) {
                    logger.d("✅ Clicked warehouses tab by ID")
                    return true
                }
            }

            // Способ 2: По тексту "Склады"
            val textNodes = DomUtils.findAllNodesByText(root, "Склады")
            if (textNodes.isNotEmpty()) {
                for (textNode in textNodes) {
                    val rect = Rect()
                    textNode.getBoundsInScreen(rect)
                    logger.d("Found text 'Склады' at bounds: $rect")

                    if (rect.bottom > 2000) {
                        var clickableParent = DomUtils.findClickableParent(textNode)
                        if (clickableParent == null) {
                            try {
                                val parent = textNode.parent
                                if (parent != null) {
                                    clickableParent = DomUtils.findClickableParent(parent)
                                }
                            } catch (_: Exception) {}
                        }

                        val targetNode = clickableParent ?: textNode
                        val targetRect = Rect()
                        targetNode.getBoundsInScreen(targetRect)
                        logger.d("Clicking target at bounds: $targetRect")

                        if (gestureHelper.tryClickNode(targetNode)) {
                            logger.d("✅ Clicked warehouses tab by text")
                            return true
                        }
                    }
                }
            }

            // Способ 3: По description
            val nodeByDesc = DomUtils.findNodeByDesc(root, "warehouseTab")
            if (nodeByDesc != null) {
                val rect = Rect()
                nodeByDesc.getBoundsInScreen(rect)
                logger.d("Found by desc at bounds: $rect")

                if (gestureHelper.tryClickNode(nodeByDesc)) {
                    logger.d("✅ Clicked warehouses tab by desc")
                    return true
                }
            }

            // Способ 4: Хардкод координаты
            logger.d("Trying hardcoded coordinates...")
            val centerX = 540f
            val centerY = 2205f

            if (gestureHelper.gestureTap(centerX, centerY)) {
                logger.d("✅ Clicked warehouses tab by coordinates")
                return true
            }

            logger.w("❌ All methods failed to click warehouses tab")
            return false

        } catch (e: Exception) {
            logger.e("Error clicking warehouses tab: ${e.message}", e)
            return false
        }
    }

    fun goToWarehousesSmart(root: AccessibilityNodeInfo) {
        if (stateManager.goingToWarehouses.get()) return

        try {
            val node = findWarehouseNodeAnywhere()
            if (node != null) {
                gestureHelper.tapNodeBoundsWithCallback(node, getCurrentProfile().warehouseDelay)
                return
            }

            val nodeByText = DomUtils.findNodeByText(root, "Склады")
            if (nodeByText != null) {
                val clickable = DomUtils.findClickableParent(nodeByText) ?: nodeByText
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                gestureHelper.updateLastClickTime()
                return
            }

            logger.w("goToWarehousesSmart: no method succeeded")
        } catch (e: Exception) {
            logger.e("goToWarehousesSmart error: ${e.message}")
            stateManager.goingToWarehouses.set(false)
        }
    }

    fun isOnBookingsTab(root: AccessibilityNodeInfo): Boolean {
        // Проверяем текст "Записи" в нижней панели навигации
        val hasBookingsText = DomUtils.hasText(root, "Записи")

        if (!hasBookingsText) return false

        // Дополнительная проверка: есть ли характерные элементы вкладки "Записи"
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