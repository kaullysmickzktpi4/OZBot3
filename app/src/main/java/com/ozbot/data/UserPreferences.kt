package com.ozbot.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ozbot.automation.config.TimeSlot

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "OZBotPreferences", Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val KEY_WAREHOUSE = "warehouse"
        private const val KEY_PROCESS = "process"
        private const val KEY_TARGET_DATES = "target_dates"
        private const val KEY_TIME_SLOTS = "time_slots"
        private const val KEY_ALLOW_QUEUE = "allow_queue"
        private const val KEY_DELAY_MIN = "delay_min"
        private const val KEY_DELAY_MAX = "delay_max"
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_SPEED_PROFILE = "speed_profile"
        private const val KEY_TG_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TG_CHAT_ID = "telegram_chat_id"
        private const val KEY_TG_ENABLED = "telegram_enabled"
        private const val KEY_TG_REPORT_INTERVAL = "telegram_report_interval"
        private const val KEY_ADMIN_CHAT_ID = "admin_chat_id"
        private const val KEY_WHITELIST = "tg_whitelist"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_LABEL = "device_label"
    }

    // ==================== DEVICE IDENTITY ====================

    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, "") ?: ""
            if (id.isBlank()) {
                id = java.util.UUID.randomUUID().toString().take(8).uppercase()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    var deviceLabel: String
        get() = prefs.getString(KEY_DEVICE_LABEL, Build.MODEL) ?: Build.MODEL
        set(value) = prefs.edit().putString(KEY_DEVICE_LABEL, value).apply()

    // ==================== ОСНОВНЫЕ НАСТРОЙКИ ====================

    var warehouse: String
        get() = prefs.getString(KEY_WAREHOUSE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WAREHOUSE, value).apply()

    var speedProfile: String
        get() {
            val raw = prefs.getString(KEY_SPEED_PROFILE, "NORMAL") ?: "NORMAL"
            return when (raw) {
                "AUTO", "TURBO" -> "NORMAL"
                "SLOW", "NORMAL", "FAST" -> raw
                else -> "NORMAL"
            }
        }
        set(value) = prefs.edit().putString(KEY_SPEED_PROFILE, value).apply()

    var process: String
        get() = prefs.getString(KEY_PROCESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROCESS, value).apply()

    var targetDates: List<String>
        get() {
            val json = prefs.getString(KEY_TARGET_DATES, "[]") ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) = prefs.edit().putString(KEY_TARGET_DATES, gson.toJson(value)).apply()

    var timeSlots: List<TimeSlot>
        get() {
            val json = prefs.getString(KEY_TIME_SLOTS, "[]") ?: "[]"
            val type = object : TypeToken<List<TimeSlot>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) = prefs.edit().putString(KEY_TIME_SLOTS, gson.toJson(value)).apply()

    var allowQueue: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_QUEUE, true)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_QUEUE, value).apply()

    var delayMin: Int
        get() = prefs.getInt(KEY_DELAY_MIN, 1000)
        set(value) = prefs.edit().putInt(KEY_DELAY_MIN, value).apply()

    var delayMax: Int
        get() = prefs.getInt(KEY_DELAY_MAX, 3000)
        set(value) = prefs.edit().putInt(KEY_DELAY_MAX, value).apply()

    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    // ==================== TELEGRAM ====================

    var telegramBotToken: String
        get() = prefs.getString(KEY_TG_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_BOT_TOKEN, value).apply()

    var telegramChatId: String
        get() = adminChatId
        set(value) { adminChatId = value }

    var telegramEnabled: Boolean
        get() = prefs.getBoolean(KEY_TG_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TG_ENABLED, value).apply()

    var telegramReportIntervalMin: Int
        get() = prefs.getInt(KEY_TG_REPORT_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_TG_REPORT_INTERVAL, value).apply()

    var adminChatId: String
        get() = prefs.getString(KEY_ADMIN_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ADMIN_CHAT_ID, value).apply()

    var whitelist: Set<String>
        get() {
            val json = prefs.getString(KEY_WHITELIST, "[]") ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            return (gson.fromJson(json, type) as List<String>).toSet()
        }
        set(value) = prefs.edit().putString(KEY_WHITELIST, gson.toJson(value.toList())).apply()

    // ==================== UTILS ====================

    fun isValid(): Boolean {
        return warehouse.isNotBlank() &&
                process.isNotBlank() &&
                targetDates.isNotEmpty() &&
                timeSlots.isNotEmpty()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}