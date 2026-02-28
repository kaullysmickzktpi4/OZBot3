package com.ozbot.automation.monitoring

data class AvailableShift(
    val process: String,
    val date: String,
    val warehouse: String,
    val firstSeenTime: Long,
    val lastSeenTime: Long
) {
    fun toShortString(): String = "$date ($process)"

    fun toFullString(): String = """
        📅 Дата: $date
        📦 Процесс: $process
        🏭 Склад: $warehouse
    """.trimIndent()
}

class ShiftMonitor {
    private val knownShifts = mutableMapOf<String, AvailableShift>()
    private val notifiedShifts = mutableSetOf<String>()

    private fun getKey(shift: AvailableShift): String {
        return "${shift.date}|${shift.process}|${shift.warehouse}"
    }

    fun updateShift(shift: AvailableShift): Boolean {
        val key = getKey(shift)
        val existing = knownShifts[key]

        if (existing == null) {
            knownShifts[key] = shift
            return true
        } else {
            knownShifts[key] = shift.copy(
                firstSeenTime = existing.firstSeenTime,
                lastSeenTime = System.currentTimeMillis()
            )
            return false
        }
    }

    fun wasNotified(shift: AvailableShift): Boolean {
        return notifiedShifts.contains(getKey(shift))
    }

    fun markNotified(shift: AvailableShift) {
        notifiedShifts.add(getKey(shift))
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        val staleThreshold = 5 * 60 * 1000L

        val iterator = knownShifts.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeenTime > staleThreshold) {
                iterator.remove()
                notifiedShifts.remove(entry.key)
            }
        }
    }

    fun getAllShifts(): List<AvailableShift> {
        return knownShifts.values.toList()
    }

    fun reset() {
        knownShifts.clear()
        notifiedShifts.clear()
    }
}