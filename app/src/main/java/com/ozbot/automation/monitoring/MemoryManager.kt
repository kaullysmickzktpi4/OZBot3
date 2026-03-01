package com.ozbot.automation.monitoring

import com.ozbot.automation.core.StateManager
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.SpeedProfile

class MemoryManager(
    private val stateManager: StateManager,
    private val logger: Logger,
    private val getCurrentProfile: () -> SpeedProfile
) {
    companion object {
        private const val GC_INTERVAL_MS = 60_000L
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

                System.gc()
            }
        }
    }

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