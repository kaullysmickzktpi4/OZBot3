package com.ozbot.model

enum class ShiftStatus {
    AVAILABLE,      // availableShift - можно записаться
    QUEUE,          // queueShift - очередь
    BOOKED,         // dayShift - уже записан
    UNAVAILABLE;    // unavailableShift - недоступно

    companion object {
        fun fromResourceId(resourceId: String): ShiftStatus {
            return when {
                resourceId.contains("availableShift") -> AVAILABLE
                resourceId.contains("queueShift") -> QUEUE
                resourceId.contains("dayShift") -> BOOKED
                else -> UNAVAILABLE
            }
        }
    }
}