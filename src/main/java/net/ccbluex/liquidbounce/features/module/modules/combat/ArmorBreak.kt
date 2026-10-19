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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.PreAttackEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.AutoGapple
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.ccbluex.liquidbounce.utils.inventory.ItemUtils
import net.ccbluex.liquidbounce.utils.annotations.JNICExclude
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0EPacketClickWindow
import java.util.LinkedList
import java.util.Queue

object ArmorBreak : Module("ArmorBreak", Category.COMBAT) {

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
    private val axe by boolean("AddAxeToArmorBreak", false)
    private val switchBack by boolean("AutoSwitchBackToLowestDamageWeapon", false)
    private val onGap by boolean("onGap", false)

    // ---------- MultiHit / BlockBreak ----------
    // 目标格挡时，一段伤害 d 会被压成 (1 + d) * 0.5。
    // 在同一个伤害周期内拆成多段打：(1+b)*0.5 + (1+c)*0.5 > (a+1)*0.5，
    // 也就是「格挡 + 多段」比「一整段」掉更多血，同时每一段都会各扣一次护甲耐久。
    private val multiHit by choices("MultiHit", arrayOf("Off", "Blocking", "Always"), "Off")
    private val hitCount by int("HitCount", 2, 2..5)
    private val critPattern by choices(
        "CritPattern",
        arrayOf("OneCritOneNormal", "BothCrit", "BothNormal"),
        "OneCritOneNormal"
    )
    private val critDrop by float("CritDrop", 0.1f, 0.0f..1.0f)
    private val swingEachHit by boolean("SwingEachHit", true)

    // 内部状态
    private var lastSwordDamage = -1.0
    private var switchEnd = false
    private var lastMultiHitTick = -1

    // 事件处理
    private val onMotion = handler<MotionEvent> { event ->
        val target = KillAura.target ?: return@handler
        when {
            event.eventState == EventState.POST && sendEvent == "PostMotion" -> doIt(target)
            event.eventState == EventState.PRE && sendEvent == "PreMotion" -> doIt(target)
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

        // 武器切换包走在前面，服务端先收到切槽，再结算补刀
        sendMultiHit(target)
    }

    /**
     * MultiHit / BlockBreak
     *
     * 格挡时一段伤害 d 被压成 (1 + d) * 0.5。若在同一个伤害周期内拆成多段，
     * (1+b)*0.5 + (1+c)*0.5 > (a+1)*0.5，格挡方反而掉更多血；
     * 并且每一段伤害都会各扣一次护甲耐久（1.8.9：每 4 点伤害 1 耐久，最低 1）。
     */
    private fun sendMultiHit(target: EntityLivingBase) {
        if (multiHit == "Off") return
        if (multiHit == "Blocking" && !looksBlocking(target)) return

        val player = mc.thePlayer ?: return
        if (lastMultiHitTick == player.ticksExisted) return
        lastMultiHitTick = player.ticksExisted

        val extra = (hitCount - 1).coerceAtLeast(1)
        val wasOnGround = player.onGround
        val wasSneaking = player.isSneaking

        try {
            for (i in 1..extra) {
                val wantCrit = when (critPattern) {
                    "BothCrit" -> true
                    "BothNormal" -> false
                    // 默认：一次暴击 + 一次不暴击（空手就是 1.25 与 1.0）
                    else -> i % 2 == 1
                }

                // 暴击需要服务端认为玩家在下落：先发一个 onGround=false 且略微下移的位置包
                sendFakeFall(wantCrit)
                if (swingEachHit) PacketUtils.sendPacket(C0APacketAnimation())

                PacketUtils.sendPacket(C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK))
            }
        } finally {
            // 恢复真实地面状态，避免后续移动被判为飞行/后摇异常
            sendGroundState(wasOnGround, wasSneaking)
        }
    }

    private fun sendFakeFall(crit: Boolean) {
        val player = mc.thePlayer ?: return
        val y = if (crit) player.posY - critDrop else player.posY
        PacketUtils.sendPacket(C04PacketPlayerPosition(player.posX, y, player.posZ, false))
    }

    private fun sendGroundState(onGround: Boolean, sneaking: Boolean) {
        val player = mc.thePlayer ?: return
        if (sneaking) player.isSneaking = true
        PacketUtils.sendPacket(
            C06PacketPlayerPosLook(
                player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch, onGround
            )
        )
    }

    /**
     * 目标是否处于格挡状态。服务端不会把格挡状态同步给客户端，
     * 这里用本地能拿到的信号做启发式判断（1.8.9 举剑/弓格挡 = 正在使用物品）。
     */
    private fun looksBlocking(target: EntityLivingBase): Boolean {
        if (target !is EntityPlayer) return false
        if (target.isBlocking) return true
        val held = target.heldItem ?: return false
        return target.isUsingItem && (held.item is ItemSword || held.item is ItemBow)
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