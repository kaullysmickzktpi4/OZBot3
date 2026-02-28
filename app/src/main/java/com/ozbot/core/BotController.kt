package com.ozbot.core

import com.ozbot.automation.OzonHireAutomationService

object BotController {

    fun start() {
        OzonHireAutomationService.getInstance()?.startAutomation()
    }

    fun stop() {
        OzonHireAutomationService.getInstance()?.stopAutomation()
    }

    fun isRunning(): Boolean {
        return OzonHireAutomationService.getInstance()?.isAutomationRunning() ?: false
    }
}