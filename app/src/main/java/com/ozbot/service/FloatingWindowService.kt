package com.ozbot.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.ozbot.R
import com.ozbot.automation.OzonHireAutomationService
import com.ozbot.automation.config.TimeSlot
import com.ozbot.automation.utils.SpeedProfile
import com.ozbot.data.UserPreferences
import com.ozbot.core.BotController
import com.ozbot.telegram.TelegramBot
import java.text.SimpleDateFormat
import java.util.*

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var calendarView: View? = null
    private var telegramSettingsView: View? = null
    private var telegramSettingsParams: WindowManager.LayoutParams? = null
    private lateinit var userPreferences: UserPreferences

    private var mainLayout: LinearLayout? = null
    private var processSpinner: Spinner? = null
    private var timeSpinner: Spinner? = null
    private var speedSpinner: Spinner? = null
    private var datesButton: Button? = null
    private var startButton: Button? = null
    private var closeButton: Button? = null
    private var telegramButton: ImageButton? = null

    private var runningLayout: LinearLayout? = null
    private var statusText: TextView? = null
    private var pauseButton: Button? = null
    private var stopButton: Button? = null

    private var tvMonthTitle: TextView? = null
    private var gridDays: GridLayout? = null
    private var btnPrev: Button? = null
    private var btnNext: Button? = null
    private var btnOk: Button? = null
    private var btnCancel: Button? = null

    // Telegram для друзей
    private var friendsEnableCheck: CheckBox? = null

    private var friendsTokenInput: EditText? = null

    private var friendsChatInput: EditText? = null

    private val selectedDates = mutableSetOf<Long>()
    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    private val processes = listOf(
        "Инвентаризация",
        "Приемка",
        "Размещение",
        "Сортировка непрофиль",
        "Производство непрофиль"
    )

    private val timeSlots = listOf(
        "08:00–20:00",
        "20:00–08:00",
        "12:00–20:00",
        "10:00–22:00",
        "09:00–21:00",
        "20:00–05:00"
    )

    private val speedOptions = listOf(
        "Медленно" to SpeedProfile.SLOW,
        "Нормально" to SpeedProfile.NORMAL,
        "Быстро" to SpeedProfile.FAST
    )

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()

        userPreferences = UserPreferences(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH)

        createFloatingWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)

        mainLayout = floatingView?.findViewById(R.id.mainLayout)
        runningLayout = floatingView?.findViewById(R.id.runningLayout)

        processSpinner = floatingView?.findViewById(R.id.processSpinner)
        timeSpinner = floatingView?.findViewById(R.id.timeSpinner)
        speedSpinner = floatingView?.findViewById(R.id.speedSpinner)
        datesButton = floatingView?.findViewById(R.id.datesButton)
        startButton = floatingView?.findViewById(R.id.startButton)
        closeButton = floatingView?.findViewById(R.id.closeButton)

        statusText = floatingView?.findViewById(R.id.statusText)
        pauseButton = floatingView?.findViewById(R.id.pauseButton)
        stopButton = floatingView?.findViewById(R.id.stopButton)

        setupSpinners()
        setupButtons()
        loadSavedSettings()

        showMainWindow()

        val params = createWindowParams(focusable = false)
        windowManager.addView(floatingView, params)

        setupDragging(params)
    }

    private fun createWindowParams(focusable: Boolean): WindowManager.LayoutParams {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }
    }

    private fun setupSpinners() {
        val processAdapter = ArrayAdapter(this, R.layout.spinner_item, processes)
        processAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        processSpinner?.adapter = processAdapter

        val timeAdapter = ArrayAdapter(this, R.layout.spinner_item, timeSlots)
        timeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        timeSpinner?.adapter = timeAdapter

        val speedLabels = speedOptions.map { it.first }
        val speedAdapter = ArrayAdapter(this, R.layout.spinner_item, speedLabels)
        speedAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        speedSpinner?.adapter = speedAdapter

        speedSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = speedOptions.getOrNull(position) ?: return
                val selectedLabel = selected.first
                val selectedProfile = selected.second
                userPreferences.speedProfile = selectedProfile.name

                val service = OzonHireAutomationService.getInstance()
                if (service?.isAutomationRunning() == true) {
                    try {
                        service.setSpeedProfile(selectedProfile)
                        Toast.makeText(this@FloatingWindowService, "Скорость: $selectedLabel", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("FloatingWindow", "Error setting speed: ${e.message}")
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        datesButton?.setOnClickListener {
            showCalendarWindow()
        }

        startButton?.setOnClickListener {
            startAutomation()
        }

        closeButton?.setOnClickListener {
            stopSelf()
        }

        telegramButton?.setOnClickListener {
            showTelegramSettings()
        }

        pauseButton?.setOnClickListener {
            Toast.makeText(this, "Пауза (в разработке)", Toast.LENGTH_SHORT).show()
        }

        stopButton?.setOnClickListener {
            stopAutomation()
        }
    }

    // ==================== TELEGRAM SETTINGS ====================

    private var tgEnableCheck: CheckBox? = null
    private var tgTokenInput: EditText? = null
    private var tgChatInput: EditText? = null
    private var tgIntervalInput: EditText? = null

    @SuppressLint("ClickableViewAccessibility")


    private fun showTelegramSettings() {
        floatingView?.visibility = View.GONE

        if (telegramSettingsView == null) {
            telegramSettingsView = createTelegramSettingsView()
            telegramSettingsParams = createWindowParams(focusable = true)
            windowManager.addView(telegramSettingsView, telegramSettingsParams)
            setupTelegramDragging()
        } else {
            telegramSettingsView?.visibility = View.VISIBLE
            telegramSettingsParams?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            windowManager.updateViewLayout(telegramSettingsView, telegramSettingsParams)
        }

        updateTelegramSettingsUI()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTelegramDragging() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        telegramSettingsView?.setOnTouchListener { v, event ->
            if (isTouchInsideEditText(event.rawX, event.rawY)) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = telegramSettingsParams?.x ?: 0
                    initialY = telegramSettingsParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        telegramSettingsParams?.x = initialX + dx.toInt()
                        telegramSettingsParams?.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(telegramSettingsView, telegramSettingsParams)
                    }
                    isDragging
                }
                else -> false
            }
        }
    }

    private fun isTouchInsideEditText(rawX: Float, rawY: Float): Boolean {
        val editTexts = listOf(tgTokenInput, tgChatInput, tgIntervalInput)
        for (editText in editTexts) {
            editText?.let {
                val location = IntArray(2)
                it.getLocationOnScreen(location)
                val x = location[0]
                val y = location[1]
                if (rawX >= x && rawX <= x + it.width && rawY >= y && rawY <= y + it.height) {
                    return true
                }
            }
        }
        return false
    }

    private fun createTelegramSettingsView(): View {
        // Внешний контейнер со ScrollView
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(500)) // ✅ УВЕЛИЧИЛ ВЫСОТУ для друзей
            setBackgroundResource(R.drawable.floating_bg)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // ==================== ЛИЧНЫЕ УВЕДОМЛЕНИЯ ====================

        val titlePersonal = TextView(this).apply {
            text = "⚙️ Личные уведомления"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, dp(8))
        }
        layout.addView(titlePersonal)

        tgEnableCheck = CheckBox(this).apply {
            text = "Включить уведомления"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = userPreferences.telegramEnabled
        }
        layout.addView(tgEnableCheck)

        addCompactField(layout, "Bot Token:", "123:ABC...", userPreferences.telegramBotToken) {
            tgTokenInput = it
        }

        addCompactField(layout, "Chat ID:", "-1001234567890", userPreferences.telegramChatId, isNumber = false) { // ✅ isNumber = false
            tgChatInput = it
        }

        addCompactField(layout, "Интервал (мин):", "30", userPreferences.telegramReportIntervalMin.toString(), isNumber = true) {
            tgIntervalInput = it
        }

        // ==================== УВЕДОМЛЕНИЯ ДЛЯ ДРУЗЕЙ ====================

        // Разделитель
        val divider = View(this).apply {
            setBackgroundColor(0xFF444444.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        }
        layout.addView(divider)

        val titleFriends = TextView(this).apply {
            text = "👥 Уведомления друзьям"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, dp(8))
        }
        layout.addView(titleFriends)

        val friendsDescription = TextView(this).apply {
            text = "Бот будет отправлять уведомления о свободных сменах в другой чат"
            textSize = 10f
            setTextColor(0xFF999999.toInt())
            setPadding(0, 0, 0, dp(6))
        }
        layout.addView(friendsDescription)

        friendsEnableCheck = CheckBox(this).apply {
            text = "Вкл. уведомления друзьям"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = userPreferences.friendsNotifyEnabled
        }
        layout.addView(friendsEnableCheck)

        addCompactField(layout, "Bot Token:", "123:ABC...", userPreferences.friendsTelegramBotToken) {
            friendsTokenInput = it
        }

        addCompactField(layout, "Chat ID:", "-1001234567890", userPreferences.friendsTelegramChatId, isNumber = false) { // ✅ isNumber = false
            friendsChatInput = it
        }

        // ==================== КНОПКИ ====================

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        val testButton = Button(this).apply {
            text = "ТЕСТ"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.button_secondary_bg)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                marginEnd = dp(4)
            }
            setOnClickListener { testTelegramConnection() }
        }
        buttonsRow.addView(testButton)

        val testFriendsBtn = Button(this).apply {
            text = "ТЕСТ👥"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.button_secondary_bg)
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener { testFriendsConnection() }
        }
        buttonsRow.addView(testFriendsBtn)

        val saveButton = Button(this).apply {
            text = "СОХР."
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.button_start_bg)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                marginStart = dp(4)
            }
            setOnClickListener { saveTelegramSettings() }
        }
        buttonsRow.addView(saveButton)

        layout.addView(buttonsRow)

        val cancelButton = Button(this).apply {
            text = "ОТМЕНА"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.button_close_bg)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36)
            ).apply {
                topMargin = dp(4)
            }
            setOnClickListener { hideTelegramSettings() }
        }
        layout.addView(cancelButton)

        scrollView.addView(layout)
        return scrollView
    }
    // ✅ ВСПОМОГАТЕЛЬНЫЙ МЕТОД для полей
    private fun addCompactField(
        parent: LinearLayout,
        label: String,
        hint: String,
        value: String,
        isNumber: Boolean = false,
        callback: (EditText) -> Unit
    ) {
        val labelView = TextView(this).apply {
            text = label
            setTextColor(0xFF999999.toInt())
            textSize = 11f
            setPadding(0, dp(6), 0, dp(3))
        }
        parent.addView(labelView)

        val input = EditText(this).apply {
            this.hint = hint
            setText(value)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundResource(R.drawable.edittext_bg)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            textSize = 12f
            isSingleLine = true
            maxLines = 1
            isFocusable = true
            isFocusableInTouchMode = true

            // ✅ ИСПРАВЛЕНО: разрешаем минус для Chat ID
            inputType = if (isNumber) {
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED  // ✅ РАЗРЕШАЕТ МИНУС
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
        }

        parent.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(34)
        ))

        callback(input)
    }

    private fun updateTelegramSettingsUI() {
        // Личные
        tgEnableCheck?.isChecked = userPreferences.telegramEnabled
        tgTokenInput?.setText(userPreferences.telegramBotToken)
        tgChatInput?.setText(userPreferences.telegramChatId)
        tgIntervalInput?.setText(userPreferences.telegramReportIntervalMin.toString())

        // Друзья
        friendsEnableCheck?.isChecked = userPreferences.friendsNotifyEnabled
        friendsTokenInput?.setText(userPreferences.friendsTelegramBotToken)
        friendsChatInput?.setText(userPreferences.friendsTelegramChatId)
    }

    private fun testTelegramConnection() {
        val token = tgTokenInput?.text?.toString() ?: ""
        val chatId = tgChatInput?.text?.toString() ?: ""

        if (token.isBlank() || chatId.isBlank()) {
            Toast.makeText(this, "Заполните Token и Chat ID", Toast.LENGTH_SHORT).show()
            return
        }

        TelegramBot.init(
            token = token,
            admin = chatId,
            devId = userPreferences.deviceId,
            devLabel = userPreferences.deviceLabel,
            wl = userPreferences.whitelist
        )
        TelegramBot.sendTestMessage()
        Toast.makeText(this, "✅ Тест отправлен! Проверьте Telegram", Toast.LENGTH_SHORT).show()
    }

    private fun saveTelegramSettings() {
        // Личные уведомления
        val enabled = tgEnableCheck?.isChecked ?: false
        val token = tgTokenInput?.text?.toString() ?: ""
        val chatId = tgChatInput?.text?.toString() ?: ""
        val interval = tgIntervalInput?.text?.toString()?.toIntOrNull() ?: 30

        userPreferences.telegramEnabled = enabled
        userPreferences.telegramBotToken = token
        userPreferences.telegramChatId = chatId
        userPreferences.telegramReportIntervalMin = interval

        if (enabled && token.isNotBlank() && chatId.isNotBlank()) {
            TelegramBot.init(
                token = token,
                admin = chatId,
                devId = userPreferences.deviceId,
                devLabel = userPreferences.deviceLabel,
                wl = userPreferences.whitelist
            )
        }

        // Уведомления друзьям
        val friendsEnabled = friendsEnableCheck?.isChecked ?: false
        val friendsToken = friendsTokenInput?.text?.toString() ?: ""
        val friendsChatId = friendsChatInput?.text?.toString() ?: ""

        userPreferences.friendsNotifyEnabled = friendsEnabled
        userPreferences.friendsTelegramBotToken = friendsToken
        userPreferences.friendsTelegramChatId = friendsChatId

        Toast.makeText(this, "✅ Настройки сохранены", Toast.LENGTH_SHORT).show()
        hideTelegramSettings()
    }

    private fun testFriendsConnection() {
        val token = friendsTokenInput?.text?.toString()?.trim() ?: ""
        val chatId = friendsChatInput?.text?.toString()?.trim() ?: ""

        android.util.Log.d("FloatingWindow", "🧪 Testing friends connection...")
        android.util.Log.d("FloatingWindow", "   Token: ${if (token.isBlank()) "EMPTY" else "OK (${token.length} chars)"}")
        android.util.Log.d("FloatingWindow", "   Chat ID: $chatId")

        if (token.isBlank() || chatId.isBlank()) {
            Toast.makeText(this, "⚠️ Заполните данные для друзей", Toast.LENGTH_SHORT).show()
            return
        }

        TelegramBot.sendToFriendsChat(token, chatId, "✅ Тест уведомлений для друзей!\n\nБот подключен!")

        Toast.makeText(this, "✅ Тест отправлен! Проверьте канал", Toast.LENGTH_LONG).show()
    }

    private fun hideTelegramSettings() {
        telegramSettingsView?.visibility = View.GONE
        floatingView?.visibility = View.VISIBLE
    }

    // ==================== CALENDAR ====================

    @SuppressLint("ClickableViewAccessibility")
    private fun showCalendarWindow() {
        floatingView?.visibility = View.GONE

        if (calendarView == null) {
            val inflater = LayoutInflater.from(this)
            calendarView = inflater.inflate(R.layout.dialog_calendar, null)

            tvMonthTitle = calendarView?.findViewById(R.id.tvMonthTitle)
            gridDays = calendarView?.findViewById(R.id.gridDays)
            btnPrev = calendarView?.findViewById(R.id.btnPrev)
            btnNext = calendarView?.findViewById(R.id.btnNext)
            btnOk = calendarView?.findViewById(R.id.btnOk)
            btnCancel = calendarView?.findViewById(R.id.btnCancel)

            btnPrev?.setOnClickListener {
                currentMonth--
                if (currentMonth < 0) {
                    currentMonth = 11
                    currentYear--
                }
                renderCalendar()
            }

            btnNext?.setOnClickListener {
                currentMonth++
                if (currentMonth > 11) {
                    currentMonth = 0
                    currentYear++
                }
                renderCalendar()
            }

            btnCancel?.setOnClickListener {
                hideCalendarWindow()
            }

            btnOk?.setOnClickListener {
                updateDatesButtonText()
                saveSettings()
                hideCalendarWindow()
            }

            val params = createWindowParams(focusable = false)
            windowManager.addView(calendarView, params)
            setupDragging(params, calendarView!!)
        } else {
            calendarView?.visibility = View.VISIBLE
        }

        renderCalendar()
    }

    private fun hideCalendarWindow() {
        calendarView?.visibility = View.GONE
        floatingView?.visibility = View.VISIBLE
    }

    private fun isPastDate(
        day: Int, month: Int, year: Int,
        todayDay: Int, todayMonth: Int, todayYear: Int
    ): Boolean {
        // month: 0..11 (как в Calendar)
        if (year < todayYear) return true
        if (year > todayYear) return false

        if (month < todayMonth) return true
        if (month > todayMonth) return false

        return day < todayDay
    }

    private fun renderCalendar() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, currentYear)
        cal.set(Calendar.MONTH, currentMonth)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val titleFmt = SimpleDateFormat("LLLL", Locale("ru"))
        tvMonthTitle?.text = titleFmt.format(cal.time).replaceFirstChar { it.uppercase() }

        gridDays?.removeAllViews()
        gridDays?.columnCount = 7

        val weekDays = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        for (day in weekDays) {
            val tv = TextView(this)
            tv.text = day
            tv.gravity = android.view.Gravity.CENTER
            tv.setTextColor(0xFF999999.toInt())
            tv.textSize = 12f
            tv.setPadding(4, 8, 4, 8)
            gridDays?.addView(tv, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
        for (i in 0 until offset) {
            val space = android.widget.Space(this)
            gridDays?.addView(space, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }

        val today = Calendar.getInstance()
        val todayDay = today.get(Calendar.DAY_OF_MONTH)
        val todayMonth = today.get(Calendar.MONTH) // 0-11
        val todayYear = today.get(Calendar.YEAR)

        for (day in 1..daysInMonth) {
            val btn = ToggleButton(this)
            btn.textOn = day.toString()
            btn.textOff = day.toString()
            btn.text = day.toString()
            btn.textSize = 12f

            val calendar = Calendar.getInstance()
            calendar.set(currentYear, currentMonth, day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val dateMillis = calendar.timeInMillis

            // Проверки дат
            val isPast = isPastDate(day, currentMonth, currentYear, todayDay, todayMonth, todayYear)
            val isToday = (day == todayDay && currentMonth == todayMonth && currentYear == todayYear)

            // Стилизация: более сдержанные цвета
            when {
                isPast -> {
                    // Прошедшая дата: тёмно-серый фон, светло-серый текст, некликабельна
                    btn.setTextColor(0xFF9E9E9E.toInt()) // #9E9E9E
                    btn.isEnabled = false
                    btn.isClickable = false
                    btn.alpha = 0.55f
                    btn.setBackgroundResource(R.drawable.calendar_day_disabled)
                }
                isToday -> {
                    // Сегодня: аккуратная рамка и ненавязчивый фон, белый текст
                    btn.setTextColor(0xFFFFFFFF.toInt()) // белый
                    btn.setBackgroundResource(R.drawable.calendar_day_today_subtle)
                    btn.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                else -> {
                    // Будущая дата: обычный стиль
                    btn.setTextColor(0xFFFFFFFF.toInt())
                    btn.setBackgroundResource(R.drawable.calendar_day_selector)
                }
            }

            btn.setPadding(0, 0, 0, 0)

            btn.isChecked = selectedDates.contains(dateMillis)

            // Только для сегодняшней и будущих дат слушатель
            if (!isPast) {
                btn.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedDates.add(dateMillis) else selectedDates.remove(dateMillis)
                }
            }

            gridDays?.addView(btn, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(36)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    // ==================== UI HELPERS ====================

    private fun showMainWindow() {
        mainLayout?.visibility = View.VISIBLE
        runningLayout?.visibility = View.GONE
        floatingView?.visibility = View.VISIBLE
    }

    private fun showRunningWindow() {
        mainLayout?.visibility = View.GONE
        runningLayout?.visibility = View.VISIBLE
        updateStatusText()
        floatingView?.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun updateDatesButtonText() {
        val count = selectedDates.size
        datesButton?.text = "Выбрано: $count дн."
    }

    private fun updateStatusText() {
        val process = processSpinner?.selectedItem?.toString() ?: ""
        val dates = selectedDates.sorted().joinToString(",") {
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(it))
        }
        val time = timeSpinner?.selectedItem?.toString() ?: ""

        statusText?.text = "$process | $dates | $time"
    }

    // ==================== SETTINGS ====================

    private fun loadSavedSettings() {
        val savedProcess = userPreferences.process
        if (savedProcess.isNotBlank()) {
            val index = processes.indexOf(savedProcess)
            if (index >= 0) {
                processSpinner?.setSelection(index)
            }
        }


        val savedDates = userPreferences.targetDates
        selectedDates.clear()
        savedDates.forEach { dateStr ->
            try {
                val parts = dateStr.split(".")
                if (parts.size >= 2) {
                    val day = parts[0].toInt()
                    val month = parts[1].toInt() - 1
                    val year = if (parts.size == 3) parts[2].toInt() else Calendar.getInstance().get(Calendar.YEAR)

                    val calendar = Calendar.getInstance()
                    calendar.set(year, month, day, 0, 0, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    selectedDates.add(calendar.timeInMillis)
                }
            } catch (e: Exception) {}
        }
        updateDatesButtonText()

        val savedSlots = userPreferences.timeSlots
        if (savedSlots.isNotEmpty()) {
            val firstSlot = savedSlots[0].toDisplayString()
            val index = timeSlots.indexOf(firstSlot)
            if (index >= 0) {
                timeSpinner?.setSelection(index)
            }
        }

        val savedSpeed = userPreferences.speedProfile
        val speedIndex = speedOptions.indexOfFirst { it.second.name == savedSpeed }
        if (speedIndex >= 0) {
            speedSpinner?.setSelection(speedIndex)
        } else {
            speedSpinner?.setSelection(speedOptions.indexOfFirst { it.second == SpeedProfile.NORMAL }.coerceAtLeast(0))
        }
    }

    private fun saveSettings() {
        val process = processSpinner?.selectedItem?.toString() ?: ""
        val timeSlot = timeSpinner?.selectedItem?.toString() ?: ""
        val selectedSpeedIndex = speedSpinner?.selectedItemPosition ?: -1
        val speed = speedOptions.getOrNull(selectedSpeedIndex)?.second?.name ?: SpeedProfile.NORMAL.name

        val dates = selectedDates.sorted().map { millis ->
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))
        }

        val parsedSlots = listOf(TimeSlot.parse(timeSlot)).filterNotNull()

        userPreferences.warehouse = "Склад 1"
        userPreferences.process = process
        userPreferences.targetDates = dates
        userPreferences.timeSlots = parsedSlots
        userPreferences.speedProfile = speed

        userPreferences.isConfigured = true

        Log.d("FloatingWindow", "Settings saved: process=$process, dates=$dates, speed=$speed")
    }

    // ==================== AUTOMATION ====================

    private fun startAutomation() {
        if (selectedDates.isEmpty()) {
            Toast.makeText(this, "Выберите даты", Toast.LENGTH_SHORT).show()
            return
        }

        saveSettings()

        val service = OzonHireAutomationService.getInstance()
        if (service == null) {
            Toast.makeText(this, "Accessibility Service не активен.\nВключите в настройках", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {}
            return
        }

        try {
            BotController.start()
            showRunningWindow()
            Toast.makeText(this, "Автоматизация запущена", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("FloatingWindow", "Error starting automation", e)
        }
    }

    private fun stopAutomation() {
        BotController.stop()
        showMainWindow()
        Toast.makeText(this, "Автоматизация остановлена", Toast.LENGTH_SHORT).show()
    }

    // ==================== DRAGGING ====================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragging(params: WindowManager.LayoutParams, view: View = floatingView!!) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        calendarView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        telegramSettingsView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
    }

    companion object {
        private var instance: FloatingWindowService? = null
        fun getInstance(): FloatingWindowService? = instance
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        return START_STICKY
    }
}