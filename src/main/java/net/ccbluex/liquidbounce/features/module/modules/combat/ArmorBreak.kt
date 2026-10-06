package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemTool
import net.minecraft.network.play.client.C0EPacketClickWindow

object ArmorBreak : Module("ArmorBreak", Category.COMBAT) {

    private val sendEvent by choices("SendEvent", arrayOf("Attack", "PostMotion", "PreMotion"), "Attack")
    private val clickMode by choices("ClickMode", arrayOf("WindowClick", "SendPacket"), "WindowClick")
    private val minHurtTime by int("MinHurtTime", 3, 0..9)
    private val axe by boolean("AddAxeToArmorBreak", false)
    private val switchBack by boolean("AutoSwitchBackToLowestDamageWeapon", false)

    private var lastSwordDamage = -1.0
    private var switchEnd = false

    private val onMotion = handler<MotionEvent> { event ->
        val target = KillAura.target ?: return@handler
        if (sendEvent == "PostMotion" && event.eventState == EventState.POST) {
            doIt(target)
        } else if (sendEvent == "PreMotion" && event.eventState == EventState.PRE) {
            doIt(target)
        }
    }

    private val onAttack = handler<AttackEvent> { event ->
        if (sendEvent == "Attack") {
            (event.targetEntity as? EntityLivingBase)?.let { doIt(it) }
        }
    }

    override val tag: String
        get() = sendEvent

    // 通过反射获取 ItemTool 的基础伤害
    private fun getAttackDamage(stack: ItemStack): Float {
        val item = stack.item
        if (item is ItemTool) {
            try {
                val field = ItemTool::class.java.getDeclaredField("damageVsEntity")
                field.isAccessible = true
                return field.getFloat(item)
            } catch (_: Exception) {}
        }
        return 0f
    }

    private fun getSwitchSlot(targetHurtTime: Int): Int {
        val damages = mutableListOf<Float>()
        val slots = mutableListOf<Int>()

        if (targetHurtTime <= minHurtTime) {
            lastSwordDamage = -1.0
            switchEnd = false
        }
        if (switchEnd && switchBack) {
            lastSwordDamage = -1.0
        }

        val player = mc.thePlayer ?: return -1
        for (i in 9 until 45) {
            val stack = player.inventoryContainer.getSlot(i).stack ?: continue
            val item = stack.item
            if (item !is ItemSword && (!axe || item !is ItemAxe)) continue
            val damage = getAttackDamage(stack)
            if (damage > lastSwordDamage) {
                damages.add(damage)
                slots.add(i)
            }
        }

        var minDamage = Float.MAX_VALUE
        var bestSlot = -1
        for (i in damages.indices) {
            if (damages[i] < minDamage) {
                minDamage = damages[i]
                bestSlot = slots[i]
            }
        }

        return if (minDamage != Float.MAX_VALUE) {
            lastSwordDamage = minDamage.toDouble()
            bestSlot
        } else {
            switchEnd = true
            -1
        }
    }

    private fun doIt(target: EntityLivingBase) {
        val player = mc.thePlayer ?: return
        val slot = getSwitchSlot(target.hurtTime)
        if (slot == -1) return

        when (clickMode) {
            "WindowClick" -> mc.playerController.windowClick(player.openContainer.windowId, slot, player.inventory.currentItem, 2, player)
            else -> sendSwitchPacket(slot, player)
        }
    }

    private fun sendSwitchPacket(slot: Int, player: EntityPlayer) {
        val packet = C0EPacketClickWindow(
            player.openContainer.windowId,
            slot,
            player.inventory.currentItem,
            2,
            player.inventory.getStackInSlot(player.inventory.currentItem + 36),
            player.openContainer.getNextTransactionID(player.inventory)
        )
        PacketUtils.sendPacket(packet)
    }
}