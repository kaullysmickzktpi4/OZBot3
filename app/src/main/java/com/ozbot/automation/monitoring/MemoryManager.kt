package com.ozbot.automation.monitoring

import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.telegram.TelegramBot

class MemoryManager(
    private val stateManager: StateManager,
    private val logger: Logger,
    private val getCurrentProfile: () -> SpeedProfile
) {
    companion object {
        private const val GC_INTERVAL_MS = 60_000L
        private const val RAM_WARNING_THRESHOLD_PERCENT = 80
    }

    fun maybeForceGc() {
        val now = System.currentTimeMillis()
        val lastGc = stateManager.lastGcTime.get()

        if (now - lastGc > GC_INTERVAL_MS) {
            if (stateManager.lastGcTime.compareAndSet(lastGc, now)) {
                val tick = stateManager.tickCount.incrementAndGet()
                val runtime = Runtime.getRuntime()
                val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val maxMb = runtime.maxMemory() / 1024 / 1024
                val profile = getCurrentProfile()
                val frozenSec = (now - stateManager.lastUiChangeTime) / 1000

                if (tick % 10 == 0L) {
                    logger.d("📊 Mem: ${usedMb}MB/${maxMb}MB | Speed: $profile | UI age: ${frozenSec}s | tick #$tick")
                }

                maybeSendTelegramReport(usedMb, maxMb, tick)
                checkRamWarning(usedMb, maxMb)
                System.gc()
            }
        }
    }

    private fun maybeSendTelegramReport(usedMb: Long, maxMb: Long, tick: Long) {
        if (!TelegramBot.isEnabled()) return

        val now = System.currentTimeMillis()
        val intervalMs = 30 * 60 * 1000L

        if (now - stateManager.lastTelegramReportTime >= intervalMs) {
            stateManager.lastTelegramReportTime = now
            val uptime = formatUptime(now - stateManager.automationStartTime)
            TelegramBot.sendStatusReport(
                usedMb,
                maxMb,
                getCurrentProfile().name,
                tick,
                stateManager.restartCount.get(),
                uptime
            )
        }
    }

    private fun checkRamWarning(usedMb: Long, maxMb: Long) {
        if (!TelegramBot.isEnabled() || maxMb <= 0) return

        val now = System.currentTimeMillis()
        if (now - stateManager.lastRamWarningTime < 10 * 60 * 1000L) return

        val percent = usedMb * 100 / maxMb
        if (percent >= RAM_WARNING_THRESHOLD_PERCENT) {
            stateManager.lastRamWarningTime = now
            TelegramBot.sendMemoryWarning(usedMb, maxMb)
        }
    }

    // ✅ СДЕЛАЙ ПУБЛИЧНЫМ
    fun formatUptime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}д ${hours % 24}ч ${minutes % 60}м"
            hours > 0 -> "${hours}ч ${minutes % 60}м"
            minutes > 0 -> "${minutes}м ${seconds % 60}с"
            else -> "${seconds}с"
        }
    }
}