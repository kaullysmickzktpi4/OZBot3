package com.ozbot.automation

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.automation.actions.CalendarActions
import com.ozbot.automation.actions.FilterActions
import com.ozbot.automation.actions.ProcessActions
import com.ozbot.automation.actions.TimePickerActions
import com.ozbot.automation.actions.WarehouseActions
import com.ozbot.automation.core.ScreenDetector
import com.ozbot.automation.core.StateManager
import com.ozbot.automation.monitoring.FreezeDetector
import com.ozbot.automation.monitoring.ShiftMonitor
import com.ozbot.automation.monitoring.ShiftScanner
import com.ozbot.automation.utils.Logger
import com.ozbot.automation.utils.NodeTreeHelper
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.bot.DomUtils
import com.ozbot.data.UserPreferences
import com.ozbot.data.database.AppDatabase
import com.ozbot.data.repository.BookingRepository
import com.ozbot.automation.navigation.GestureHelper
import com.ozbot.automation.navigation.NavigationHelper
import com.ozbot.telegram.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.lang.ref.WeakReference
import com.ozbot.utils.ScreenshotHelper
import java.text.SimpleDateFormat
import java.util.Locale
import com.ozbot.automation.monitoring.MemoryManager

class OzonHireAutomationService : AccessibilityService() {

    companion object {
        private const val CHANNEL_ID = "ozbot_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TARGET_PACKAGE = "ru.ozon.hire"

        @Volatile
        private var instance: WeakReference<OzonHireAutomationService>? = null
        fun getInstance() = instance?.get()
    }

    // Основные компоненты
    private lateinit var prefs: UserPreferences
    private lateinit var repo: BookingRepository
    private lateinit var logger: Logger
    private lateinit var stateManager: StateManager
    private lateinit var screenDetector: ScreenDetector

    // Действия
    private lateinit var warehouseActions: WarehouseActions
    private lateinit var processActions: ProcessActions
    private lateinit var calendarActions: CalendarActions
    private lateinit var timePickerActions: TimePickerActions
    private lateinit var filterActions: FilterActions

    // Навигация и жесты
    private lateinit var gestureHelper: GestureHelper
    private lateinit var navigationHelper: NavigationHelper

    // Мониторинг
    private lateinit var freezeDetector: FreezeDetector
    private lateinit var memoryManager: MemoryManager
    private lateinit var shiftScanner: ShiftScanner
    private lateinit var shiftMonitor: ShiftMonitor

    // Профили скорости
    @Volatile
    private var currentProfile = SpeedProfile.NORMAL

    private var lastRelaunchAttempt: Long = 0

    private var scope: CoroutineScope? = null
    val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // Popup keywords
    private val POPUP_KEYWORDS = listOf(
        "Как прошла смена", "Оцените", "Обновление", "Обновить", "Позже",
        "Пропустить", "Не сейчас", "Отмена", "Понятно",
        "Хорошо", "OK", "Ок", "Готово", "Продолжить", "Спасибо",
        "Новая версия", "Оценить", "Напомнить позже"
    )

    private val DISMISS_BUTTON_TEXTS = listOf(
        "Позже", "Пропустить", "Не сейчас", "Отмена",
        "Понятно", "OK", "Ок", "Готово", "Нет", "✕", "×", "Напомнить позже"
    )

    // ==================== LIFECYCLE ====================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)

        prefs = UserPreferences(this)
        repo = BookingRepository(AppDatabase.getDatabase(this))
        logger = Logger(filesDir)
        stateManager = StateManager()
        screenDetector = ScreenDetector()
        shiftMonitor = ShiftMonitor()

        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val displayMetrics = resources.displayMetrics
        logger.d("📱 Device: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, density=${displayMetrics.density}")

        initializeComponents()
        createNotificationChannel()
        initializeSpeedProfile()
        initTelegram()

        shiftScanner = ShiftScanner(
            prefs = prefs,
            logger = logger,
            shiftMonitor = shiftMonitor,
            scope = scope
        )

