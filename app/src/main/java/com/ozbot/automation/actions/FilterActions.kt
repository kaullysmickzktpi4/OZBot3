package com.ozbot.automation.actions

import android.accessibilityservice.AccessibilityService
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
import com.ozbot.automation.navigation.GestureHelper
import com.ozbot.automation.navigation.NavigationHelper

class FilterActions(
    private val service: AccessibilityService,
    private val stateManager: StateManager,
    private val logger: Logger,
    private val gestureHelper: GestureHelper,
    private val navigationHelper: NavigationHelper,
    private val screenDetector: ScreenDetector,
    private val findOzonRoot: () -> AccessibilityNodeInfo?,
    private val getCurrentProfile: () -> SpeedProfile
) {

    private val handler = Handler(Looper.getMainLooper())

    // ✅ Флаг — уже в процессе настройки фильтра, не трогать
    @Volatile private var isConfiguringFilter = false
    @Volatile private var lastFilterActionTime = 0L
    private val FILTER_ACTION_COOLDOWN = 2000L

    fun setupWarehouseFilter(root: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()

        // Защита от повторного вызова
        if (isConfiguringFilter) {
            logger.d("⏳ Filter configuration in progress, skipping...")
            return false
        }

        if (now - lastFilterActionTime < FILTER_ACTION_COOLDOWN) {
            logger.d("⏳ Filter action cooldown, skipping...")
            return false
        }

        try {
            // Шаг 1: Если фильтр не открыт — открываем
            if (!screenDetector.isFilterModalOpen(root)) {
                logger.d("🔧 Filter not open, opening...")
                if (clickFilterOpenButton(root)) {
                    lastFilterActionTime = now
                    gestureHelper.updateLastClickTime(getWaitMs(1200L, 1600L, 2000L))
                    return false
                } else {
                    logger.w("❌ Cannot open filter, skipping")
                    stateManager.filterConfigured = true
                    return false
                }
            }

            // Шаг 2: Фильтр открыт — проверяем состояние чекбокса
            logger.d("📋 Filter modal open, checking favorites toggle...")

            val toggleChecked = isFavoritesToggleChecked(root)
            logger.d("Favorites toggle checked: $toggleChecked")

            if (!toggleChecked) {
                // Нужно включить — кликаем чекбокс
                logger.d("🎯 Enabling favorites toggle...")
                if (clickFavoritesToggle(root)) {
                    lastFilterActionTime = now
                    isConfiguringFilter = true
                    // После клика чекбокса ждём — кнопка сменится на "Принять"
                    handler.postDelayed({
                        applyFilterDelayed()
                    }, getWaitMs(600L, 900L, 1200L))
                    return true
                } else {
                    logger.w("❌ Cannot click toggle, closing filter")
                    closeFilterWithBack()
                    stateManager.filterConfigured = true
                    return false
                }
            } else {
                // Избранные уже включены — просто закрываем
                logger.d("✅ Favorites already enabled, closing filter...")
                if (clickCloseOrApplyButton(root)) {
                    lastFilterActionTime = now
                    isConfiguringFilter = true
                    handler.postDelayed({
                        isConfiguringFilter = false
                        stateManager.filterConfigured = true
                        stateManager.markNavigation()
                        logger.d("✅ Filter done (was already configured)")
                    }, getWaitMs(800L, 1200L, 1600L))
                    return true
                } else {
                    closeFilterWithBack()
                    stateManager.filterConfigured = true
                    return false
                }
            }

        } catch (e: Exception) {
            logger.e("setupWarehouseFilter error: ${e.message}", e)
            isConfiguringFilter = false
            closeFilterWithBack()
            stateManager.filterConfigured = true
            return false
        }
    }

    private fun applyFilterDelayed() {
        if (!stateManager.isRunning.get()) {
            isConfiguringFilter = false
            return
        }

        val root = findOzonRoot()
        if (root == null) {
            logger.w("❌ Root is null in applyFilterDelayed")
            isConfiguringFilter = false
            stateManager.filterConfigured = true
            return
        }

        try {
            logger.d("🔍 Looking for 'Принять' button after toggle click...")

            // После включения чекбокса кнопка должна стать "Принять"
            val clicked = clickCloseOrApplyButton(root)

            if (clicked) {
                logger.d("✅ Apply/Close button clicked!")
                handler.postDelayed({
                    isConfiguringFilter = false
                    stateManager.filterConfigured = true
                    stateManager.markNavigation()
                    logger.d("✅ Filter configuration complete!")

                    // Проверяем что вернулись на склады
                    handler.postDelayed({
                        val checkRoot = findOzonRoot()
                        if (checkRoot != null) {
                            if (!screenDetector.isWarehouseScreen(checkRoot)) {
                                logger.w("⚠️ Not on warehouse screen after filter, going back...")
                                navigationHelper.clickWarehousesTab(checkRoot)
                            }
                            NodeTreeHelper.safeRecycle(checkRoot)
                        }
                    }, getWaitMs(800L, 1200L, 1600L))

                }, getWaitMs(800L, 1200L, 1600L))
            } else {
                logger.w("❌ Apply button not found, using BACK")
                closeFilterWithBack()
                isConfiguringFilter = false
                stateManager.filterConfigured = true
            }
        } catch (e: Exception) {
            logger.e("applyFilterDelayed error: ${e.message}", e)
            isConfiguringFilter = false
            closeFilterWithBack()
            stateManager.filterConfigured = true
        } finally {
            NodeTreeHelper.safeRecycle(root)
        }
    }

    // ==================== КНОПКА ОТКРЫТИЯ ФИЛЬТРА ====================

    private fun clickFilterOpenButton(root: AccessibilityNodeInfo): Boolean {
        try {
            // Из DOM знаем: кнопка фильтра справа от "Карта"
            // bounds кнопки фильтра примерно [729,2016][864,2151]
            val displayMetrics = service.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val bottomNavThreshold = screenHeight * 0.8f

            val mapNodes = DomUtils.findAllNodesByText(root, "Карта")
            for (mapNode in mapNodes) {
                val mapRect = Rect()
                mapNode.getBoundsInScreen(mapRect)
                if (mapRect.top < bottomNavThreshold) continue

                // Кнопка фильтра — справа от Карта в той же строке
                NodeTreeHelper.withNodeTree(root, maxDepth = 20) { node ->
                    if (node.isClickable) {
                        val nodeRect = Rect()
                        try {
                            node.getBoundsInScreen(nodeRect)
                            val sameRow = kotlin.math.abs(nodeRect.centerY() - mapRect.centerY()) < 100
                            val rightOfMap = nodeRect.left >= mapRect.right - 10
                            val inBottomArea = nodeRect.top > bottomNavThreshold
                            val notWide = nodeRect.width() < 300 // не широкая кнопка

                            if (sameRow && rightOfMap && inBottomArea && notWide) {
                                if (gestureHelper.tryClickNode(node)) {
                                    logger.d("✅ Filter open button clicked at $nodeRect")
                                    return true
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    null
                }
            }

            // Fallback: хардкод координаты из DOM [729,2016][864,2151]
            val cx = 796f
            val cy = 2083f
            logger.d("Trying hardcoded filter button coords: $cx, $cy")
            if (gestureHelper.gestureTap(cx, cy)) {
                logger.d("✅ Filter open button clicked by coords")
                return true
            }

            return false
        } catch (e: Exception) {
            logger.e("clickFilterOpenButton error: ${e.message}", e)
            return false
        }
    }

    // ==================== ЧЕКБОКС ИЗБРАННЫЕ СКЛАДЫ ====================

    /**
     * Из DOM: checkable="true" checked="true/false"
     * bounds [906,1107][1041,1242] — это сам чекбокс
     */
    private fun isFavoritesToggleChecked(root: AccessibilityNodeInfo): Boolean {
        try {
            // Ищем текст "Избранные склады" и проверяем checkable node рядом
            val favoriteNodes = DomUtils.findAllNodesByText(root, "Избранные склады")
            if (favoriteNodes.isEmpty()) {
                logger.w("'Избранные склады' text not found")
                return false
            }

            val textNode = favoriteNodes.first()
            val textRect = Rect()
            textNode.getBoundsInScreen(textRect)

            // Ищем checkable node на той же высоте
            var result = false
            NodeTreeHelper.withNodeTree(root, maxDepth = 15) { node ->
                if (node.isCheckable) {
                    val nodeRect = Rect()
                    try {
                        node.getBoundsInScreen(nodeRect)
                        val sameRow = kotlin.math.abs(nodeRect.centerY() - textRect.centerY()) < 150
                        if (sameRow) {
                            logger.d("Found checkable near 'Избранные склады': checked=${node.isChecked}, bounds=$nodeRect")
                            result = node.isChecked
                            return@withNodeTree node // выходим
                        }
                    } catch (_: Exception) {}
                }
                null
            }
            return result
        } catch (e: Exception) {
            logger.e("isFavoritesToggleChecked error: ${e.message}", e)
            return false
        }
    }

    private fun clickFavoritesToggle(root: AccessibilityNodeInfo): Boolean {
        try {
            val favoriteNodes = DomUtils.findAllNodesByText(root, "Избранные склады")
            if (favoriteNodes.isEmpty()) return false

            val textNode = favoriteNodes.first()
            val textRect = Rect()
            textNode.getBoundsInScreen(textRect)

            var clicked = false
            NodeTreeHelper.withNodeTree(root, maxDepth = 15) { node ->
                if (node.isCheckable && node.isClickable) {
                    val nodeRect = Rect()
                    try {
                        node.getBoundsInScreen(nodeRect)
                        val sameRow = kotlin.math.abs(nodeRect.centerY() - textRect.centerY()) < 150
                        if (sameRow) {
                            clicked = gestureHelper.tryClickNode(node)
                            logger.d("Clicked favorites toggle: $clicked at $nodeRect")
                            return@withNodeTree node
                        }
                    } catch (_: Exception) {}
                }
                null
            }
            return clicked
        } catch (e: Exception) {
            logger.e("clickFavoritesToggle error: ${e.message}", e)
            return false
        }
    }

    // ==================== КНОПКА "ПРИНЯТЬ" / "ЗАКРЫТЬ" ====================

    /**
     * Из DOM: bounds [45,1987][1035,2145]
     * Текст "Закрыть" когда нет изменений
     * Текст "Принять" когда есть изменения
     */
    private fun clickCloseOrApplyButton(root: AccessibilityNodeInfo): Boolean {
        try {
            // Стратегия 1: По тексту "Принять"
            val applyNodes = DomUtils.findAllNodesByText(root, "Принять")
            if (applyNodes.isNotEmpty()) {
                val node = applyNodes.first()
                val clickable = DomUtils.findClickableParent(node) ?: node
                if (gestureHelper.tryClickNode(clickable)) {
                    logger.d("✅ Clicked 'Принять'")
                    return true
                }
            }

            // Стратегия 2: По тексту "Закрыть"
            val closeNodes = DomUtils.findAllNodesByText(root, "Закрыть")
            if (closeNodes.isNotEmpty()) {
                val node = closeNodes.first()
                val clickable = DomUtils.findClickableParent(node) ?: node
                if (gestureHelper.tryClickNode(clickable)) {
                    logger.d("✅ Clicked 'Закрыть'")
                    return true
                }
            }

            // Стратегия 3: Хардкод из DOM — большая кнопка внизу [45,1987][1035,2145]
            val cx = 540f
            val cy = 2066f
            logger.d("Trying hardcoded apply/close button: $cx, $cy")
            if (gestureHelper.gestureTap(cx, cy)) {
                logger.d("✅ Clicked apply/close by hardcoded coords")
                return true
            }

            logger.w("❌ Apply/Close button not found")
            return false

        } catch (e: Exception) {
            logger.e("clickCloseOrApplyButton error: ${e.message}", e)
            return false
        }
    }

    private fun closeFilterWithBack() {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            gestureHelper.updateLastClickTime()
            stateManager.markNavigation()
            logger.d("Filter closed via BACK")
        } catch (_: Exception) {}
    }

    private fun getWaitMs(fast: Long, normal: Long, slow: Long): Long {
        return when (getCurrentProfile()) {
            SpeedProfile.FAST -> fast
            SpeedProfile.NORMAL -> normal
            SpeedProfile.SLOW -> slow
        }
    }
}