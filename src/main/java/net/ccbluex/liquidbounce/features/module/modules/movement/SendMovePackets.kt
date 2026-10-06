package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.network.play.client.C03PacketPlayer

object SendMovePackets : Module("SendMovePackets", Category.PLAYER) {
    val onPacket = handler<PacketEvent> { event ->
        if (event.packet is C03PacketPlayer) {
            event.cancelEvent()
        }
    }
}