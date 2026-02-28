package com.ozbot.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "booking_records")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val warehouseName: String,
    val processName: String,
    val date: String,
    val timeSlot: String,
    val status: String,
    val createdAt: Date = Date(),
    val errorMessage: String? = null
)