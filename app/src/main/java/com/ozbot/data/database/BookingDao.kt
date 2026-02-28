package com.ozbot.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Query("SELECT * FROM booking_records ORDER BY createdAt DESC")
    fun getAllBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM booking_records WHERE status = :status")
    fun getBookingsByStatus(status: String): Flow<List<BookingEntity>>

    @Query("SELECT DISTINCT date FROM booking_records WHERE status IN (:statuses)")
    suspend fun getBookedDates(statuses: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(booking: BookingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookings: List<BookingEntity>)

    @Update
    suspend fun update(booking: BookingEntity)

    @Delete
    suspend fun delete(booking: BookingEntity)

    @Query("DELETE FROM booking_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM booking_records WHERE status = :status")
    fun getCountByStatus(status: String): Flow<Int>
}