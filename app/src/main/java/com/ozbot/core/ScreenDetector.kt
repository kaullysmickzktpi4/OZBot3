package com.ozbot.automation.core

import android.view.accessibility.AccessibilityNodeInfo
import com.ozbot.bot.DomUtils

class ScreenDetector {

    fun isWarehouseScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Выберите склад")
    }

    fun isWarehouseLoaded(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Записаться")
    }

    fun isCalendarScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Записывайтесь заранее") ||
                DomUtils.hasText(root, "Выберите дату")
    }

    fun isTimePickerModal(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Выберите время")
    }

    fun isProcessListScreen(root: AccessibilityNodeInfo): Boolean {
        val processKeywords = listOf("Инвентаризация", "Приемка", "Подбор", "Сортировка", "Размещение")
        val hasProcess = processKeywords.any { DomUtils.hasText(root, it) }

        val hasProcessContext = DomUtils.hasText(root, "Выберите операцию") ||
                DomUtils.hasText(root, "Доступные операции") ||
                DomUtils.hasText(root, "Нет мест") ||
                DomUtils.hasText(root, "НЕТ МЕСТ") ||
                DomUtils.hasText(root, "Даты:")

        val isDefinitelyBookings = isOnBookingsTab(root)

        return hasProcess &&
                !isDefinitelyBookings &&
                !isWarehouseScreen(root) &&
                !isCalendarScreen(root) &&
                !isTimePickerModal(root) &&
                !isFilterModalOpen(root) &&
                (hasProcessContext || !DomUtils.hasText(root, "Выберите склад"))
    }

    fun isNoSlotsScreen(root: AccessibilityNodeInfo): Boolean {
        return DomUtils.hasText(root, "Нет мест") ||
                DomUtils.hasText(root, "НЕТ МЕСТ") ||
                DomUtils.hasText(root, "Выберите другую операцию")
    }

    fun isFilterModalOpen(root: AccessibilityNodeInfo): Boolean {
        val hasFiltersTitle = DomUtils.hasText(root, "Фильтры")

        val hasFavorites = DomUtils.hasText(root, "Избранные склады") ||
                DomUtils.hasText(root, "ИЗБРАННЫЕ СКЛАДЫ")

        val hasRecordButton = DomUtils.hasText(root, "Записаться")

        return hasFiltersTitle && hasFavorites && !hasRecordButton
    }

    fun isOnHomeScreen(root: AccessibilityNodeInfo): Boolean {
        if (isFilterModalOpen(root)) return false

        return DomUtils.hasText(root, "Записи") &&
                DomUtils.hasText(root, "Склады") &&
                !isWarehouseScreen(root) &&
                !isProcessListScreen(root)
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Проверяет что мы на вкладке "Записи"
     */
    fun isOnBookingsTab(root: AccessibilityNodeInfo): Boolean {
        val hasBookingsLabel = DomUtils.hasText(root, "Записи") || DomUtils.hasText(root, "Мои записи")
        if (!hasBookingsLabel) return false

        val hasBookingsList = DomUtils.hasText(root, "Предстоящие") ||
                DomUtils.hasText(root, "Предстоящая") ||
                DomUtils.hasText(root, "Завершенные") ||
                DomUtils.hasText(root, "Завершена") ||
                DomUtils.hasText(root, "Отмененные") ||
                DomUtils.hasText(root, "Отменить") ||
                DomUtils.hasText(root, "У вас пока нет записей")

        val processKeywords = listOf("Инвентаризация", "Приемка", "Подбор", "Сортировка", "Размещение")
        val hasProcess = processKeywords.any { DomUtils.hasText(root, it) }

        return hasBookingsList && !hasProcess
    }

    fun isOnOtherTab(root: AccessibilityNodeInfo): Boolean {
        val hasBottomNav = DomUtils.hasText(root, "Записи") ||
                DomUtils.hasText(root, "Выплаты") ||
                DomUtils.hasText(root, "Поездки") ||
                DomUtils.hasText(root, "Курсы")

        return hasBottomNav && !isWarehouseScreen(root)
    }

    fun isOzonAppLoaded(root: AccessibilityNodeInfo, warehouseFinder: () -> AccessibilityNodeInfo?): Boolean {
        val hasBottomNav = warehouseFinder() != null ||
                DomUtils.hasText(root, "Склады") ||
                DomUtils.hasText(root, "Записи") ||
                DomUtils.hasText(root, "Выплаты")

        return hasBottomNav
    }
}