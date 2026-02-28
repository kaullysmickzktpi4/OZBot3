package com.ozbot.models

data class BotConfig(
    val processName: String, // "Инвентаризация", "Подбор" и т.д.
    val targetDates: List<String>, // ["01.02", "02.02", "03.02"]
    val preferredTime: String, // "8:00 – 20:00"
    val allowQueue: Boolean = true, // Разрешить запись в очередь
    val retryIntervalMs: Long = 30000, // 30 секунд между проверками
    val isActive: Boolean = false
)

enum class ProcessType(val displayName: String) {
    SORTING_LARGE("Сортировка крупного товара"),
    SORTING_SMALL("Сортировка мелкого товара"),
    PICKING("Подбор"),
    INVENTORY("Инвентаризация"),
    RECEIVING("Приемка"),
    PACKING("Упаковка"),
    RETURNS("Возвраты"),
    CONSOLIDATION("Консолидация"),
    SORTING_NON_PROFILE("Сортировка непрофиль"),
    LOADING_UNLOADING("Погрузка и разгрузка"),
    PROBLEM_GOODS("Обработка проблемного товара"),
    PLACEMENT("Размещение"),
    PRODUCTION_NON_PROFILE("Производство непрофиль");

    companion object {
        fun fromDisplayName(name: String): ProcessType? {
            return values().find { it.displayName == name }
        }
    }
}

enum class ShiftDayType {
    AVAILABLE,      // availableShift - записываемся сразу
    QUEUE,          // queueShift - записываемся в очередь
    ALREADY_BOOKED, // dayShift - уже записаны
    UNAVAILABLE     // unavailableShift - недоступно
}