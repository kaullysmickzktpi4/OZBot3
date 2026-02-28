package com.ozbot.models

data class ShiftDay(
    val date: String, // "01.02"
    val dayNumber: String, // "1", "2", "3"...
    val type: ShiftDayType,
    val bounds: android.graphics.Rect
)

data class TimeSlot(
    val timeRange: String, // "8:00 – 20:00"
    val price12h: String, // "≈8 266 ₽ / 12 ч"
    val price1h: String, // "≈688 ₽ / 1 ч"
    val probability: String?, // "Высокая вероятность записи"
    val isQueue: Boolean,
    val checkboxBounds: android.graphics.Rect
)