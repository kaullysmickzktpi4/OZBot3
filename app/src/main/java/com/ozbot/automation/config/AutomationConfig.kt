package com.ozbot.automation.config

/**
 * Конфигурация автоматизации
 */
data class AutomationConfig(
    val processName: String,
    val targetDates: List<String>,
    val timeSlots: List<TimeSlot>,
    val allowQueueEnrollment: Boolean = true,
    val minDuration: Int = 12,
    val delays: AutomationDelays = AutomationDelays()
)

/**
 * Задержки между действиями
 */
data class AutomationDelays(
    val afterNavigation: Long = 1000,
    val afterWarehouseSelection: Long = 1500,
    val afterProcessSelection: Long = 1500,
    val afterScroll: Long = 800,
    val afterClick: Long = 500,
    val beforeRetry: Long = 2000,
    val afterMonthSwitch: Long = 1000
)

// УДАЛИТЕ КЛАСС TimeSlot ОТСЮДА - он теперь в отдельном файле TimeSlot.kt