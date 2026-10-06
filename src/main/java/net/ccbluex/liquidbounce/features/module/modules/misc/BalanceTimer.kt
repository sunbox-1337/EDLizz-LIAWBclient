package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.InfoDisplay
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import java.util.LinkedList

object BalanceTimer : Module("BalanceTimer", Category.MISC) {

    val speed by float("TimerSpeed", 2.0f, 2.0f..10.0f)

    private var movementPacket = 0
    private var isReleasing = false
    private val packets = LinkedList<Packet<*>>()

    override fun onDisable() {
        mc.timer.timerSpeed = 1.0f
        movementPacket = 0
        isReleasing = false
        packets.forEach { PacketUtils.sendPacket(it, false) }
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
        PacketUtils.sendPacket(C0FPacketConfirmTransaction(0, 0, false), false)
        // 调试信息：每3tick在聊天栏显示当前积压的移动包数量
        if (mc.thePlayer?.ticksExisted?.rem(3) == 0) {
            InfoDisplay.showLog("balance move packets:" + packets + movementPacket,1000)
        }
    }
}