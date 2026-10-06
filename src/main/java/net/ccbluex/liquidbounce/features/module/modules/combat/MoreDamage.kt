package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.player.Gapple
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C09PacketHeldItemChange

object MoreDamage : Module("MoreDamage", Category.COMBAT) {

    val minSharp by int("MinSharp", 1, 1..5)
    val onGap by boolean("OnGap", false)

    private fun findItem(start: Int, end: Int): Int {
        for (i in start..end) {
            val stack = mc.thePlayer?.inventoryContainer?.getSlot(i)?.stack ?: continue
            if (EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) >= minSharp) {
                return i
            }
        }
        return -1
    }

    val onPacket = handler<PacketEvent> { event ->
        if (!onGap && Gapple.state) return@handler
        val packet = event.packet
        if (packet is C02PacketUseEntity && packet.action == C02PacketUseEntity.Action.ATTACK) {
            val slot = findItem(36, 44) - 36
            if (slot >= 0) {
                event.cancelEvent()
                PacketUtils.sendPacket(C09PacketHeldItemChange(slot), false)
                PacketUtils.sendPacket(packet, false)
                PacketUtils.sendPacket(C09PacketHeldItemChange(mc.thePlayer?.inventory?.currentItem ?: 0), false)
            }
        }
    }
}