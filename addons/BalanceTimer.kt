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

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.ClientUtils.chat
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import java.util.*

object BalanceTimer : Module("BalanceTimer", Category.ADDONS) {

    val speed by float("TimerSpeed", 2.0f, 2.0f..10.0f)

    private var movementPacket = 0
    private var isReleasing = false
    private val packets = LinkedList<Packet<*>>()

    override fun onDisable() {
        mc.timer.timerSpeed = 1.0f
        movementPacket = 0
        isReleasing = false
        packets.forEach { sendPacket(it, false) }
        packets.clear()
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is C03PacketPlayer) {
            isReleasing = false
            if (!packet.isMoving && !packet.rotating) {
                mc.timer.timerSpeed = 1.0f
                movementPacket++
                event.cancelEvent()
            } else if (movementPacket > 0) {
                mc.timer.timerSpeed = speed
                movementPacket--
                isReleasing = true
            } else {
                mc.timer.timerSpeed = 1.0f
            }
        }
        if (packet is C0FPacketConfirmTransaction) {
            packets.add(packet)
            event.cancelEvent()
        }
    }

    val onUpdate = handler<UpdateEvent> {
        sendPacket(C0FPacketConfirmTransaction(0, 0, false), false)
        if (mc.thePlayer?.ticksExisted?.rem(3) == 0) {
            chat(movementPacket.toString())
        }
    }
}