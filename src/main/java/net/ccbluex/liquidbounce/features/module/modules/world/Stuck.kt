package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

object Stuck : Module("Stuck", Category.WORLD) {

    private var stuckTicks = 0

    override fun onEnable() {
        stuckTicks = 1   // 开启时卡住一帧
    }

    override fun onDisable() {
        stuckTicks = 0
    }

    val onPlayerTick = handler<PlayerTickEvent> { event ->
        if (stuckTicks > 0) {
            event.cancelEvent()   // 取消移动包，保持位置不变
            stuckTicks--
        }
    }
}