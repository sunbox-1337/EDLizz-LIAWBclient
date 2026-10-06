package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.minecraft.network.play.client.C03PacketPlayer
import java.util.LinkedList

object ViaPacketFix : Module("ViaPacketFix", Category.MISC) {

    val c08fix by boolean("C08Fix", false)
    val placeTimingFix by boolean("PlaceTimingFix", false)

    val packets = LinkedList<Any>()    // 保留以备将来使用
    var lastRotations: Rotation? = null
    var realRotations: Rotation? = null

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (placeTimingFix && (packet is C03PacketPlayer.C05PacketPlayerLook || packet is C03PacketPlayer.C06PacketPlayerPosLook)) {
            if (lastRotations != null) {
                realRotations = lastRotations
            }
            lastRotations = Rotation(packet.yaw, packet.pitch)
        }
    }
}