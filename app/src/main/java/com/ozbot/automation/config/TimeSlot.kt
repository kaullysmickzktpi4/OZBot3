package com.ozbot.automation.config

data class TimeSlot(
    val startTime: String,  // "08:00"
    val endTime: String,    // "20:00"
    val duration: Int = 12  // часов
) {
    fun toDisplayString(): String {
        return "$startTime–$endTime"
    }

    companion object {
        fun parse(displayString: String): TimeSlot? {
            // "08:00–20:00" -> TimeSlot
            val parts = displayString.split("–", "-")
            if (parts.size != 2) return null

            val start = parts[0].trim()
            val end = parts[1].trim()

            return try {
                val startHour = start.split(":")[0].toInt()
                val endHour = end.split(":")[0].toInt()
                val duration = if (endHour > startHour) {
                    endHour - startHour
                } else {
                    24 - startHour + endHour
                }

                TimeSlot(start, end, duration)
            } catch (e: Exception) {
                null
            }
        }
    }
}