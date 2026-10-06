/*
 * NekoBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/RouQingNeko1024/NekoBounce
 * Code By GoldBounce,Lizz,NightSky,FDP
 * https://github.com/SkidderMC/FDPClient
 * https://github.com/qm123pz/NightSky-Client
 * https://github.com/bzym2/GoldBounce/
 */
/*
 *
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.EntityLivingBase
 *  net.minecraft.entity.player.EntityPlayer
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemAxe
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.ItemSword
 *  net.minecraft.network.Packet
 *  net.minecraft.network.play.client.C0EPacketClickWindow
 */
//SKID SuperBounce
//CRK&SKID By NekoBanka
package net.ccbluex.liquidbounce.features.module.modules.neko

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PreAttackEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.ccbluex.liquidbounce.utils.inventory.ItemUtils
import net.ccbluex.liquidbounce.utils.annotations.JNICExclude
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C0EPacketClickWindow
import java.util.LinkedList
import java.util.Queue

object ArmorBreak : Module("ArmorBreak", Category.NEKO) {

    // 配置项（委托属性）
    private val sendEvent by choices(
        "SendEvent",
        arrayOf("PreAttack", "Attack", "PostMotion", "PreMotion"),
        "PreAttack"
    )
    private val clickMode by choices(
        "ClickMode",
        arrayOf("WindowClick", "SendPacket"),
        "WindowClick"
    )
    private val minHurtTime by int("MinHurtTime", 3, 0..9)
    private val axe by bool("AddAxeToArmorBreak", false)
    private val switchBack by bool("AutoSwitchBackToLowestDamageWeapon", false)
    private val onGap by bool("onGap", false)

    // 内部状态
    private var lastSwordDamage = -1.0
    private var switchEnd = false

    // 事件处理
    private val onMotion = handler<MotionEvent> { event ->
        val target = KillAura.target ?: return@handler
        when {
            event.eventState == MotionEvent.State.POST && sendEvent == "PostMotion" -> doIt(target)
            event.eventState == MotionEvent.State.PRE && sendEvent == "PreMotion" -> doIt(target)
        }
    }

    private val onAttack = handler<AttackEvent> { event ->
        if (sendEvent == "Attack") {
            (event.targetEntity as? EntityLivingBase)?.let { doIt(it) }
        }
    }

    private val onPreAttack = handler<PreAttackEvent> { event ->
        if (sendEvent == "PreAttack") {
            (event.targetEntity as? EntityLivingBase)?.let { doIt(it) }
        }
    }

    override val tag: String
        get() = sendEvent

    /**
     * 根据目标受伤时间，获取伤害最低的武器槽位
     */
    private fun getSwitchSlot(targetHurtTime: Int): Int {
        val swordDamages = LinkedList<Double>()
        val swordSlots = LinkedList<Int>()

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
            val item = stack.item ?: continue
            if (item !is ItemSword && (!axe || item !is ItemAxe)) continue
            val damage = ItemUtils.getAttackDamage(stack)
            if (damage > lastSwordDamage) {
                swordDamages.add(damage)
                swordSlots.add(i)
            }
        }

        var minDamage = 10000.0
        var bestSlot = -1
        while (swordDamages.isNotEmpty()) {
            val dmg = swordDamages.poll() ?: break
            val slot = swordSlots.poll() ?: break
            if (dmg < minDamage) {
                minDamage = dmg
                bestSlot = slot
            }
        }

        return if (minDamage != 10000.0) {
            lastSwordDamage = minDamage
            bestSlot
        } else {
            switchEnd = true
            -1
        }
    }

    /**
     * 查找背包中伤害最高的剑
     */
    private fun findBestSword(): Int {
        var maxDamage = -1.0
        var bestSlot = -1
        val player = mc.thePlayer ?: return -1
        for (i in 9 until 45) {
            val stack = player.inventoryContainer.getSlot(i).stack ?: continue
            if (stack.item !is ItemSword) continue
            val damage = ItemUtils.getAttackDamage(stack)
            if (damage > maxDamage) {
                maxDamage = damage
                bestSlot = i
            }
        }
        return bestSlot
    }

    /**
     * 执行武器切换动作
     */
    private fun doIt(target: EntityLivingBase) {
        val player = mc.thePlayer ?: return

        if (AutoGapple.state && !onGap) {
            val slot = findBestSword()
            if (slot != -1) {
                sendSwitchPacket(slot, player)
            }
            return
        }

        val slot = getSwitchSlot(target.hurtTime)
        if (slot == -1) return

        when (clickMode) {
            "WindowClick" -> {
                mc.playerController.windowClick(
                    player.openContainer.windowId,
                    slot,
                    player.inventory.currentItem,
                    2,
                    player
                )
            }
            else -> sendSwitchPacket(slot, player)
        }
    }

    /**
     * 发送 ClickWindow 包切换物品
     */
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

    // ========== @JNICExclude 保留内容 ==========
    @JNICExclude
    fun meow(): String = "喵喵喵"

    @JNICExclude
    fun meowPro(): String = "护甲全碎喵"

    @JNICExclude
    fun meowMax(): String = "NekoBounce喵，你不要删除这个玩意喵"

    @JNICExclude
    fun meowUltra(): String = "喵喵喵喵喵喵喵喵喵喵喵喵喵喵喵喵喵喵"
}