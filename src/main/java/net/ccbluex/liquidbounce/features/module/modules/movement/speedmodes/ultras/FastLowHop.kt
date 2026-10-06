package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.movement.MovementUtils

object FastLowHop : SpeedMode("FastLowHop") {

    var groundSpeed = 1.6f
    var timerBoost = 1.2f

    override fun onMotion() {
        val player = mc.thePlayer ?: return
        if (!player.isMoving || player.isSneaking) return

        if (player.onGround) {
            mc.timer.timerSpeed = timerBoost
            MovementUtils.strafe(groundSpeed)
        } else {
            mc.timer.timerSpeed = 1f
            MovementUtils.strafe(MovementUtils.speed * 0.98f)
        }
    }

    override fun onMove(event: MoveEvent) {
        // 已在 onMotion 处理
    }

    override fun onJump(event: JumpEvent) {
        event.cancelEvent()
        mc.thePlayer.motionY = 0.09
    }

    override fun onDisable() {
        mc.timer.timerSpeed = 1f
    }
}