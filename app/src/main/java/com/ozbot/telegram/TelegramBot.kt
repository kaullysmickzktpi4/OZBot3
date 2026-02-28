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

    interface CommandHandler {
        fun onStartAutomation(): String
        fun onStopAutomation(): String
        fun onAddDate(date: String): String
        fun onRemoveDate(date: String): String
        fun onListDates(): String
        fun onStatus(): String
    }

    private const val TAG = "TelegramBot"

    @Volatile private var botToken: String = ""
    @Volatile private var chatId: String = ""
    @Volatile private var enabled: Boolean = false
    @Volatile private var commandHandler: CommandHandler? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Volatile private var lastSendTime = 0L
    @Volatile private var pollingJob: Job? = null
    @Volatile private var updateOffset: Long = 0L
    private const val MIN_SEND_INTERVAL_MS = 1000L

    fun init(token: String, chat: String) {
        botToken = token
        chatId = chat
        enabled = token.isNotBlank() && chat.isNotBlank()
        updateOffset = 0L

        if (enabled) {
            Log.d(TAG, "Telegram bot initialized")
        }
    }

    fun isEnabled(): Boolean = enabled

    fun setCommandHandler(handler: CommandHandler?) {
        commandHandler = handler
    }

    fun startPollingCommands() {
        if (!enabled) return
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            syncOffsetToLatestUpdate()
            Log.d(TAG, "▶️ Command polling started")
            while (isActive && enabled) {
                try {
                    pollUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                    delay(2000L)
                }
            }
            Log.d(TAG, "⏹️ Command polling stopped")
        }
    }

    fun stopPollingCommands() {
        pollingJob?.cancel()
        pollingJob = null
    }

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
        val url = "https://api.telegram.org/bot$token/sendMessage"

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
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Telegram API error: ${response.code} ${response.body?.string()}")
            }
        }
    }

    private suspend fun syncOffsetToLatestUpdate() {
        val token = botToken
        if (token.isBlank()) return

        val url = "https://api.telegram.org/bot$token/getUpdates?timeout=1&limit=1"
        val request = Request.Builder().url(url).get().build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) return

                val result = json.optJSONArray("result") ?: return
                if (result.length() == 0) return

                val last = result.optJSONObject(result.length() - 1)?.optLong("update_id", 0L) ?: 0L
                if (last > 0L) {
                    updateOffset = last + 1
                    Log.d(TAG, "⏭️ Skip old updates. New offset=$updateOffset")
                }
            }
        }.onFailure {
            Log.w(TAG, "Offset sync failed: ${it.message}")
        }
    }

    private suspend fun pollUpdates() {
        val token = botToken
        if (token.isBlank()) return

        val url = "https://api.telegram.org/bot$token/getUpdates?timeout=25&offset=$updateOffset"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val body = response.body?.string() ?: return
            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) return

            val result = json.optJSONArray("result") ?: return
            for (i in 0 until result.length()) {
                val update = result.optJSONObject(i) ?: continue
                val updateId = update.optLong("update_id", 0L)
                if (updateId >= updateOffset) {
                    updateOffset = updateId + 1
                }

                val message = update.optJSONObject("message") ?: continue
                val chat = message.optJSONObject("chat")?.optString("id") ?: continue
                if (chat != chatId) continue

                val text = message.optString("text", "").trim()
                if (text.isBlank()) continue

                handleIncomingCommand(text)
            }
        }
    }

    private fun handleIncomingCommand(rawText: String) {
        val handler = commandHandler
        if (handler == null) {
            send("⚠️ Командный обработчик не подключен")
            return
        }

        val text = rawText.lowercase(Locale.getDefault())
        when {
            text == "/help" || text == "помощь" -> {
                send(
                    """
📘 <b>Команды</b>
/startbot - запустить бот
/stopbot - остановить бот
/status - статус
/dates - список дат
/adddate 18.03 - добавить дату
/removedate 18.03 - удалить дату
                    """.trimIndent()
                )
            }
            text == "/startbot" || text == "старт" || text == "запусти" -> send(handler.onStartAutomation())
            text == "/stopbot" || text == "стоп" || text == "останови" -> send(handler.onStopAutomation())
            text == "/status" || text == "статус" -> send(handler.onStatus())
            text == "/dates" || text == "даты" || text == "список дат" -> send(handler.onListDates())
            text.startsWith("/adddate") || text.startsWith("добав") -> {
                val parsed = parseDateArgument(rawText)
                if (parsed == null) send("❌ Не понял дату. Пример: /adddate 18.03 или 'добавь 18 марта'")
                else send(handler.onAddDate(parsed))
            }
            text.startsWith("/removedate") || text.startsWith("удал") -> {
                val parsed = parseDateArgument(rawText)
                if (parsed == null) send("❌ Не понял дату. Пример: /removedate 18.03 или 'удали 18 марта'")
                else send(handler.onRemoveDate(parsed))
            }
            else -> send("❓ Неизвестная команда. Напиши /help")
        }
    }

    private fun parseDateArgument(rawText: String): String? {
        val cleaned = rawText
            .replace("/adddate", "", ignoreCase = true)
            .replace("/removedate", "", ignoreCase = true)
            .replace("добавь", "", ignoreCase = true)
            .replace("добавить", "", ignoreCase = true)
            .replace("удали", "", ignoreCase = true)
            .replace("удалить", "", ignoreCase = true)
            .trim()
            .replace(Regex("\\s+"), " ")

        val numeric = Regex("(\\d{1,2})[.\\-/ ](\\d{1,2})").find(cleaned)
        if (numeric != null) {
            val day = numeric.groupValues[1].toIntOrNull() ?: return null
            val month = numeric.groupValues[2].toIntOrNull() ?: return null
            if (day in 1..31 && month in 1..12) {
                return String.format(Locale.US, "%02d.%02d", day, month)
            }
        }

        val monthMap = mapOf(
            "январ" to 1,
            "феврал" to 2,
            "март" to 3,
            "апрел" to 4,
            "май" to 5,
            "июн" to 6,
            "июл" to 7,
            "август" to 8,
            "сентябр" to 9,
            "октябр" to 10,
            "ноябр" to 11,
            "декабр" to 12
        )

        val dayMatch = Regex("\\b(\\d{1,2})\\b").find(cleaned) ?: return null
        val day = dayMatch.groupValues[1].toIntOrNull() ?: return null
        if (day !in 1..31) return null

        val month = monthMap.entries.firstOrNull { cleaned.lowercase(Locale.getDefault()).contains(it.key) }?.value
            ?: return null

        return String.format(Locale.US, "%02d.%02d", day, month)
    }

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
        stopPollingCommands()
        commandHandler = null
        scope.cancel()
    }
}