package com.ozbot.data.repository

import com.ozbot.data.database.AppDatabase
import com.ozbot.data.database.BookingEntity
import com.ozbot.data.database.BookingStatus
import com.ozbot.data.database.LogEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

class BookingRepository(private val database: AppDatabase) {

    private val bookingDao = database.bookingDao()
    private val logDao = database.logDao()

    fun getAllBookings(): Flow<List<BookingEntity>> {
        return bookingDao.getAllBookings()
    }

    fun getBookingsByStatus(status: BookingStatus): Flow<List<BookingEntity>> {
        return bookingDao.getBookingsByStatus(status.name)
    }

    suspend fun getBookedDates(): List<String> {
        return bookingDao.getBookedDates(
            listOf(BookingStatus.SUCCESS.name, BookingStatus.PENDING.name)
        )
    }

    suspend fun recordBooking(
        warehouseName: String,
        processName: String,
        date: String,
        timeSlot: String,
        status: BookingStatus,
        errorMessage: String? = null
    ) {
        val booking = BookingEntity(
            warehouseName = warehouseName,
            processName = processName,
            date = date,
            timeSlot = timeSlot,
            status = status.name,
            errorMessage = errorMessage
        )

        bookingDao.insert(booking)

        val logMessage = when (status) {
            BookingStatus.SUCCESS -> "✅ Успешно записан на $date $timeSlot в $warehouseName ($processName)"
            BookingStatus.PENDING -> "⏳ Ожидание подтверждения для $date $timeSlot"
            BookingStatus.FAILED -> "❌ Не удалось записаться: ${errorMessage ?: "неизвестная ошибка"}"
        }

        logDao.insert(LogEntity(
            level = status.name,
            message = logMessage
        ))
    }

    suspend fun updateBookingStatus(
        bookingId: Long,
        newStatus: BookingStatus,
        errorMessage: String? = null
    ) {
        val booking = bookingDao.getAllBookings()
        // Находим и обновляем нужную запись
        // TODO: реализовать getById в DAO
    }

    suspend fun logEvent(level: String, message: String) {
        logDao.insert(LogEntity(
            level = level,
            message = message
        ))
    }

    suspend fun clearOldLogs(daysToKeep: Int = 7) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        logDao.deleteOldLogs(cutoffTime)
    }

    fun getSuccessfulBookingsCount(): Flow<Int> {
        return bookingDao.getCountByStatus(BookingStatus.SUCCESS.name)
    }

    fun getFailedBookingsCount(): Flow<Int> {
        return bookingDao.getCountByStatus(BookingStatus.FAILED.name)
    }

    suspend fun clearAllBookings() {
        bookingDao.clearAll()
    }

    suspend fun clearAllLogs() {
        logDao.clearAll()
    }

    fun getRecentLogs(): Flow<List<LogEntity>> {
        return logDao.getRecentLogs()
    }
}