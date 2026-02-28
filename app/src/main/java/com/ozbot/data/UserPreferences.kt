package com.ozbot.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ozbot.automation.config.TimeSlot

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "OZBotPreferences",
        Context.MODE_PRIVATE
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

        // Telegram keys (личные уведомления)
        private const val KEY_TG_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TG_CHAT_ID = "telegram_chat_id"
        private const val KEY_TG_ENABLED = "telegram_enabled"
        private const val KEY_TG_REPORT_INTERVAL = "telegram_report_interval"

        // ✅ НОВЫЕ: Telegram для друзей
        private const val KEY_FRIENDS_NOTIFY_ENABLED = "friends_notify_enabled"
        private const val KEY_FRIENDS_TG_BOT_TOKEN = "friends_telegram_bot_token"
        private const val KEY_FRIENDS_TG_CHAT_ID = "friends_telegram_chat_id"
    }

    // Склад
    var warehouse: String
        get() = prefs.getString(KEY_WAREHOUSE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WAREHOUSE, value).apply()

    // Скорость
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

    // Процесс
    var process: String
        get() = prefs.getString(KEY_PROCESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROCESS, value).apply()

    // Даты для записи
    var targetDates: List<String>
        get() {
            val json = prefs.getString(KEY_TARGET_DATES, "[]") ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString(KEY_TARGET_DATES, json).apply()
        }

    // Временные слоты
    var timeSlots: List<TimeSlot>
        get() {
            val json = prefs.getString(KEY_TIME_SLOTS, "[]") ?: "[]"
            val type = object : TypeToken<List<TimeSlot>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString(KEY_TIME_SLOTS, json).apply()
        }

    // Разрешить очередь
    var allowQueue: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_QUEUE, true)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_QUEUE, value).apply()

    // Минимальная задержка
    var delayMin: Int
        get() = prefs.getInt(KEY_DELAY_MIN, 1000)
        set(value) = prefs.edit().putInt(KEY_DELAY_MIN, value).apply()

    // Максимальная задержка
    var delayMax: Int
        get() = prefs.getInt(KEY_DELAY_MAX, 3000)
        set(value) = prefs.edit().putInt(KEY_DELAY_MAX, value).apply()

    // Флаг первичной настройки
    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()

    // Автозапуск при запуске приложения
    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    // ==================== TELEGRAM (личные) ====================

    var telegramBotToken: String
        get() = prefs.getString(KEY_TG_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_BOT_TOKEN, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TG_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_CHAT_ID, value).apply()

    var telegramEnabled: Boolean
        get() = prefs.getBoolean(KEY_TG_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TG_ENABLED, value).apply()

    var telegramReportIntervalMin: Int
        get() = prefs.getInt(KEY_TG_REPORT_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_TG_REPORT_INTERVAL, value).apply()

    // ==================== TELEGRAM (для друзей) ====================

    var friendsNotifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_FRIENDS_NOTIFY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FRIENDS_NOTIFY_ENABLED, value).apply()

    var friendsTelegramBotToken: String
        get() = prefs.getString(KEY_FRIENDS_TG_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FRIENDS_TG_BOT_TOKEN, value).apply()

    var friendsTelegramChatId: String
        get() = prefs.getString(KEY_FRIENDS_TG_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FRIENDS_TG_CHAT_ID, value).apply()

    /**
     * Проверка валидности настроек
     */
    fun isValid(): Boolean {
        return warehouse.isNotBlank() &&
                process.isNotBlank() &&
                targetDates.isNotEmpty() &&
                timeSlots.isNotEmpty()
    }

    /**
     * Очистка всех настроек
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}