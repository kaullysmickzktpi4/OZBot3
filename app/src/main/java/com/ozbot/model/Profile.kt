package com.ozbot.model

data class Profile(
    val id: Long = 0,
    val name: String,                    // Название профиля
    val processName: String,             // Название процесса ("Инвентаризация", "Подбор")
    val targetDates: List<String>,       // Даты для записи ["01.02", "02.02"]
    val preferredTime: String,           // Предпочитаемое время "8:00 – 20:00"
    val allowQueue: Boolean = true,      // Записываться в очередь?
    val isActive: Boolean = false,       // Активен ли профиль
    val delayBeforeWarehouse: Long = 2000,  // Задержка перед переходом в "Склады"
    val delayBeforeBooking: Long = 1500,    // Задержка перед нажатием "Записаться"
    val delayAfterCalendarLoad: Long = 1000 // Задержка после загрузки календаря
)