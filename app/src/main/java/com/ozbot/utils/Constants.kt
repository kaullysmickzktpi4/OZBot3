package com.ozbot.utils

object Constants {
    const val TARGET_PACKAGE = "ru.ozon.hire"

    val PROCESSES = listOf(
        "Инвентаризация",
        "Приемка",
        "Сортировка непрофиль",
        "Производство непрофиль",
        "Размещение"
    )

    val TIME_SLOTS = listOf(
        "08:00–20:00",
        "20:00–08:00",
        "12:00–20:00",
        "20:00–05:00",
        "10:00–22:00",
        "09:00–21:00"
    )

    // Задержки для профилей скорости (в миллисекундах)
    const val DELAY_FAST_MIN = 100L
    const val DELAY_FAST_MAX = 300L

    const val DELAY_NORMAL_MIN = 500L
    const val DELAY_NORMAL_MAX = 1000L

    const val DELAY_SLOW_MIN = 1500L
    const val DELAY_SLOW_MAX = 3000L

    // Таймауты
    const val ELEMENT_WAIT_TIMEOUT = 10000L // 10 секунд
    const val AUTO_ADAPT_TIMEOUT = 15000L // 15 секунд с автоадаптацией

    // Тексты успеха
    val SUCCESS_TEXTS = listOf("Вы записаны", "Вы в очереди")

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "oz_bot_channel"
    const val NOTIFICATION_ID = 1001
}