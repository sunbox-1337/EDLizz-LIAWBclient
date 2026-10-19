/*
 * JKLArBreak - 武器轮换破甲（抄自 Air Client ArmorBreaker）
 *
 * 逻辑:
 *   1. 攻击对方时，快照所有武器（热栏+主背包），按伤害从高到低排序
 *   2. 轮换阶段：每 tick 把快照中下一把武器交换到当前手持热栏位置
 *      - 热栏武器(0-8)：直接切换 currentItem
 *      - 背包武器(9-35)：通过 windowClick(mode=2) 交换到 currentItem 热栏位置
 *   3. 优先级: 目标可被攻击 > 自己可被攻击 > 轮换
 *   4. 目标无敌帧时轮换武器磨损对方盔甲，目标可被攻击时切回最高伤害武器
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.ccbluex.liquidbounce.utils.inventory.attackDamage
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword

object JKLArBreak : Module("JKLArBreak", Category.COMBAT, spacedName = "JKL ArBreak") {

    private val axeMode by boolean("AxeMode", false)
    private val onlyKillAura by boolean("OnlyKillAura", false)
    private val debugMode by boolean("Debug", false)

    /** 当前攻击目标 */
    private var target: EntityLivingBase? = null
    /** 是否处于轮换状态 */
    private var isCycling = false
    /** 轮换索引 */
    private var cycleIndex = 0
    /** 轮换快照：每把武器的（原始槽位, 伤害），按伤害从高到低 */
    private var cycleWeapons: List<Pair<Int, Double>> = emptyList()

    override fun onDisable() {
        target = null
        isCycling = false
        cycleIndex = 0
        cycleWeapons = emptyList()
    }

    val onAttack = handler<AttackEvent> { event ->
        if (onlyKillAura && !KillAura.handleEvents()) return@handler

        target = event.targetEntity as? EntityLivingBase

        // 攻击时快照所有武器
        cycleWeapons = snapshotWeapons()
        cycleIndex = 0
        isCycling = true

        debug("Attack detected, target=${target?.name}, ${cycleWeapons.size} weapons snapshotted")
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler

        // Scaffold 启用时禁用
        if (Scaffold.handleEvents()) return@handler

        if (onlyKillAura && !KillAura.handleEvents()) {
            if (isCycling) {
                switchToBestWeapon()
                isCycling = false
            }
            return@handler
        }

        // 没有武器快照且不在轮换，无事可做
        if (cycleWeapons.isEmpty() && !isCycling) return@handler

        // 清理无效目标
        val tgt = target
        if (tgt == null || tgt.isDead || !mc.theWorld.loadedEntityList.contains(tgt)) {
            target = null
            if (isCycling) {
                switchToBestWeapon()
                isCycling = false
            }
            return@handler
        }

        // 优先级1: 目标可被攻击 (hurtTime==0) → 切到最高伤害武器
        // 优先级2: 自己可被攻击 (hurtTime==0) → 确保手上有武器
        // 优先级3: 两者都在无敌帧 → 轮换
        when {
            tgt.hurtTime == 0 -> {
                if (isCycling) debug("Can attack target → hold best weapon")
                switchToBestWeapon()
                isCycling = false
            }
            player.hurtTime == 0 -> {
                val heldItem = player.heldItem
                val isHoldingWeapon = if (axeMode) {
                    heldItem?.item is ItemSword || heldItem?.item is ItemAxe
                } else {
                    heldItem?.item is ItemSword
                }
                if (!isHoldingWeapon) {
                    if (isCycling) debug("Can be damaged, not holding weapon → force hold best weapon")
                    switchToBestWeapon()
                }
                isCycling = false
            }
            isCycling && cycleIndex < cycleWeapons.size -> {
                // 轮换：把第 cycleIndex 把武器换到手上
                val (slot, damage) = cycleWeapons[cycleIndex]
                debug("Cycling → weapon at slot $slot (damage=${String.format("%.1f", damage)}), ${cycleIndex + 1}/${cycleWeapons.size}")
                swapWeaponToHand(slot)
                cycleIndex++
            }
            isCycling -> {
                // 轮换完成，切到最高伤害武器
                switchToBestWeapon()
                isCycling = false
                debug("Cycle complete → hold best weapon")
            }
        }
    }

    /**
     * 快照当前所有武器（热栏 0-8 + 主背包 9-35），按伤害从高到低排序。
     */
    private fun snapshotWeapons(): List<Pair<Int, Double>> {
        val player = mc.thePlayer ?: return emptyList()

        return (0..35).mapNotNull { slot ->
            val stack = player.inventory.getStackInSlot(slot) ?: return@mapNotNull null
            val isWeapon = if (axeMode) {
                stack.item is ItemSword || stack.item is ItemAxe
            } else {
                stack.item is ItemSword
            }
            if (isWeapon) slot to stack.attackDamage else null
        }.sortedByDescending { it.second }
    }

    /**
     * 切换到当前伤害最高的武器。
     */
    private fun switchToBestWeapon() {
        val best = snapshotWeapons().firstOrNull() ?: return
        swapWeaponToHand(best.first)
    }

    /**
     * 把指定槽位的武器换到玩家手上。
     *   - 热栏(0-8)：直接切换 currentItem
     *   - 主背包(9-35)：通过 windowClick(mode=2) 交换到 currentItem 热栏位置
     */
    private fun swapWeaponToHand(weaponSlot: Int) {
        val player = mc.thePlayer ?: return

        if (weaponSlot <= 8) {
            // 武器已在热栏，直接切换
            if (player.inventory.currentItem != weaponSlot) {
                player.inventory.currentItem = weaponSlot
                mc.playerController?.syncCurrentPlayItem()
            }
        } else {
            // 武器在主背包，交换到当前手持热栏位置
            val hotbarSlot = player.inventory.currentItem

            val wasInventoryOpen = serverOpenInventory
            if (!wasInventoryOpen) {
                serverOpenInventory = true
            }

            mc.playerController?.windowClick(
                player.openContainer.windowId,
                weaponSlot,       // 容器槽位（主背包9-35直接对应容器索引）
                hotbarSlot,       // 热栏索引（0-8）
                2,                // 模式2 = 与热栏槽位交换
                player
            )

            if (!wasInventoryOpen) {
                serverOpenInventory = false
            }

            debug("Swapped inv slot $weaponSlot → hotbar $hotbarSlot")
        }
    }

    private fun debug(message: String) {
        if (debugMode) {
            chat("§7[JKLArBreak] §f$message")
        }
    }
}
