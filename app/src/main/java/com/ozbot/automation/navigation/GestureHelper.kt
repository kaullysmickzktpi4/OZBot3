package com.ozbot.automation.navigation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.bot.DomUtils
import kotlin.random.Random

class GestureHelper(
    private val service: AccessibilityService,
    private val stateManager: StateManager,
    private val logger: Logger,
    private val getCurrentProfile: () -> SpeedProfile,
    private val updateAutoProfile: (Boolean) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())

    fun currentClickCooldownMs(): Long {
        val profile = getCurrentProfile()
        val base = profile.clickDelay
        val jitter = (base * 0.15).toLong()
        val extra = if (jitter > 0) Random.nextLong(0, jitter + 1) else 0L
        return base + extra
    }

    fun updateLastClickTime(extraDelay: Long = 0L) {
        stateManager.lastClickTime = System.currentTimeMillis() + currentClickCooldownMs() + extraDelay
    }

    fun gestureTap(x: Float, y: Float): Boolean {
        return try {
            val profile = getCurrentProfile()
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, profile.gestureMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    updateAutoProfile(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    updateAutoProfile(false)
                }
            }

            service.dispatchGesture(gesture, callback, null)
            true

        } catch (e: Exception) {
            logger.e("gestureTap failed: ${e.message}")
            false
        }
    }

    fun tryClickNode(node: AccessibilityNodeInfo): Boolean {
        try {
            DomUtils.tapNode(service, node)
            updateAutoProfile(true)
            return true
        } catch (_: Throwable) {}

        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 4 && rect.height() > 4) {
                if (gestureTap(rect.centerX().toFloat(), rect.centerY().toFloat())) {
                    return true
                }
            }
        } catch (_: Exception) {}

        try {
            val clickable = DomUtils.findClickableParent(node) ?: node
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                updateAutoProfile(true)
                return true
            }
        } catch (_: Exception) {}

        updateAutoProfile(false)
        return false
    }

    fun safeClickNode(node: AccessibilityNodeInfo, actionName: String): Boolean {
        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Получаем размеры экрана
            val displayMetrics = service.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            // ❌ УБЕРИ ЭТУ ПРОВЕРКУ — она мешает кликать на фильтр в bottom nav
            /*
            val bottomNavThreshold = screenHeight * 0.85f
            if (rect.top > bottomNavThreshold) {
                logger.w("⚠️ PREVENTED click in bottom nav for: $actionName")
                logger.w("   Coordinates: $rect (threshold: $bottomNavThreshold)")
                return false
            }
            */

            // Проверка: НЕ в header (верхние 3% экрана)
            val headerThreshold = screenHeight * 0.03f
            if (rect.top < headerThreshold) {
                logger.w("⚠️ PREVENTED click in header for: $actionName")
                logger.w("   Coordinates: $rect (threshold: $headerThreshold)")
                return false
            }

            logger.d("✅ Safe click for '$actionName' at: $rect (screen: ${displayMetrics.widthPixels}x$screenHeight)")
            return tryClickNode(node)

        } catch (e: Exception) {
            logger.e("safeClickNode error: ${e.message}")
            return false
        }
    }

    fun tapNodeBoundsWithCallback(node: AccessibilityNodeInfo, afterMs: Long) {
        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val x = rect.centerX().toFloat()
            val y = rect.centerY().toFloat()

            if (rect.width() <= 0 || rect.height() <= 0) {
                logger.w("tapNodeBoundsWithCallback: invalid bounds")
                return
            }

            val profile = getCurrentProfile()
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, profile.gestureMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            stateManager.goingToWarehouses.set(true)

            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    updateLastClickTime(afterMs)
                    handler.postDelayed({
                        stateManager.goingToWarehouses.set(false)
                    }, afterMs.coerceAtLeast(200L))
                    updateAutoProfile(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    updateLastClickTime()
                    updateAutoProfile(false)
                    stateManager.goingToWarehouses.set(false)
                }
            }

            service.dispatchGesture(gesture, callback, null)

        } catch (e: Exception) {
            logger.e("tapNodeBoundsWithCallback error: ${e.message}")
            stateManager.goingToWarehouses.set(false)
        }
    }

    fun performFastSwipeUp(bounds: Rect) {
        try {
            val centerX = bounds.centerX().toFloat()
            val startY = (bounds.top + bounds.height() * 0.8f)
            val endY = (bounds.top + bounds.height() * 0.2f)

            val path = Path().apply {
                moveTo(centerX, startY)
                lineTo(centerX, endY)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            service.dispatchGesture(gesture, null, null)

        } catch (e: Exception) {
            logger.e("performFastSwipeUp error: ${e.message}")
        }
    }
}