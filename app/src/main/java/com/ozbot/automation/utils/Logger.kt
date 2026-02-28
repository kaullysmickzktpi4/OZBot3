package com.ozbot.automation.utils

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Logger(private val filesDir: File) {
    companion object {
        private const val TAG = "OzonBot"
        private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024L
    }

    private var logFile: File? = null
    private val logDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH", Locale.getDefault())

    @Volatile
    private var lastLogRotationCheck = 0L

    @Volatile
    var enabled = true

    init {
        initializeLogging()
    }

    private fun initializeLogging() {
        try {
            val logsDir = File(filesDir, "logs")
            if (!logsDir.exists()) logsDir.mkdirs()
            rotateLogIfNeeded(logsDir)
            cleanupOldLogs(logsDir, 3)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logging: ${e.message}")
            enabled = false
        }
    }

    private fun rotateLogIfNeeded(logsDir: File) {
        val now = System.currentTimeMillis()
        if (now - lastLogRotationCheck < 60_000) return
        lastLogRotationCheck = now

        val fileName = "ozbot_${fileDateFormat.format(Date())}.log"
        val newFile = File(logsDir, fileName)

        if (logFile?.absolutePath != newFile.absolutePath) {
            logFile = newFile
        }

        logFile?.let { file ->
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                try {
                    val content = file.readText()
                    val keepFrom = (content.length - 1024 * 1024).coerceAtLeast(0)
                    file.writeText(content.substring(keepFrom))
                } catch (_: Exception) {
                    file.delete()
                }
            }
        }
    }

    private fun cleanupOldLogs(logsDir: File, keepDays: Int) {
        try {
            val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
            logsDir.listFiles()?.forEach {
                if (it.lastModified() < cutoff) it.delete()
            }
        } catch (_: Exception) {}
    }

    private fun logToFile(level: String, message: String) {
        if (!enabled) return
        try {
            val timestamp = logDateFormat.format(Date())
            logFile?.appendText("[$timestamp][$level] $message\n")
        } catch (_: Exception) {}
    }

    fun d(message: String) {
        Log.d(TAG, message)
        logToFile("D", message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
        logToFile("W", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        logToFile("E", "$message ${throwable?.message ?: ""}")
    }

    fun getLogFilePath(): String? = logFile?.absolutePath
}