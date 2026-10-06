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
import net.ccbluex.liquidbounce.event.PlaceClickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.ClientUtils.chat
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils.overBlock
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.minecraft.network.play.client.C03PacketPlayer
import java.util.*

object ViaPacketFix : Module("ViaPacketFix", Category.ADDONS) {

    val c08fix by boolean("C08Fix", false)
    val placeTimingFix by boolean("PlaceTimingFix", false)

    val packets = LinkedList<Packet<*>>()
    val ticks = LinkedList<Int>()
    var lastRotations: Rotation? = null
    var realRotations: Rotation? = null

    val onPlace = handler<PlaceClickEvent> { event ->
        if (!placeTimingFix) return@handler
        val rot = realRotations ?: return@handler
        if (!overBlock(rot, event.facing, event.blockPos, true)) {
            event.cancelEvent()
            chat("Fix Placement")
        }
    }

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