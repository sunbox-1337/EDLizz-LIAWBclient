package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S12PacketEntityVelocity
import kotlin.math.cos
import kotlin.math.sin

object GrimFly : Module("GrimFly", Category.MISC) {

    val vspeed by float("VSpeed", 0.42f, 0.01f..1.0f)
    val hspeed by float("HSpeed", 0.42f, 0.01f..1.0f)

    private var sent = false

    override fun onDisable() {
        sent = false
    }

    /**
     * 计算移动方向对应的 yaw 角度（复制自 EntitySpeed 的 getMoveYaw）
     */
    private fun getMoveYaw(yaw: Double): Double {
        var result = yaw
        if ((mc.thePlayer?.moveForward ?: 0f) < 0f) result += 180.0
        var forward = 1.0
        if ((mc.thePlayer?.moveForward ?: 0f) < 0f) forward = -0.5
        else if ((mc.thePlayer?.moveForward ?: 0f) > 0f) forward = 0.5
        if ((mc.thePlayer?.moveStrafing ?: 0f) > 0f) result -= 90.0 * forward
        if ((mc.thePlayer?.moveStrafing ?: 0f) < 0f) result += 90.0 * forward
        return result
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is S12PacketEntityVelocity && packet.entityID == mc.thePlayer?.entityId && sent) {
            event.cancelEvent()
            val yawRad = Math.toRadians(getMoveYaw(RotationUtils.serverRotation.yaw.toDouble()))  // yaw 显式转 Double
            mc.thePlayer?.motionX = -sin(yawRad) * hspeed.toDouble()
            mc.thePlayer?.motionZ = cos(yawRad) * hspeed.toDouble()
            mc.thePlayer?.motionY = vspeed.toDouble()
            sent = false
        }
    }

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState == EventState.POST) {
            mc.netHandler.addToSendQueue(C03PacketPlayer(mc.thePlayer?.onGround ?: false))
            sent = true
        }
    }
}