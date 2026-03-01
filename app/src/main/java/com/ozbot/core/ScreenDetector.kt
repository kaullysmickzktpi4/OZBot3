package com.ozbot.automation.core

import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.bot.DomUtils

class ScreenDetector {

    // ==================== НАВИГАЦИОННЫЕ ТАБЫ ====================

    fun isWarehouseTabSelected(root: AccessibilityNodeInfo): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId("ru.ozon.hire:id/warehouseTab")
        return nodes?.any { it.isSelected } == true
    }

    fun isCalendarTabSelected(root: AccessibilityNodeInfo): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId("ru.ozon.hire:id/calendarTab")
        return nodes?.any { it.isSelected } == true
    }

    // ==================== ГЛАВНЫЙ ЭКРАН ====================

    /**
     * Мы на вкладке Записи и это реально экран Записей —
     * НЕ экран процессов, НЕ склад, НЕ календарь, НЕ таймпикер
     */
    fun isOnBookingsTab(root: AccessibilityNodeInfo): Boolean {
        if (!isCalendarTabSelected(root)) return false
        if (isWarehouseScreen(root)) return false
        if (isProcessListScreen(root)) return false
        if (isCalendarScreen(root)) return false
        if (isTimePickerModal(root)) return false
        if (isFilterModalOpen(root)) return false
        return true
    }

    // ==================== ЭКРАН СКЛАДОВ ====================

    fun isWarehouseScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Выберите склад")
    }

    fun isWarehouseLoaded(root: AccessibilityNodeInfo): Boolean {
        if (!isWarehouseScreen(root)) return false
        val hasRecordButton = DomUtils.hasText(root, "Записаться")
        val isLoading = DomUtils.hasText(root, "Загрузка") ||
                DomUtils.hasText(root, "Подождите")
        return hasRecordButton && !isLoading
    }

    fun isFilterModalOpen(root: AccessibilityNodeInfo): Boolean {
        val hasFiltersTitle = DomUtils.hasText(root, "Фильтры")
        val hasFavorites = DomUtils.hasText(root, "Избранные склады") ||
                DomUtils.hasText(root, "ИЗБРАННЫЕ СКЛАДЫ")
        val hasRecordButton = DomUtils.hasText(root, "Записаться")
        return hasFiltersTitle && hasFavorites && !hasRecordButton
    }

    // ==================== ЭКРАН ПРОЦЕССОВ ====================

    fun isProcessListScreen(root: AccessibilityNodeInfo): Boolean {
        val hasAvailableHeader = DomUtils.hasText(root, "ДОСТУПНЫЕ")
        if (!hasAvailableHeader) return false
        if (isWarehouseScreen(root)) return false
        if (isCalendarScreen(root)) return false
        if (isTimePickerModal(root)) return false
        return true
    }

    fun isWaitingConfirmationScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Ждёт подтверждения")
    }

    // ==================== ЭКРАН КАЛЕНДАРЯ ====================

    fun isCalendarScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Записывайтесь заранее") ||
                DomUtils.hasText(root, "Выберите дату")
    }

    // ==================== ЭКРАН ВЫБОРА ВРЕМЕНИ ====================

    fun isTimePickerModal(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Выберите время")
    }

    // ==================== НЕТ МЕСТ ====================

    fun isNoSlotsScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Нет мест") ||
                DomUtils.hasText(root, "НЕТ МЕСТ") ||
                DomUtils.hasText(root, "Выберите другую операцию")
    }

    // ==================== ДРУГИЕ СОСТОЯНИЯ ====================

    /**
     * Домашний экран — calendarTab выбран но это не склад/процесс/календарь
     * По сути то же что isOnBookingsTab — оставляем для совместимости
     */
    fun isOnHomeScreen(root: AccessibilityNodeInfo): Boolean {
        return isOnBookingsTab(root)
    }

    fun isOnOtherTab(root: AccessibilityNodeInfo): Boolean {
        val walletSelected = root.findAccessibilityNodeInfosByViewId(
            "ru.ozon.hire:id/walletTab"
        )?.any { it.isSelected } == true

        val tripsSelected = root.findAccessibilityNodeInfosByViewId(
            "ru.ozon.hire:id/tripsTab"
        )?.any { it.isSelected } == true

        val coursesSelected = root.findAccessibilityNodeInfosByViewId(
            "ru.ozon.hire:id/coursesTab"
        )?.any { it.isSelected } == true

        return walletSelected || tripsSelected || coursesSelected
    }

    fun isOzonAppLoaded(
        root: AccessibilityNodeInfo,
        warehouseFinder: () -> AccessibilityNodeInfo?
    ): Boolean {
        val hasWarehouseTab = root.findAccessibilityNodeInfosByViewId(
            "ru.ozon.hire:id/warehouseTab"
        )?.isNotEmpty() == true

        val hasCalendarTab = root.findAccessibilityNodeInfosByViewId(
            "ru.ozon.hire:id/calendarTab"
        )?.isNotEmpty() == true

        return hasWarehouseTab || hasCalendarTab || warehouseFinder() != null
    }
}