package com.ozbot.automation.utils

enum class SpeedProfile(
    val clickDelay: Long,
    val loadWait: Long,
    val tickerInterval: Long,
    val idleTickerInterval: Long,
    val gestureMs: Long,
    val warehouseDelay: Long,
    val stepTimeout: Long,
    val scrollDelay: Long
) {
    FAST(110, 280, 70, 220, 55, 500, 1800, 45),
    NORMAL(220, 450, 110, 300, 70, 700, 2200, 65),
    SLOW(360, 750, 170, 450, 90, 1000, 3000, 95)
}