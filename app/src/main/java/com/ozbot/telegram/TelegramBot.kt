package com.ozbot.telegram

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
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
        fun onScreenshot(replyToChatId: String)
        fun onAddUser(targetChatId: String): String
        fun onRemoveUser(targetChatId: String): String
        fun onListUsers(): String
        fun onSetLabel(newLabel: String): String
        fun onGetDeviceInfo(): String
    }

    private const val TAG = "TelegramBot"

    // ==================== ЗАШИТЫЕ ДАННЫЕ ====================
    private const val HARDCODED_TOKEN = "8505350967:AAEKGLeBM3i-svRANRy2LzX_3ooP99fQYVA"
    private const val HARDCODED_ADMIN = "338039305"

    // ==================== СОСТОЯНИЕ ====================
    @Volatile private var botToken: String = HARDCODED_TOKEN
    @Volatile private var adminChatId: String = HARDCODED_ADMIN
    @Volatile private var deviceId: String = ""
    @Volatile private var deviceLabel: String = ""
    @Volatile private var whitelist: MutableSet<String> = mutableSetOf()
    @Volatile private var enabled: Boolean = true
    @Volatile private var commandHandler: CommandHandler? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Volatile private var lastSendTime = 0L
    @Volatile private var pollingJob: Job? = null
    @Volatile private var updateOffset: Long = 0L
    private const val MIN_SEND_INTERVAL_MS = 1000L

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    fun init(
        token: String,
        admin: String,
        devId: String,
        devLabel: String,
        wl: Set<String>
    ) {
        // Токен и admin всегда берём зашитые — игнорируем переданные
        botToken = HARDCODED_TOKEN
        adminChatId = HARDCODED_ADMIN
        deviceId = devId
        deviceLabel = devLabel
        whitelist = wl.toMutableSet()
        enabled = true
        updateOffset = 0L
        Log.d(TAG, "TelegramBot initialized. device=$deviceLabel[$deviceId], admin=$adminChatId, whitelist=${whitelist.size}")
    }

    fun isEnabled(): Boolean = enabled

    fun setCommandHandler(handler: CommandHandler?) {
        commandHandler = handler
    }

    fun updateWhitelist(newWhitelist: Set<String>) {
        whitelist = newWhitelist.toMutableSet()
    }

    // ==================== POLLING ====================

    fun startPollingCommands() {
        if (!enabled) return
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            syncOffsetToLatestUpdate()
            Log.d(TAG, "▶️ Polling started")
            while (isActive && enabled) {
                try {
                    pollUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                    delay(2000L)
                }
            }
            Log.d(TAG, "⏹ Polling stopped")
        }
    }

    fun stopPollingCommands() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ==================== ОТПРАВКА ====================

    fun send(message: String, silent: Boolean = false) {
        sendTo(adminChatId, message, silent)
    }

    fun sendTo(targetChatId: String, message: String, silent: Boolean = false) {
        if (targetChatId.isBlank()) return
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                if (now - lastSendTime < MIN_SEND_INTERVAL_MS) delay(MIN_SEND_INTERVAL_MS)
                sendTelegramText(botToken, targetChatId, message, silent)
                lastSendTime = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "sendTo error: ${e.message}")
            }
        }
    }

    fun sendDeviceOnline() {
        send("""
🟢 <b>Устройство подключено</b>
📱 $deviceLabel
🆔 DeviceID: <code>$deviceId</code>
⏰ ${dateFormat.format(java.util.Date())}

Используй <code>/setlabel ИМЯ</code> чтобы задать имя
    """.trimIndent())
    }

    fun sendPhoto(targetChatId: String, photoBytes: ByteArray, caption: String = "") {
        if (targetChatId.isBlank()) return
        scope.launch {
            try {
                sendTelegramPhoto(botToken, targetChatId, photoBytes, caption)
            } catch (e: Exception) {
                Log.e(TAG, "sendPhoto error: ${e.message}")
            }
        }
    }

    fun sendToFriendsChat(friendsBotToken: String, friendsChatId: String, message: String) {
        if (friendsBotToken.isBlank() || friendsChatId.isBlank()) return
        scope.launch {
            try {
                sendTelegramText(friendsBotToken, friendsChatId, message)
            } catch (e: Exception) {
                Log.e(TAG, "sendToFriendsChat error: ${e.message}")
            }
        }
    }

    // ==================== HTTP ====================

    private suspend fun sendTelegramText(
        token: String, chat: String, text: String, silent: Boolean = false
    ) {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val json = JSONObject().apply {
            put("chat_id", chat)
            put("text", text)
            put("parse_mode", "HTML")
            put("disable_notification", silent)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "sendMessage error: ${response.code} ${response.body?.string()}")
            }
        }
    }

    private suspend fun sendTelegramPhoto(
        token: String, chat: String, photoBytes: ByteArray, caption: String
    ) {
        val url = "https://api.telegram.org/bot$token/sendPhoto"
        val tmpFile = File.createTempFile("ozbot_screen_", ".jpg")
        try {
            tmpFile.writeBytes(photoBytes)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chat)
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "photo", "screenshot.jpg",
                    tmpFile.asRequestBody("image/jpeg".toMediaType())
                )
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "sendPhoto error: ${response.code} ${response.body?.string()}")
                }
            }
        } finally {
            tmpFile.delete()
        }
    }

    // ==================== POLLING LOGIC ====================

    private suspend fun syncOffsetToLatestUpdate() {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?timeout=1&limit=1"
        val request = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) return
                val result = json.optJSONArray("result") ?: return
                if (result.length() > 0) {
                    val last = result.optJSONObject(result.length() - 1)
                    updateOffset = last.optLong("update_id", 0L) + 1
                }
            }
        }.onFailure { Log.w(TAG, "Offset sync failed: ${it.message}") }
    }

    private suspend fun pollUpdates() {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?timeout=25&offset=$updateOffset"
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
                if (updateId >= updateOffset) updateOffset = updateId + 1

                val message = update.optJSONObject("message") ?: continue
                val senderChatId = message.optJSONObject("chat")?.optString("id") ?: continue
                val text = message.optString("text", "").trim()
                if (text.isBlank()) continue

                val isAdmin = senderChatId == adminChatId
                val isWhitelisted = whitelist.contains(senderChatId)

                if (!isAdmin && !isWhitelisted) {
                    Log.w(TAG, "Unauthorized: chatId=$senderChatId")
                    sendTo(senderChatId, "⛔ У вас нет доступа к этому боту.")
                    continue
                }

                handleIncomingCommand(text, senderChatId, isAdmin)
            }
        }
    }

    // ==================== ОБРАБОТКА КОМАНД ====================

    private fun handleIncomingCommand(rawText: String, senderChatId: String, isAdmin: Boolean) {
        val handler = commandHandler
        if (handler == null) {
            sendTo(senderChatId, "⚠️ Сервис автоматизации не подключён. Включите Accessibility Service.")
            return
        }

        val text = rawText.lowercase(Locale.getDefault())
        val tag = "📱 <b>$deviceLabel</b> [<code>$deviceId</code>]\n"

        when {
            text == "/help" || text == "помощь" -> {
                val adminPart = if (isAdmin) """

<i>👑 Только для администратора:</i>
/adduser 123456789 — добавить пользователя
/removeuser 123456789 — удалить пользователя
/listusers — список пользователей""" else ""
                sendTo(senderChatId, """
${tag}📘 <b>Команды OZBot</b>

/startbot — запустить автоматизацию
/stopbot — остановить
/status — статус устройства
/dates — список дат поиска
/adddate 18.03 — добавить дату
/removedate 18.03 — удалить дату
/screenshot — скриншот экрана$adminPart
                """.trimIndent())
            }
            text == "/startbot" || text == "старт" || text == "запусти" ->
                sendTo(senderChatId, tag + handler.onStartAutomation())
            text == "/stopbot" || text == "стоп" || text == "останови" ->
                sendTo(senderChatId, tag + handler.onStopAutomation())
            text == "/status" || text == "статус" ->
                sendTo(senderChatId, tag + handler.onStatus())
            text == "/dates" || text == "даты" || text == "список дат" ->
                sendTo(senderChatId, tag + handler.onListDates())
            text.startsWith("/adddate") || text.startsWith("добав") -> {
                val parsed = parseDateArgument(rawText)
                if (parsed == null) sendTo(senderChatId, "❌ Не понял дату. Пример: /adddate 18.03")
                else sendTo(senderChatId, tag + handler.onAddDate(parsed))
            }
            text.startsWith("/removedate") || text.startsWith("удал") -> {
                val parsed = parseDateArgument(rawText)
                if (parsed == null) sendTo(senderChatId, "❌ Не понял дату. Пример: /removedate 18.03")
                else sendTo(senderChatId, tag + handler.onRemoveDate(parsed))
            }
            text == "/screenshot" || text == "скрин" || text == "скриншот" -> {
                sendTo(senderChatId, "${tag}⏳ Делаю скриншот...")
                handler.onScreenshot(senderChatId)
            }
            text.startsWith("/adduser") -> {
                if (!isAdmin) { sendTo(senderChatId, "⛔ Только для администратора"); return }
                val targetId = rawText.removePrefix("/adduser").trim()
                if (targetId.isBlank()) { sendTo(senderChatId, "❌ Укажите chatId: /adduser 123456789"); return }
                sendTo(senderChatId, tag + handler.onAddUser(targetId))
            }
            text.startsWith("/removeuser") -> {
                if (!isAdmin) { sendTo(senderChatId, "⛔ Только для администратора"); return }
                val targetId = rawText.removePrefix("/removeuser").trim()
                if (targetId.isBlank()) { sendTo(senderChatId, "❌ Укажите chatId: /removeuser 123456789"); return }
                sendTo(senderChatId, tag + handler.onRemoveUser(targetId))
            }
            text == "/listusers" -> {
                if (!isAdmin) { sendTo(senderChatId, "⛔ Только для администратора"); return }
                sendTo(senderChatId, tag + handler.onListUsers())
            }
            text.startsWith("/setlabel") -> {
                if (!isAdmin) { sendTo(senderChatId, "⛔ Только для администратора"); return }
                val newLabel = rawText.removePrefix("/setlabel").trim()
                if (newLabel.isBlank()) { sendTo(senderChatId, "❌ Укажите имя: /setlabel Телефон_Вани"); return }
                sendTo(senderChatId, tag + handler.onSetLabel(newLabel))
            }

            text == "/myid" || text == "/deviceinfo" -> {
                sendTo(senderChatId, tag + handler.onGetDeviceInfo())
            }

            else -> sendTo(senderChatId, "❓ Неизвестная команда. Напиши /help")
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
        val dotFormat = Regex("(\\d{1,2})\\.(\\d{1,2})")
        dotFormat.find(cleaned)?.let { m ->
            val day = m.groupValues[1].padStart(2, '0')
            val month = m.groupValues[2].padStart(2, '0')
            return "$day.$month"
        }
        return null
    }

    // ==================== УВЕДОМЛЕНИЯ ====================

    fun sendBotStarted(profile: String) {
        send("🚀 <b>OZ Bot запущен</b>\n📱 $deviceLabel [<code>$deviceId</code>]\n⚡ Профиль: <code>$profile</code>\n🕐 ${dateFormat.format(Date())}")
    }

    fun sendBotStopped(totalRestarts: Int, runtime: String) {
        send("🛑 <b>OZ Bot остановлен</b>\n📱 $deviceLabel [<code>$deviceId</code>]\n⏱ Работал: $runtime\n🔄 Перезапусков: $totalRestarts")
    }

    fun sendBookingSuccess(process: String, date: String, time: String) {
        send("""
✅ <b>СМЕНА ЗАБРОНИРОВАНА!</b>
📱 $deviceLabel [<code>$deviceId</code>]
📋 Процесс: $process
📅 Дата: $date
🕐 Время: $time
        """.trimIndent())
    }

    fun sendStatusReport(
        ramUsedMb: Long, ramMaxMb: Long, profile: String,
        tickCount: Long, restartCount: Int, uptime: String
    ) {
        val ramPercent = if (ramMaxMb > 0) (ramUsedMb * 100 / ramMaxMb) else 0
        val ramBar = buildProgressBar(ramPercent.toInt(), 10)
        send("""
📊 <b>Статус OZ Bot</b>
📱 $deviceLabel [<code>$deviceId</code>]

💾 RAM: ${ramUsedMb}MB / ${ramMaxMb}MB
$ramBar $ramPercent%
⚡ Скорость: <code>$profile</code>
🔄 Тики: $tickCount | ♻️ Рестарты: $restartCount
⏱ Аптайм: $uptime
        """.trimIndent(), silent = true)
    }

    fun sendFreezeAlert(frozenSeconds: Long, restartNumber: Int) {
        send("🥶 <b>FREEZE DETECTED!</b>\n📱 $deviceLabel [<code>$deviceId</code>]\n⏱ UI не менялся: ${frozenSeconds}s\n🔄 Перезапуск #$restartNumber")
    }

    fun sendRestartComplete() {
        send("✅ Перезапущено, продолжаю работу\n📱 $deviceLabel [<code>$deviceId</code>]", silent = true)
    }

    fun sendError(error: String) {
        send("❌ <b>Ошибка</b>\n📱 $deviceLabel [<code>$deviceId</code>]\n<code>$error</code>")
    }

    fun sendMemoryWarning(usedMb: Long, maxMb: Long) {
        val percent = if (maxMb > 0) (usedMb * 100 / maxMb) else 0
        send("⚠️ <b>Высокое потребление RAM!</b>\n📱 $deviceLabel [<code>$deviceId</code>]\n💾 ${usedMb}MB / ${maxMb}MB ($percent%)")
    }

    fun sendTestMessage(): Boolean {
        send("✅ OZ Bot подключен!\n📱 $deviceLabel [<code>$deviceId</code>]")
        return true
    }

    // Отправить сообщение с inline-кнопками
    fun sendWithButtons(message: String, buttons: List<List<Pair<String, String>>>) {
        scope.launch {
            try {
                sendTelegramTextWithButtons(botToken, adminChatId, message, buttons)
            } catch (e: Exception) {
                Log.e(TAG, "sendWithButtons error: ${e.message}")
            }
        }
    }

    private suspend fun sendTelegramTextWithButtons(
        token: String,
        chat: String,
        text: String,
        buttons: List<List<Pair<String, String>>> // [[("текст", "callback_data"), ...], ...]
    ) {
        val url = "https://api.telegram.org/bot$token/sendMessage"

        // Строим inline_keyboard
        val keyboard = org.json.JSONArray()
        for (row in buttons) {
            val jsonRow = org.json.JSONArray()
            for ((label, data) in row) {
                jsonRow.put(org.json.JSONObject().apply {
                    put("text", label)
                    put("callback_data", data)
                })
            }
            keyboard.put(jsonRow)
        }

        val replyMarkup = org.json.JSONObject().apply {
            put("inline_keyboard", keyboard)
        }

        val json = org.json.JSONObject().apply {
            put("chat_id", chat)
            put("text", text)
            put("parse_mode", "HTML")
            put("reply_markup", replyMarkup)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "sendWithButtons error: ${response.code} ${response.body?.string()}")
            }
        }
    }

    // Ответ на нажатие кнопки (убирает часики на кнопке)
    private suspend fun answerCallbackQuery(callbackQueryId: String) {
        val url = "https://api.telegram.org/bot$botToken/answerCallbackQuery"
        val json = org.json.JSONObject().apply {
            put("callback_query_id", callbackQueryId)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        runCatching { client.newCall(request).execute().close() }
    }
    private fun buildProgressBar(percent: Int, length: Int): String {
        val filled = (percent * length / 100).coerceIn(0, length)
        return "▓".repeat(filled) + "░".repeat(length - filled)
    }

    fun shutdown() {
        stopPollingCommands()
        commandHandler = null
        scope.cancel()
    }
}