/*
 * NekoBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/RouQingNeko1024/NekoBounce
 * Code By GoldBounce,Lizz,NightSky,FDP
 * https://github.com/SkidderMC/FDPClient
 * https://github.com/qm123pz/NightSky-Client
 * https://github.com/bzym2/GoldBounce/
 */
//SKID SuperBounce
//By NekoBanka
package net.ccbluex.liquidbounce.features.module.modules.addons

import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.serverRotation
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S12PacketEntityVelocity
import kotlin.math.cos
import kotlin.math.sin

object GrimFly : Module("GrimFly", Category.ADDONS) {

    val vspeed by float("VSpeed", 0.42f, 0.01f..1.0f)
    val hspeed by float("HSpeed", 0.42f, 0.01f..1.0f)

    private var sent = false

    override fun onDisable() {
        sent = false
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is S12PacketEntityVelocity && packet.entityId == mc.thePlayer?.entityId && sent) {
            event.cancelEvent()
            val yawRad = Math.toRadians(EntitySpeed.getMoveYaw(serverRotation.yaw))
            mc.thePlayer?.motionX = -sin(yawRad) * hspeed
            mc.thePlayer?.motionZ = cos(yawRad) * hspeed
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