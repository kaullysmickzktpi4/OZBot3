package com.ozbot.telegram

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object TelegramBot {

    private const val TAG = "TelegramBot"

    @Volatile private var botToken: String = ""
    @Volatile private var chatId: String = ""
    @Volatile private var enabled: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Volatile private var lastSendTime = 0L
    private const val MIN_SEND_INTERVAL_MS = 1000L

    fun init(token: String, chat: String) {
        botToken = token
        chatId = chat
        enabled = token.isNotBlank() && chat.isNotBlank()

        if (enabled) {
            Log.d(TAG, "Telegram bot initialized")
        }
    }

    fun isEnabled(): Boolean = enabled

    fun send(message: String, silent: Boolean = false) {
        if (!enabled) return

        scope.launch {
            try {
                val now = System.currentTimeMillis()
                if (now - lastSendTime < MIN_SEND_INTERVAL_MS) {
                    delay(MIN_SEND_INTERVAL_MS)
                }

                sendTelegram(botToken, chatId, message, silent)
                lastSendTime = System.currentTimeMillis()

            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Отправка в чат друзей
     */
    /**
     * ✅ Отправка в чат друзей (отдельный бот/чат)
     */
    fun sendToFriendsChat(friendsBotToken: String, friendsChatId: String, message: String) {
        if (friendsBotToken.isBlank() || friendsChatId.isBlank()) {
            Log.w(TAG, "❌ Friends chat not configured: token=${friendsBotToken.isNotBlank()}, chatId=${friendsChatId.isNotBlank()}")
            return
        }

        Log.d(TAG, "📤 Sending to friends chat: chatId=$friendsChatId")

        scope.launch {
            try {
                sendTelegram(friendsBotToken, friendsChatId, message, silent = false)
                Log.d(TAG, "✅ Friends notification sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Friends send error: ${e.message}", e)
            }
        }
    }

    private suspend fun sendTelegram(token: String, chat: String, text: String, silent: Boolean) {
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"

            Log.d(TAG, "📡 Sending to: $url")
            Log.d(TAG, "   Chat ID: $chat")
            Log.d(TAG, "   Message length: ${text.length}")

            val json = JSONObject().apply {
                put("chat_id", chat)
                put("text", text)
                put("parse_mode", "HTML")
                put("disable_notification", silent)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "📥 Response code: ${response.code}")
                Log.d(TAG, "📥 Response body: $responseBody")

                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ Telegram API error: ${response.code}")
                    Log.e(TAG, "   Response: $responseBody")
                } else {
                    Log.d(TAG, "✅ Message sent successfully")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ sendTelegram exception: ${e.message}", e)
            e.printStackTrace()
        }
    }

    // ==================== FORMATTED MESSAGES ====================

    fun sendBotStarted(profile: String) {
        send("""
🚀 <b>OZ Bot запущен</b>

⚡ Профиль: <code>$profile</code>
🕐 Время: ${dateFormat.format(Date())}
        """.trimIndent())
    }

    fun sendBotStopped(totalRestarts: Int, runtime: String) {
        send("""
🛑 <b>OZ Bot остановлен</b>

⏱ Работал: $runtime
🔄 Перезапусков: $totalRestarts
        """.trimIndent())
    }

    fun sendBookingSuccess(process: String, date: String, time: String) {
        send("""
✅ <b>СМЕНА ЗАБРОНИРОВАНА!</b>

📋 Процесс: $process
📅 Дата: $date
🕐 Время: $time
        """.trimIndent())
    }

    fun sendStatusReport(
        ramUsedMb: Long,
        ramMaxMb: Long,
        profile: String,
        tickCount: Long,
        restartCount: Int,
        uptime: String
    ) {
        val ramPercent = if (ramMaxMb > 0) (ramUsedMb * 100 / ramMaxMb) else 0
        val ramBar = buildProgressBar(ramPercent.toInt(), 10)

        send("""
📊 <b>Статус OZ Bot</b>

💾 RAM: ${ramUsedMb}MB / ${ramMaxMb}MB
$ramBar $ramPercent%

⚡ Скорость: <code>$profile</code>
🔄 Тики: $tickCount
♻️ Рестартов: $restartCount
⏱ Аптайм: $uptime
        """.trimIndent(), silent = true)
    }

    fun sendFreezeAlert(frozenSeconds: Long, restartNumber: Int) {
        send("""
🥶 <b>FREEZE DETECTED!</b>

⏱ UI не менялся: ${frozenSeconds}s
🔄 Перезапуск #$restartNumber
        """.trimIndent())
    }

    fun sendRestartComplete() {
        send("✅ Приложение перезапущено, продолжаю работу", silent = true)
    }

    fun sendError(error: String) {
        send("""
❌ <b>Ошибка</b>

<code>$error</code>
        """.trimIndent())
    }

    fun sendMemoryWarning(usedMb: Long, maxMb: Long) {
        val percent = if (maxMb > 0) (usedMb * 100 / maxMb) else 0
        send("""
⚠️ <b>Высокое потребление RAM!</b>

💾 ${usedMb}MB / ${maxMb}MB ($percent%)
        """.trimIndent())
    }

    fun sendTestMessage(): Boolean {
        if (!enabled) return false
        send("✅ OZ Bot подключен к Telegram!")
        return true
    }

    private fun buildProgressBar(percent: Int, length: Int): String {
        val filled = (percent * length / 100).coerceIn(0, length)
        val empty = length - filled
        return "▓".repeat(filled) + "░".repeat(empty)
    }

    fun shutdown() {
        scope.cancel()
    }
}