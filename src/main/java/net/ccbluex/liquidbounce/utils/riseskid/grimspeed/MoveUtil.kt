package net.ccbluex.liquidbounce.utils.riseskid.grimspeed

import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import kotlin.math.cos
import kotlin.math.sin

object MoveUtil : MinecraftInstance {

    fun isMoving(): Boolean {
        val player = mc.thePlayer ?: return false
        return player.moveForward != 0f || player.moveStrafing != 0f
    }

    fun direction(): Double {
        val player = mc.thePlayer ?: return 0.0
        var rotationYaw = player.rotationYaw

        if (player.moveForward < 0f) {
            rotationYaw += 180f
        }

        var forward = 1f
        if (player.moveForward < 0f) {
            forward = -0.5f
        } else if (player.moveForward > 0f) {
            forward = 0.5f
        }

        if (player.moveStrafing > 0f) {
            rotationYaw -= 90f * forward
        }
        if (player.moveStrafing < 0f) {
            rotationYaw += 90f * forward
        }

        return Math.toRadians(rotationYaw.toDouble())
    }

    fun moveFlying(increase: Double) {
        val player = mc.thePlayer ?: return
        if (!isMoving()) return

        val yaw = direction()
        player.motionX += -sin(yaw) * increase
        player.motionZ += cos(yaw) * increase
    }
}