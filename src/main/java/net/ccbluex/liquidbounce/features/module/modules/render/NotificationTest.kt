package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

object NotificationTest : Module("NotificationTest", Category.RENDER) {
    private var showInfo by boolean("ShowInfo", false)
    private var showSuccess by boolean("ShowSuccess", false)
    private var showError by boolean("ShowError", false)
    private var showWarning by boolean("ShowWarning", false)

    val onTick = handler<GameTickEvent> {
        if (showInfo) {
            WaterMark.showInfo("Info", "This is an information message.")
            showInfo = false
        }
        if (showSuccess) {
            WaterMark.showSuccess("Success", "Operation completed successfully.")
            showSuccess = false
        }
        if (showError) {
            WaterMark.showError("Error", "An error has occurred.")
            showError = false
        }
        if (showWarning) {
            WaterMark.showWarning("Warning", "Proceed with caution.")
            showWarning = false
        }
    }
}