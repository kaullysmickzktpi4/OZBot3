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

    fun setupWarehouseFilter(root: AccessibilityNodeInfo): Boolean {
        try {
            logger.d("🔧 Setting up warehouse filter...")

            if (!screenDetector.isFilterModalOpen(root)) {
                logger.d("Filter modal not open, opening...")

                if (clickWarehouseFilterButton(root)) {
                    logger.d("✅ Filter button clicked, waiting for modal...")
                    gestureHelper.updateLastClickTime(getFilterOpenWaitMs())
                    return false
                } else {
                    logger.w("❌ Failed to click filter button")
                    return false
                }
            }

            logger.d("📋 Filter modal is open, enabling favorites...")

            val toggleEnabled = enableFavoriteWarehousesToggle(root)

            if (toggleEnabled) {
                logger.d("✅ Toggle processed, applying filter soon...")

                handler.postDelayed({
                    if (!stateManager.isRunning.get()) return@postDelayed

                    val r = findOzonRoot()
                    if (r != null) {
                        if (applyFilter(r)) {
                            logger.d("✅✅ Filter configuration complete!")

                            handler.postDelayed({
                                val checkRoot = findOzonRoot()
                                if (checkRoot != null) {
                                    if (!screenDetector.isWarehouseScreen(checkRoot)) {
                                        logger.w("⚠️ Not on warehouse screen after filter, returning...")
                                        navigationHelper.clickWarehousesTab(checkRoot)
                                    } else {
                                        logger.d("✅ Back on warehouse screen, ready to work")
                                    }
                                    NodeTreeHelper.safeRecycle(checkRoot)
                                }
                            }, getFilterPostApplyCheckDelayMs())
                        } else {
                            logger.e("❌ Failed to apply filter!")
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            gestureHelper.updateLastClickTime()
                            stateManager.filterConfigured = true
                        }
                        NodeTreeHelper.safeRecycle(r)
                    }
                }, getFilterApplyDelayMs())

                return true
            } else {
                logger.w("❌ Failed to enable toggle")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                stateManager.filterConfigured = true
                gestureHelper.updateLastClickTime()
                return false
            }

        } catch (e: Exception) {
            logger.e("setupWarehouseFilter error: ${e.message}", e)
            try {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } catch (_: Exception) {}
            stateManager.filterConfigured = true
            return false
        }
    }

    private fun clickWarehouseFilterButton(root: AccessibilityNodeInfo): Boolean {
        try {
            logger.d("🔍 Clicking warehouse filter button (next to 'Карта' in bottom nav)...")

            val displayMetrics = service.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val screenWidth = displayMetrics.widthPixels

            logger.d("Screen: ${screenWidth}x${screenHeight}")

            // НОВАЯ СТРАТЕГИЯ: Ищем кнопку "Карта" в BOTTOM NAVIGATION
            val mapNodes = DomUtils.findAllNodesByText(root, "Карта")

            if (mapNodes.isEmpty()) {
                logger.w("❌ 'Карта' button not found")
                return false
            }

            // Ищем "Карта" которая в BOTTOM NAV (нижние 20% экрана)
            val bottomNavThreshold = screenHeight * 0.8f
            var mapRect = Rect()

            for (mapNode in mapNodes) {
                val rect = Rect()
                mapNode.getBoundsInScreen(rect)

                logger.d("Found 'Карта' at: $rect")

                if (rect.top > bottomNavThreshold) {
                    logger.d("  ↳ ✅ This 'Карта' is in bottom navigation!")
                    mapRect = rect
                    break
                } else {
                    logger.d("  ↳ Skipped: not in bottom nav (Y=${rect.top} < $bottomNavThreshold)")
                }
            }

            if (mapRect.isEmpty) {
                logger.w("❌ 'Карта' not found in bottom navigation")
                return false
            }

            logger.d("✅ Found 'Карта' in bottom nav at: $mapRect")

            // Теперь ищем кнопку СПРАВА от "Карта" в той же строке
            var filterButton: AccessibilityNodeInfo? = null
            var minDistance = Int.MAX_VALUE

            NodeTreeHelper.withNodeTree(root, maxDepth = 20) { node ->
                if (node.isClickable) {
                    val nodeRect = Rect()
                    try {
                        node.getBoundsInScreen(nodeRect)

                        val isInBottomNav = nodeRect.top > bottomNavThreshold
                        val isSameRow = kotlin.math.abs(nodeRect.centerY() - mapRect.centerY()) < 80
                        val isRightOfMap = nodeRect.left > mapRect.right - 20
                        val isNotMapItself = nodeRect != mapRect

                        if (isInBottomNav && isSameRow && isRightOfMap && isNotMapItself) {
                            val distance = nodeRect.left - mapRect.right

                            if (distance >= 0 && distance < minDistance && distance < 500) {
                                minDistance = distance
                                filterButton = node
                                logger.d("Found potential filter at: $nodeRect (distance: ${distance}px from 'Карта')")
                            }
                        }
                    } catch (_: Exception) {}
                }
                null
            }

            val selectedFilterButton = filterButton
            if (selectedFilterButton != null) {
                val filterRect = Rect()
                selectedFilterButton.getBoundsInScreen(filterRect)
                logger.d("✅ Clicking filter button at: $filterRect")

                if (gestureHelper.tryClickNode(selectedFilterButton)) {
                    logger.d("✅ Filter button clicked!")

                    // Проверяем что фильтр открылся
                    handler.postDelayed({
                        val checkRoot = findOzonRoot()
                        if (checkRoot != null) {
                            if (screenDetector.isFilterModalOpen(checkRoot)) {
                                logger.d("✅ Filter modal opened successfully!")
                            } else {
                                logger.w("⚠️ Filter didn't open")
                            }
                            NodeTreeHelper.safeRecycle(checkRoot)
                        }
                    }, 500L)

                    return true
                }
            }

            logger.w("❌ Filter button not found next to 'Карта'")

            // Пропускаем фильтр
            stateManager.filterConfigured = true
            return false

        } catch (e: Exception) {
            logger.e("clickWarehouseFilterButton error: ${e.message}", e)
            stateManager.filterConfigured = true
            return false
        }
    }

    private fun getFilterOpenWaitMs(): Long {
        return when (getCurrentProfile()) {
            SpeedProfile.FAST -> 800L
            SpeedProfile.NORMAL -> 1300L
            SpeedProfile.SLOW -> 1800L
        }
    }

    private fun getFilterApplyDelayMs(): Long {
        return when (getCurrentProfile()) {
            SpeedProfile.FAST -> 350L
            SpeedProfile.NORMAL -> 650L
            SpeedProfile.SLOW -> 950L
        }
    }

    private fun getFilterPostApplyCheckDelayMs(): Long {
        return when (getCurrentProfile()) {
            SpeedProfile.FAST -> 700L
            SpeedProfile.NORMAL -> 1100L
            SpeedProfile.SLOW -> 1500L
        }
    }

    private fun enableFavoriteWarehousesToggle(root: AccessibilityNodeInfo): Boolean {
        try {
            logger.d("🔍 Looking for 'Избранные склады' toggle...")

            val favoriteNodes = DomUtils.findAllNodesByText(root, "Избранные склады")
            if (favoriteNodes.isEmpty()) {
                logger.w("❌ 'Избранные склады' text not found")
                return false
            }

            logger.d("Found ${favoriteNodes.size} 'Избранные склады' text nodes")

            val favoriteTextNode = favoriteNodes.first()
            val parent = try {
                favoriteTextNode.parent
            } catch (e: Exception) {
                logger.e("Failed to get parent: ${e.message}")
                return false
            }

            if (parent == null) {
                logger.w("❌ Parent is null")
                return false
            }

            logger.d("Parent has ${parent.childCount} children")

            var toggleNode: AccessibilityNodeInfo? = null

            for (i in 0 until parent.childCount) {
                val child = try { parent.getChild(i) } catch (_: Exception) { null }
                if (child == null) continue

                val childText = child.text?.toString() ?: ""

                if (childText.contains("Избранные склады")) {
                    logger.d("Found text node at index $i")
                    break
                }

                if (child.isCheckable && child.isClickable) {
                    toggleNode = child
                    logger.d("Found checkable node at index $i (checked=${child.isChecked})")
                }
            }

            if (toggleNode == null) {
                logger.w("❌ Toggle not found")
                return false
            }

            val isChecked = toggleNode.isChecked
            logger.d("Toggle state: checked=$isChecked")

            if (isChecked) {
                logger.d("✅ 'Избранные склады' already enabled")
                return true
            }

            logger.d("🎯 Clicking toggle to enable...")

            if (gestureHelper.tryClickNode(toggleNode)) {
                logger.d("✅ Toggle clicked")
                return true
            }

            logger.w("❌ Failed to click toggle")
            return false

        } catch (e: Exception) {
            logger.e("enableFavoriteWarehousesToggle error: ${e.message}", e)
            return false
        }
    }

    private fun applyFilter(root: AccessibilityNodeInfo): Boolean {
        try {
            logger.d("🔍 Looking for apply button in filter modal...")

            // КРИТИЧНО: Проверяем что мы В ФИЛЬТРЕ
            if (!screenDetector.isFilterModalOpen(root)) {
                logger.w("❌ Not in filter modal, cannot apply")
                return false
            }

            val displayMetrics = service.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val screenWidth = displayMetrics.widthPixels

            logger.d("Screen: ${screenWidth}x${screenHeight}")

            // СТРАТЕГИЯ 1: Ищем БОЛЬШУЮ синюю кнопку внизу модалки
            logger.d("Strategy 1: Looking for large button at bottom of modal...")

            var largestButton: AccessibilityNodeInfo? = null
            var largestButtonArea = 0
            var largestButtonY = 0

            NodeTreeHelper.withNodeTree(root, maxDepth = 15) { node ->
                val className = node.className?.toString() ?: ""

                if ((className.contains("Button", ignoreCase = true) ||
                            className.contains("TextView", ignoreCase = true)) &&
                    node.isClickable
                ) {
                    val rect = Rect()
                    try {
                        node.getBoundsInScreen(rect)

                        val isBottomHalf = rect.top > screenHeight * 0.5f
                        val notInBottomNav = rect.bottom < screenHeight * 0.95f
                        val veryWide = rect.width() > screenWidth * 0.7f
                        val tallEnough = rect.height() > 50 * displayMetrics.density

                        if (isBottomHalf && notInBottomNav && veryWide && tallEnough) {
                            val area = rect.width() * rect.height()

                            logger.d("Found large button: bounds=$rect, area=$area")

                            if (area > largestButtonArea ||
                                (area == largestButtonArea && rect.top > largestButtonY)
                            ) {
                                largestButtonArea = area
                                largestButtonY = rect.top
                                largestButton = node
                            }
                        }
                    } catch (_: Exception) {}
                }
                null
            }

            val button = largestButton
            if (button != null) {
                val rect = Rect()
                button.getBoundsInScreen(rect)
                val text = button.text?.toString() ?: ""

                logger.d("✅ Found largest button at bottom: bounds=$rect, text='$text'")

                if (button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    logger.d("✅ Filter applied successfully via large button!")
                    stateManager.filterConfigured = true

                    handler.postDelayed({
                        val checkRoot = findOzonRoot()
                        if (checkRoot != null) {
                            if (!screenDetector.isFilterModalOpen(checkRoot)) {
                                logger.d("✅ Filter closed successfully")
                            }
                            NodeTreeHelper.safeRecycle(checkRoot)
                        }
                    }, getFilterPostApplyCheckDelayMs())

                    return true
                }
            }

            // СТРАТЕГИЯ 2: Ищем по тексту "Применить"
            logger.d("Strategy 2: Looking for text 'Применить'...")

            val applyNodes = DomUtils.findAllNodesByText(root, "Применить")

            if (applyNodes.isNotEmpty()) {
                logger.d("Found ${applyNodes.size} 'Применить' nodes")

                var bottomMost: AccessibilityNodeInfo? = null
                var maxY = 0

                for (node in applyNodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)

                    logger.d("'Применить' at: $rect")

                    if (rect.bottom < screenHeight * 0.95f && rect.top > maxY) {
                        maxY = rect.top
                        bottomMost = node
                    }
                }

                if (bottomMost != null) {
                    val clickable = DomUtils.findClickableParent(bottomMost) ?: bottomMost
                    val rect = Rect()
                    clickable.getBoundsInScreen(rect)

                    logger.d("✅ Clicking 'Применить' at: $rect")

                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        logger.d("✅ Filter applied via 'Применить' text!")
                        stateManager.filterConfigured = true
                        return true
                    }
                }
            }

            // СТРАТЕГИЯ 3: fallback — кликабельный блок внизу
            logger.d("Strategy 3: Looking for any clickable at bottom...")

            var bottomClickable: AccessibilityNodeInfo? = null
            var maxBottomY = 0

            NodeTreeHelper.withNodeTree(root, maxDepth = 15) { node ->
                if (node.isClickable) {
                    val rect = Rect()
                    try {
                        node.getBoundsInScreen(rect)

                        val isWide = rect.width() > screenWidth * 0.6f
                        val isBottomArea = rect.top > screenHeight * 0.6f && rect.bottom < screenHeight * 0.95f
                        val isTallEnough = rect.height() > 40 * displayMetrics.density

                        if (isWide && isBottomArea && isTallEnough && rect.top > maxBottomY) {
                            maxBottomY = rect.top
                            bottomClickable = node
                            logger.d("Found bottom clickable: $rect")
                        }
                    } catch (_: Exception) {}
                }
                null
            }

            val selectedBottomClickable = bottomClickable
            if (selectedBottomClickable != null) {
                val rect = Rect()
                selectedBottomClickable.getBoundsInScreen(rect)
                logger.d("✅ Clicking bottom clickable at: $rect")

                if (selectedBottomClickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    logger.d("✅ Clicked bottom clickable (likely apply button)!")
                    stateManager.filterConfigured = true
                    return true
                }
            }

            logger.w("❌ No apply button found, using BACK")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            stateManager.filterConfigured = true
            return false

        } catch (e: Exception) {
            logger.e("applyFilter error: ${e.message}", e)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            stateManager.filterConfigured = true
            return false
        }
    }

    /**
     * Проверяет есть ли указанный текст рядом с прямугольником
     */
    private fun checkIfNearText(
        root: AccessibilityNodeInfo,
        rect: Rect,
        searchText: String,
        maxDistance: Int
    ): Boolean {
        val nodes = DomUtils.findAllNodesByText(root, searchText)

        for (node in nodes) {
            try {
                val nodeRect = Rect()
                node.getBoundsInScreen(nodeRect)

                val distance = minOf(
                    kotlin.math.abs(rect.top - nodeRect.bottom),
                    kotlin.math.abs(rect.bottom - nodeRect.top),
                    kotlin.math.abs(rect.left - nodeRect.right),
                    kotlin.math.abs(rect.right - nodeRect.left)
                )

                if (distance < maxDistance) {
                    return true
                }
            } catch (_: Exception) {}
        }

        return false
    }
}