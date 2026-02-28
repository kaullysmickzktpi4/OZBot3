package com.ozbot.automation

/**
 * Результат записи на смену
 */
data class EnrollmentResult(
    val date: String,
    val processName: String,
    val timeSlot: String,
    val success: Boolean,
    val isQueue: Boolean = false,
    val errorMessage: String? = null
)