package com.ozbot.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "automation_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val level: String, // INFO, WARNING, ERROR
    val message: String,
    val timestamp: Date = Date()
)