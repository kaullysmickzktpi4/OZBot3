package com.ozbot.automation.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StateManager {

    val isRunning = AtomicBoolean(false)
    val exitingCalendar = AtomicBoolean(false)
    val goingToWarehouses = AtomicBoolean(false)
    val waitingForWarehouseLoad = AtomicBoolean(false)
    val monthClicked = AtomicBoolean(false)
    val waitingAfterNavigation = AtomicBoolean(false) // ← НОВЫЙ ФЛАГ

    @Volatile var lastMonthText: String? = null
    @Volatile var pendingMonthTarget: Int? = null
    @Volatile var currentStep = 0
    @Volatile var lastStepTime = 0L
    @Volatile var lastClickTime = 0L
    @Volatile var lastPopupDismissTime = 0L
    @Volatile var filterConfigured = false
    @Volatile var automationStartTime = 0L
    @Volatile var forceGoToWarehousesOnStart = false
    @Volatile var lastSelectedBookingDate: String? = null
    @Volatile var lastNavigationTime = 0L // ← НОВЫЙ — время последней навигации

    val consecSuccessGestures = AtomicInteger(0)
    val consecCancelledGestures = AtomicInteger(0)
    @Volatile var lastProfileChangeTime = 0L
    @Volatile var autoCurrentLevel = 2

    @Volatile var lastDomHash: Int = 0
    @Volatile var domStableCount = 0
    @Volatile var lastUiChangeTime = 0L
    @Volatile var lastFreezeCheckTime = 0L
    @Volatile var freezeDetectedHash = 0
    val restartCount = AtomicInteger(0)

    val lastGcTime = AtomicLong(0L)
    val tickCount = AtomicLong(0L)
    @Volatile var lastTelegramReportTime = 0L
    @Volatile var lastRamWarningTime = 0L

    val POPUP_DISMISS_COOLDOWN = 500L
    val PROFILE_CHANGE_MIN_INTERVAL = 8_000L
    val SUCCESS_TO_SPEEDUP = 8
    val FAILS_TO_SLOWDOWN = 3

    // Минимальное время ожидания после навигации (мс)
    val NAV_WAIT_MS = 800L

    fun reset() {
        exitingCalendar.set(false)
        goingToWarehouses.set(false)
        waitingForWarehouseLoad.set(false)
        waitingAfterNavigation.set(false) // ← сбрасываем
        monthClicked.set(false)
        pendingMonthTarget = null
        lastMonthText = null
        consecSuccessGestures.set(0)
        consecCancelledGestures.set(0)
        lastDomHash = 0
        domStableCount = 0
        lastPopupDismissTime = 0L
        filterConfigured = false
        forceGoToWarehousesOnStart = false
        lastSelectedBookingDate = null
        lastNavigationTime = 0L
    }

    fun resetForStart() {
        currentStep = 0
        lastStepTime = System.currentTimeMillis()
        lastGcTime.set(System.currentTimeMillis())
        tickCount.set(0)
        automationStartTime = System.currentTimeMillis()
        lastTelegramReportTime = System.currentTimeMillis()
        lastUiChangeTime = System.currentTimeMillis()
        freezeDetectedHash = 0
        restartCount.set(0)
    }

    // Проверка — нужно ли ждать после навигации
    fun isWaitingAfterNav(): Boolean {
        if (lastNavigationTime == 0L) return false
        return System.currentTimeMillis() - lastNavigationTime < NAV_WAIT_MS
    }

    // Вызывай после каждого клика на таб/навигацию
    fun markNavigation() {
        lastNavigationTime = System.currentTimeMillis()
    }
}