        logger.d("✅ Service connected | Profile: $currentProfile")
    }

    private fun initializeComponents() {
        gestureHelper = GestureHelper(
            service = this,
            stateManager = stateManager,
            logger = logger,
            getCurrentProfile = ::getEffectiveProfile,
            updateAutoProfile = ::updateAutoProfile
        )

        navigationHelper = NavigationHelper(
            service = this,
            stateManager = stateManager,
            logger = logger,
            gestureHelper = gestureHelper,
            getCurrentProfile = ::getEffectiveProfile
        )

        warehouseActions = WarehouseActions(
            stateManager = stateManager,
            logger = logger,
            gestureHelper = gestureHelper,
            getCurrentProfile = ::getEffectiveProfile
        )

        processActions = ProcessActions(
            prefs = prefs,
            stateManager = stateManager,
            logger = logger,
            gestureHelper = gestureHelper,
            screenDetector = screenDetector,
            getCurrentProfile = ::getEffectiveProfile,
            findOzonRoot = ::findOzonRoot
        )

        calendarActions = CalendarActions(
            prefs = prefs,
            stateManager = stateManager,
            repo = repo,
            logger = logger,
            gestureHelper = gestureHelper,
            navigationHelper = navigationHelper,
            screenDetector = screenDetector,
            findOzonRoot = ::findOzonRoot,
            scope = scope
        )

        timePickerActions = TimePickerActions(
            prefs = prefs,
            stateManager = stateManager,
            repo = repo,
            scope = scope,
            logger = logger,
            gestureHelper = gestureHelper,
            findOzonRoot = ::findOzonRoot
        )

        filterActions = FilterActions(
            service = this,
            stateManager = stateManager,
            logger = logger,
            gestureHelper = gestureHelper,
            navigationHelper = navigationHelper,
            screenDetector = screenDetector,
            findOzonRoot = ::findOzonRoot,
            getCurrentProfile = ::getEffectiveProfile
        )

        freezeDetector = FreezeDetector(
            stateManager = stateManager,
            logger = logger
        )

        memoryManager = MemoryManager(
            stateManager = stateManager,
            logger = logger,
            getCurrentProfile = ::getEffectiveProfile
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { logger.w("Service interrupted") }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        timePickerActions.destroy()
        TelegramBot.stopPollingCommands()
        TelegramBot.setCommandHandler(null)
        filterActions.destroy()
        scope?.cancel()
        scope = null
        handler.removeCallbacksAndMessages(null)
        stateManager.isRunning.set(false)
        logger.d("Service destroyed")
        System.gc()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OZBot Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OZBot automation service"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializeSpeedProfile() {
        val profileName = prefs.speedProfile
        currentProfile = try {
            SpeedProfile.valueOf(profileName)
        } catch (_: Exception) {
            SpeedProfile.NORMAL
        }
    }

    private fun getEffectiveProfile(): SpeedProfile = currentProfile

    private fun updateAutoProfile(@Suppress("UNUSED_PARAMETER") success: Boolean) {
        // AUTO profile removed intentionally. Keep method for GestureHelper callback compatibility.
    }

    fun setSpeedProfile(profile: SpeedProfile) {
        currentProfile = profile
        prefs.speedProfile = profile.name
        logger.d("⚡ Profile: $profile")
    }

    private fun initTelegram() {
        // Telegram всегда включён — токен зашит в коде
        TelegramBot.init(
            token = "",       // игнорируется — используется зашитый
            admin = "",       // игнорируется — используется зашитый
            devId = prefs.deviceId,
            devLabel = prefs.deviceLabel,
            wl = prefs.whitelist
        )

        TelegramBot.setCommandHandler(object : TelegramBot.CommandHandler {
            override fun onStartAutomation(): String {
                return if (isAutomationRunning()) "⚠️ Автоматизация уже запущена"
                else { startAutomation(); "▶️ Запускаю автоматизацию" }
            }
            override fun onStopAutomation(): String {
                return if (!isAutomationRunning()) "ℹ️ Автоматизация уже остановлена"
                else { stopAutomation(); "⏹ Останавливаю автоматизацию" }
            }
            override fun onAddDate(date: String): String {
                val current = prefs.targetDates.toMutableSet()
                if (!current.add(date)) return "ℹ️ Дата $date уже есть в поиске"
                prefs.targetDates = current.sortedByDate()
                return "✅ Добавил дату $date в поиск"
            }
            override fun onRemoveDate(date: String): String {
                val current = prefs.targetDates.toMutableSet()
                if (!current.remove(date)) return "ℹ️ Даты $date нет в списке"
                prefs.targetDates = current.sortedByDate()
                return "🗑 Удалил дату $date"
            }
            override fun onListDates(): String {
                val dates = prefs.targetDates.sortedByDate()
                return if (dates.isEmpty()) "📭 Список дат пуст"
                else "📅 Даты поиска: ${dates.joinToString(", ")}"
            }
            override fun onStatus(): String {
                val running = if (isAutomationRunning()) "🟢 работает" else "🔴 остановлен"
                val dates = prefs.targetDates.sortedByDate()
                return "🤖 Статус: $running\n🏭 Склад: ${prefs.warehouse.ifBlank { "не выбран" }}\n📋 Процесс: ${prefs.process.ifBlank { "не выбран" }}\n📅 Даты: ${if (dates.isEmpty()) "нет" else dates.joinToString(", ")}"
            }
            override fun onScreenshot(replyToChatId: String) {
                ScreenshotHelper.takeScreenshot(
                    service = this@OzonHireAutomationService,
                    onSuccess = { bytes ->
                        val ts = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(java.util.Date())
                        TelegramBot.sendPhoto(replyToChatId, bytes, "📸 ${prefs.deviceLabel} [${prefs.deviceId}] — $ts")
                    },
                    onError = { error -> TelegramBot.sendTo(replyToChatId, "❌ $error") }
                )
            }
            override fun onAddUser(targetChatId: String): String {
                val current = prefs.whitelist.toMutableSet()
                if (!current.add(targetChatId)) return "ℹ️ Пользователь $targetChatId уже в списке"
                prefs.whitelist = current
                TelegramBot.updateWhitelist(current)
                return "✅ Добавил пользователя $targetChatId"
            }
            override fun onSetLabel(newLabel: String): String {
                prefs.deviceLabel = newLabel
                // Обновляем label в TelegramBot без перезапуска
                TelegramBot.init(
                    token = "",
                    admin = "",
                    devId = prefs.deviceId,
                    devLabel = newLabel,
                    wl = prefs.whitelist
                )
                return "✅ Имя устройства изменено на: <b>$newLabel</b>\n🆔 ID: <code>${prefs.deviceId}</code>"
            }

            override fun onGetDeviceInfo(): String {
                return """
                📱 <b>Информация об устройстве</b>
                🆔 DeviceID: <code>${prefs.deviceId}</code>
                📛 Имя: <b>${prefs.deviceLabel}</b>
                🤖 Модель: ${android.os.Build.MODEL}
                📊 Android: ${android.os.Build.VERSION.RELEASE}
                """.trimIndent()
            }
            override fun onRemoveUser(targetChatId: String): String {
                val current = prefs.whitelist.toMutableSet()
                if (!current.remove(targetChatId)) return "ℹ️ Пользователя $targetChatId нет в списке"
                prefs.whitelist = current
                TelegramBot.updateWhitelist(current)
                return "🗑 Удалил пользователя $targetChatId"
            }
            override fun onListUsers(): String {
                val users = prefs.whitelist
                return if (users.isEmpty()) "📭 Whitelist пуст"
                else "👥 Пользователи (${users.size}):\n" + users.joinToString("\n") { u -> "• $u" }
            }

        })
        TelegramBot.startPollingCommands()
    }
    // ==================== POPUPS ====================

    private fun checkAndDismissPopups(root: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        if (now - stateManager.lastPopupDismissTime < 500L) {
            return false
        }

        if (!hasPopupIndicators(root)) return false

        logger.d("🔔 Popup detected, trying to dismiss...")

        if (tryDismissWithButton(root)) {
            stateManager.lastPopupDismissTime = now
            logger.d("✅ Popup dismissed with button")
            return true
        }

        if (tryDismissWithCloseIcon(root)) {
            stateManager.lastPopupDismissTime = now
            logger.d("✅ Popup dismissed with close icon")
            return true
        }

        if (tryDismissWithBack()) {
            stateManager.lastPopupDismissTime = now
            logger.d("✅ Popup dismissed with BACK")
            return true
        }

        return false
    }

    private fun hasPopupIndicators(root: AccessibilityNodeInfo): Boolean {
        val isWarehouse = screenDetector.isWarehouseScreen(root)
        val isCalendar = screenDetector.isCalendarScreen(root)
        val isTimePicker = screenDetector.isTimePickerModal(root)
        val isProcess = screenDetector.isProcessListScreen(root)
        val isFilter = screenDetector.isFilterModalOpen(root)
        val isNoSlots = screenDetector.isNoSlotsScreen(root)

        if (isWarehouse || isCalendar || isTimePicker || isProcess || isFilter || isNoSlots) {
            return false
        }

        for (keyword in POPUP_KEYWORDS) {
            if (DomUtils.hasText(root, keyword)) {
                logger.d("🔔 Popup keyword found: '$keyword'")
                return true
            }
        }

        var hasDialog = false
        NodeTreeHelper.withNodeTree(root, maxDepth = 5) { node ->
            val className = node.className?.toString() ?: ""
            if (className.contains("Dialog", ignoreCase = true) ||
                className.contains("Modal", ignoreCase = true) ||
                className.contains("Popup", ignoreCase = true) ||
                className.contains("BottomSheet", ignoreCase = true)) {
                hasDialog = true
                return@withNodeTree true
            }
            null
        }

        return hasDialog
    }

    private fun tryDismissWithButton(root: AccessibilityNodeInfo): Boolean {
        for (buttonText in DISMISS_BUTTON_TEXTS) {
            val nodes = DomUtils.findAllNodesByText(root, buttonText)
            for (node in nodes) {
                val clickable = DomUtils.findClickableParent(node) ?: node
                if (clickable.isClickable) {
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    gestureHelper.updateLastClickTime()
                    return true
                }
            }
        }
        return false
    }

    private fun tryDismissWithCloseIcon(root: AccessibilityNodeInfo): Boolean {
        val closeButtons = mutableListOf<AccessibilityNodeInfo>()

        NodeTreeHelper.withNodeTree(root, maxDepth = 10) { node ->
            val className = node.className?.toString() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            if ((className.contains("ImageButton") || className.contains("ImageView")) &&
                node.isClickable) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)

                if (rect.top < 400 && rect.right > 800) closeButtons.add(node)
                else if (desc.contains("close") || desc.contains("закрыть") ||
                    desc.contains("dismiss") || desc.contains("cancel")) closeButtons.add(node)
                // ✅ FIX: нода не добавлена в список — recycle сразу
                else try { node.recycle() } catch (_: Exception) {}
            }
            null
        }

        var clicked = false
        for (btn in closeButtons) {
            try {
                if (!clicked && btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    gestureHelper.updateLastClickTime()
                    clicked = true
                }
            } catch (_: Exception) {}
            // ✅ FIX: recycle все ноды из списка после использования
            try { btn.recycle() } catch (_: Exception) {}
        }

        return clicked
    }

    private fun tryDismissWithBack(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            gestureHelper.updateLastClickTime()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== MAIN TICKER ====================

    private val aggressiveTicker = object : Runnable {
        override fun run() {
            if (stateManager.isRunning.get()) {
                try {
                    val wasActive = tickAggressively()
                    memoryManager.maybeForceGc()

                    val interval = if (wasActive) {
                        getEffectiveProfile().tickerInterval
                    } else {
                        getEffectiveProfile().idleTickerInterval
                    }

                    handler.postDelayed(this, interval)
                } catch (e: Exception) {
                    logger.e("tickAggressively error: ${e.message}", e)
                    handler.postDelayed(this, getEffectiveProfile().idleTickerInterval)
                }
            }
        }
    }

    private fun tickAggressively(): Boolean {
        if (!stateManager.isRunning.get()) return false

        // ✅ Ждём после навигации — не дёргаем UI пока экран грузится
        if (stateManager.isWaitingAfterNav()) {
            logger.d("⏳ Waiting after navigation...")
            return false
        }

        val now = System.currentTimeMillis()
        val profile = getEffectiveProfile()

        if (now - stateManager.lastStepTime > profile.stepTimeout) {
            logger.w("⏱️ Step timeout, reset")
            stateManager.currentStep = 0
            stateManager.lastStepTime = now
        }

        var root: AccessibilityNodeInfo? = null
        try {
            root = findOzonRoot()
            if (root == null) {
                if (now - stateManager.lastClickTime > 2000 && now - lastRelaunchAttempt > 10000) {
                    lastRelaunchAttempt = now
                    logger.w("❌ Ozon lost, relaunch")
                    forceRestartApp()  // Изменено с launchOzon() на forceRestartApp()
                    gestureHelper.updateLastClickTime()
                    return true
                }
                return false
            }

            if (checkAndDismissPopups(root)) {
                logger.d("🔔 Popup closed, continuing...")
                return true
            }

            if (freezeDetector.checkForFreeze(root)) {
                forceRestartApp()
                return true
            }

            if (!freezeDetector.isUiStable(root)) {
                return false
            }

            if (stateManager.exitingCalendar.compareAndSet(true, false)) {
                logger.d("exitingCalendar cleared")
            }

            // ✅ НОВАЯ ПРОВЕРКА: Выход из вкладки "Записи"
            if (screenDetector.isOnBookingsTab(root)) {
                logger.d("🚫 [BOOKINGS TAB] Ушли в Записи, возвращаемся к складам")
                navigationHelper.clickWarehousesTab(root)
                stateManager.markNavigation()
                gestureHelper.updateLastClickTime()
                stateManager.lastStepTime = now
                return true
            }

            val isCalendarOrTime = screenDetector.isCalendarScreen(root) ||
                    screenDetector.isTimePickerModal(root)

            if (stateManager.forceGoToWarehousesOnStart &&
                !screenDetector.isWarehouseScreen(root) &&
                !isCalendarOrTime &&
                !screenDetector.isFilterModalOpen(root)
            ) {
                logger.d("🚚 [START NAV] Принудительный переход в вкладку Склады")
                navigationHelper.clickWarehousesTab(root)
                stateManager.markNavigation()
                gestureHelper.updateLastClickTime()
                stateManager.lastStepTime = now
                return true
            }

            if (!isCalendarOrTime && (now - stateManager.lastClickTime < gestureHelper.currentClickCooldownMs())) {
                return false
            }

            when {
                screenDetector.isTimePickerModal(root) -> {
                    logger.d("📋 [TIME]")
                    timePickerActions.handleTimePicker(root)
                    gestureHelper.updateLastClickTime()
                    return true
                }

                screenDetector.isCalendarScreen(root) -> {
                    logger.d("📅 [CALENDAR]")
                    val availableDates = calendarActions.handleCalendar(root)
                    if (availableDates.isNotEmpty()) {
                        shiftScanner.notifyAboutShifts(availableDates)
                    }
                    gestureHelper.updateLastClickTime()
                    stateManager.lastStepTime = now
                    return true
                }

                screenDetector.isNoSlotsScreen(root) -> {
                    logger.d("🚫 [NO SLOTS]")
                    handleNoSlotsScreen(root)
                    stateManager.lastStepTime = now
                    return true
                }

                screenDetector.isProcessListScreen(root) -> {
                    logger.d("📋 [PROCESS]")

                    // ✅ ДОБАВИТЬ СКАНИРОВАНИЕ ДОСТУПНЫХ ДАТ
                    shiftScanner.scanProcessScreen(root)

                    processActions.clickProcess(root)
                    gestureHelper.updateLastClickTime()
                    stateManager.lastStepTime = now
                    return true
                }

                screenDetector.isWarehouseScreen(root) -> {
                    logger.d("🏭 [WAREHOUSE]")
                    handleWarehouseScreen(root, now)
                    return true
                }

                else -> {
                    logger.d("🔄 [NAV] Unknown screen, checking location...")

                    if (screenDetector.isFilterModalOpen(root)) {
                        logger.d("🎛️ [FILTER] Working with filter...")
                        if (!stateManager.filterConfigured) {
                            filterActions.setupWarehouseFilter(root)
                            gestureHelper.updateLastClickTime(300L)
                        } else {
                            logger.d("Filter already configured, closing...")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            gestureHelper.updateLastClickTime()
                        }
                        stateManager.lastStepTime = now
                        return true
                    }

                    if (screenDetector.isOnHomeScreen(root)) {
                        logger.d("On home screen, going to warehouses")
                        navigationHelper.clickWarehousesTab(root)
                        stateManager.markNavigation()
                        gestureHelper.updateLastClickTime()
                        stateManager.lastStepTime = now
                        return true
                    }

                    if (screenDetector.isOnOtherTab(root)) {
                        logger.d("On other tab, going to warehouses")
                        navigationHelper.clickWarehousesTab(root)
                        stateManager.markNavigation()
                        gestureHelper.updateLastClickTime()
                        stateManager.lastStepTime = now
                        return true
                    }

                    navigationHelper.goToWarehousesSmart(root)
                    gestureHelper.updateLastClickTime()
                    stateManager.lastStepTime = now
                    return true
                }
            }
        } catch (e: Exception) {
            logger.e("tick error: ${e.message}")
            return false
        } finally {
            NodeTreeHelper.safeRecycle(root)
        }
    }

    private fun handleNoSlotsScreen(root: AccessibilityNodeInfo) {
        logger.d("🚫 Нет мест для выбранного процесса")
        navigationHelper.goToWarehousesSmart(root)
        gestureHelper.updateLastClickTime()
    }

    private fun handleWarehouseScreen(root: AccessibilityNodeInfo, now: Long) {
        val profile = getEffectiveProfile()
        stateManager.forceGoToWarehousesOnStart = false

        // Если уже кликнули и ждём перехода — не делаем ничего
        if (stateManager.waitingForWarehouseLoad.get()) {
            logger.d("⏳ Waiting for process screen to load...")
            return
        }

        // Ждём после навигации
        if (stateManager.isWaitingAfterNav()) {
            logger.d("⏳ Waiting after navigation...")
            return
        }

        // Фильтр открыт — обрабатываем его
        if (screenDetector.isFilterModalOpen(root)) {
            logger.d("🎛️ [FILTER in WAREHOUSE] Working with filter modal...")
            if (!stateManager.filterConfigured) {
                filterActions.setupWarehouseFilter(root)
                gestureHelper.updateLastClickTime(300L)
            } else {
                logger.d("Filter already configured but still open, closing...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                gestureHelper.updateLastClickTime()
            }
            stateManager.lastStepTime = now
            return
        }

        // Склад ещё не загружен
        if (!screenDetector.isWarehouseLoaded(root)) {
            if (now - stateManager.lastStepTime < profile.loadWait) return
            logger.w("Warehouse load timeout")
            navigationHelper.goToWarehousesSmart(root)
            gestureHelper.updateLastClickTime()
            stateManager.lastStepTime = now
            return
        }

        // Фильтр не настроен — настраиваем
        if (!stateManager.filterConfigured) {
            logger.d("🔧 Configuring warehouse filter...")
            filterActions.setupWarehouseFilter(root)
            gestureHelper.updateLastClickTime(300L)
            stateManager.lastStepTime = now
            return
        }

        // Всё готово — кликаем по складу
        warehouseActions.clickWarehouse(root)
        stateManager.lastStepTime = now
    }    // ==================== START/STOP ====================

    fun startAutomation() {
        if (stateManager.isRunning.get()) {
            logger.w("Automation already running")
            return
        }

        stateManager.reset()
        stateManager.resetForStart()
        warehouseActions.reset()
        initializeSpeedProfile()
        initTelegram()

        stateManager.filterConfigured = false
        stateManager.forceGoToWarehousesOnStart = true

        val effective = getEffectiveProfile()
        logger.d("🚀 START | Profile: $currentProfile | Effective: $effective")

        stateManager.isRunning.set(true)

        launchOzon()

        waitForOzonAndGoToWarehouses()
    }

    private fun waitForOzonAndGoToWarehouses() {
        val startTime = System.currentTimeMillis()
        val maxWaitTime = 12_000L
        val checkInterval = 700L

        val checker = object : Runnable {
            override fun run() {
                if (!stateManager.isRunning.get()) return

                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > maxWaitTime) {
                    logger.w("⏱️ Timeout waiting for Ozon to load, starting anyway")
                    startTicker()
                    return
                }

                var root: AccessibilityNodeInfo? = null
                try {
                    root = findOzonRoot()

                    if (root == null) {
                        logger.d("Waiting for Ozon root... ${elapsed}ms")
                        handler.postDelayed(this, checkInterval)
                        return
                    }

                    if (checkAndDismissPopups(root)) {
                        logger.d("Dismissed popup, retrying...")
                        handler.postDelayed(this, 500L)
                        NodeTreeHelper.safeRecycle(root)
                        return
                    }

                    if (screenDetector.isOnBookingsTab(root)) {
                        logger.d("🚫 On bookings tab at start, going to warehouses")
                        navigationHelper.clickWarehousesTab(root)
                        stateManager.markNavigation()
                        NodeTreeHelper.safeRecycle(root)
                        handler.postDelayed(this, 800L)
                        return
                    }

                    if (!screenDetector.isOzonAppLoaded(root, navigationHelper::findWarehouseNodeAnywhere)) {
                        logger.d("Waiting for Ozon UI to load... ${elapsed}ms")
                        handler.postDelayed(this, checkInterval)
                        NodeTreeHelper.safeRecycle(root)
                        return
                    }

                    logger.d("✅ Ozon loaded, going to warehouse tab")

                    if (screenDetector.isWarehouseScreen(root)) {
                        logger.d("Already on warehouse screen")
                        stateManager.forceGoToWarehousesOnStart = false
                        NodeTreeHelper.safeRecycle(root)
                        startTicker()
                        return
                    }

                    navigationHelper.clickWarehousesTab(root)
                    stateManager.markNavigation()
                    NodeTreeHelper.safeRecycle(root)
                    handler.postDelayed(this, 800L)

                } catch (e: Exception) {
                    logger.e("Error in waitForOzonAndGoToWarehouses: ${e.message}", e)
                    handler.postDelayed(this, checkInterval)
                    NodeTreeHelper.safeRecycle(root)
                }
            }
        }

        handler.postDelayed(checker, 2000L)
    }


    private fun startTicker() {
        if (!stateManager.isRunning.get()) return
        logger.d("🎬 Starting main ticker")
        handler.post(aggressiveTicker)
    }

    fun stopAutomation() {
        logger.d("🛑 STOP")
        stateManager.isRunning.set(false)
        handler.removeCallbacksAndMessages(null)
        stateManager.reset()
        System.gc()
    }

    // ==================== APP MANAGEMENT ====================

    private fun findOzonRoot(): AccessibilityNodeInfo? {
        try {
            for (window in windows) {
                val root = window.root ?: continue
                if (root.packageName == TARGET_PACKAGE) return root
            }
        } catch (_: Exception) {}

        val activeRoot = rootInActiveWindow
        if (activeRoot?.packageName == TARGET_PACKAGE) return activeRoot

        return null
    }

    private fun launchOzon() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
                ?: Intent().setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.MainActivity")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            logger.d("🚀 Launch Ozon")
        } catch (e: Exception) {
            logger.e("Launch fail: ${e.message}")
        }
    }

    private fun forceRestartApp() {
        logger.d("🔄 Force restarting Ozon app...")
        stateManager.filterConfigured = false

        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({ forceStopAndRelaunch() }, 1000L)
        } catch (e: Exception) {
            logger.e("forceRestartApp error: ${e.message}")
            handler.postDelayed({ launchOzon() }, 500L)
        }
    }

    private fun forceStopAndRelaunch() {
        try {
            stateManager.filterConfigured = false
            try {
                Runtime.getRuntime().exec("am force-stop $TARGET_PACKAGE")
            } catch (_: Exception) {}

            handler.postDelayed({
                launchOzon()
                stateManager.lastUiChangeTime = System.currentTimeMillis()
                stateManager.freezeDetectedHash = 0
                stateManager.lastDomHash = 0
                stateManager.domStableCount = 0

                handler.postDelayed({
                    val root = findOzonRoot()
                    if (root != null) {
                        navigationHelper.clickWarehousesTab(root)
                        stateManager.markNavigation()
                        NodeTreeHelper.safeRecycle(root)
                    }
                }, 2000L)
            }, 1500L)
        } catch (e: Exception) {
            logger.e("forceStopAndRelaunch error: ${e.message}")
            launchOzon()
        }
    }

    private fun Collection<String>.sortedByDate(): List<String> {
        return this.sortedBy {
            val parts = it.split(".")
            val day = parts.getOrNull(0)?.toIntOrNull() ?: 99
            val month = parts.getOrNull(1)?.toIntOrNull() ?: 99
            month * 100 + day
        }
    }

    // ==================== PUBLIC API ====================

    fun isAutomationRunning(): Boolean = stateManager.isRunning.get()
    fun getLogFilePath(): String? = logger.getLogFilePath()
    fun setLoggingEnabled(enabled: Boolean) { logger.enabled = enabled }
    fun getCurrentEffectiveProfile(): SpeedProfile = getEffectiveProfile()
    fun getRestartCount(): Int = stateManager.restartCount.get()
}