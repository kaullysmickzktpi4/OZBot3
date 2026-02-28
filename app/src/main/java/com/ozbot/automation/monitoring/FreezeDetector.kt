package com.ozbot.automation.monitoring

import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.NodeTreeHelper
import com.ozbot.telegram.TelegramBot

class FreezeDetector(
    private val stateManager: StateManager,
    private val logger: Logger
) {
    companion object {
        private const val DOM_STABLE_THRESHOLD = 2
        private const val FREEZE_TIMEOUT_MS = 60_000L
        private const val FREEZE_CHECK_INTERVAL_MS = 5_000L
    }

    fun calculateDomHash(root: AccessibilityNodeInfo): Int {
        var hash = 0
        var nodeCount = 0

        NodeTreeHelper.withNodeTree(root, maxDepth = 8) { node ->
            nodeCount++
            hash = hash * 31 + (node.className?.hashCode() ?: 0)
            hash = hash * 31 + (node.text?.toString()?.hashCode() ?: 0)
            hash = hash * 31 + (if (node.isClickable) 1 else 0)
            if (nodeCount > 40) return@withNodeTree true
            null
        }

        return hash + nodeCount * 7
    }

    fun isUiStable(root: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        val currentHash = calculateDomHash(root)

        if (currentHash == stateManager.lastDomHash) {
            stateManager.domStableCount++
        } else {
            stateManager.domStableCount = 0
            stateManager.lastDomHash = currentHash
            stateManager.lastUiChangeTime = now
        }

        return stateManager.domStableCount >= DOM_STABLE_THRESHOLD
    }

    fun checkForFreeze(root: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()

        if (now - stateManager.lastFreezeCheckTime < FREEZE_CHECK_INTERVAL_MS) {
            return false
        }

        stateManager.lastFreezeCheckTime = now

        val currentHash = calculateDomHash(root)

        if (currentHash != stateManager.freezeDetectedHash) {
            stateManager.freezeDetectedHash = currentHash
            stateManager.lastUiChangeTime = now
            return false
        }

        val frozenTime = now - stateManager.lastUiChangeTime

        if (frozenTime >= FREEZE_TIMEOUT_MS) {
            val restarts = stateManager.restartCount.incrementAndGet()
            logger.w("🥶 FREEZE DETECTED! UI unchanged for ${frozenTime / 1000}s. Restart #$restarts")
            TelegramBot.sendFreezeAlert(frozenTime / 1000, restarts)
            return true
        }

        if (frozenTime >= FREEZE_TIMEOUT_MS / 2) {
            logger.w("⚠️ Possible freeze: UI unchanged for ${frozenTime / 1000}s")
        }

        return false
    }
}