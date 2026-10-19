/*
 * AutoBlock —— 自动格挡（重写版）
 *
 * 用 MC 自己的「模拟右键」起手格挡（playerController.sendUseItem），
 * 松开时发 C07 RELEASE_USE_ITEM 并清掉 itemInUse。
 *
 * 两种模式：
 *  - Cycle   ：每 N tick 的循环里松开 M tick，其余时间一直格挡（默认 10 tick 里松 1 tick）
 *  - Predict ：进范围 + 敌人挥手 → 先挡 kr tick；
 *              自己被打后等 10 tick 伤害冷却，在冷却结束前后 ± kr/2 的窗口内格挡；
 *              再用两次被击中的间隔预测下一次命中时间，在其前后 ± kr/2 继续挡，循环。
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemSword

object AutoBlock : Module("AutoBlock", Category.COMBAT) {

    private val mode by choices("Mode", arrayOf("Cycle", "Predict"), "Cycle")

    /** 敌人距离范围：超出这个距离不格挡 */
    private val range by float("Range", 4.5f, 2f..8f)

    /** 只有范围内有敌人才工作 */
    private val requireTarget by boolean("RequireTarget", true)

    /** 只有玩家按住右键（使用键）时才执行格挡 */
    private val requireRightClick by boolean("RequireRightClick", true)

    // ---------------- Cycle ----------------
    /** 循环周期（tick） */
    private val cycleLength by int("CycleTicks", 10, 3..40) { mode == "Cycle" }

    /** 每个周期里松开的 tick 数 */
    private val releaseTicks by int("ReleaseTicks", 1, 1..10) { mode == "Cycle" }

    // ---------------- Predict ----------------
    /** 宽容度 kr：格挡窗口前后各放宽 kr/2 tick */
    private val tolerance by int("Tolerance(kr)", 4, 1..15) { mode == "Predict" }

    /** 被击中后的伤害冷却（tick），默认 10 */
    private val damageCooldown by int("DamageCooldown", 10, 5..30) { mode == "Predict" }

    /** 敌人挥手时也先挡一下（kr tick） */
    private val swingTrigger by boolean("SwingTrigger", true) { mode == "Predict" }

    private var tick = 0
    private var blocking = false

    // Predict 状态
    private var lastHitAt = Int.MIN_VALUE
    private var hitInterval = -1
    private var swingBlockUntil = Int.MIN_VALUE
    private var predictedAt = Int.MIN_VALUE

    override fun onEnable() {
        tick = 0
        resetPredict()
    }

    override fun onDisable() {
        stopBlock()
        resetPredict()
    }

    private fun resetPredict() {
        lastHitAt = Int.MIN_VALUE
        hitInterval = -1
        swingBlockUntil = Int.MIN_VALUE
        predictedAt = Int.MIN_VALUE
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler
        tick++

        // 必须手持剑才能格挡
        if (player.heldItem?.item !is ItemSword) {
            stopBlock()
            return@handler
        }

        // 只有按住右键（使用键）才执行格挡
        if (requireRightClick && !mc.gameSettings.keyBindUseItem.isKeyDown) {
            stopBlock()
            return@handler
        }

        if (requireTarget && findEnemy() == null) {
            stopBlock()
            return@handler
        }

        val shouldBlock = if (mode == "Cycle") cycleShouldBlock() else predictShouldBlock(player)

        if (shouldBlock && findEnemy() != null) startBlock() else stopBlock()
    }

    /** Cycle：一个周期内前 releaseTicks 个 tick 松手，其余全挡 */
    private fun cycleShouldBlock(): Boolean {
        val period = cycleLength.coerceAtLeast(1)
        val phase = tick % period
        return phase >= releaseTicks.coerceAtMost(period - 1)
    }

    /** Predict：挥手预判 + 冷却窗口 + 命中预测窗口 */
    private fun predictShouldBlock(player: EntityPlayer): Boolean {
        val enemy = findEnemy() ?: return false
        val half = ((tolerance + 1) / 2).coerceAtLeast(1)

        // 1) 敌人正在挥手 → 先挡 kr tick（相当于提前出手）
        if (swingTrigger && enemy.isSwingInProgress) {
            swingBlockUntil = tick + tolerance
        }

        // 2) 自己被击中 → 记录时间 / 更新命中间隔 / 推算下一次命中
        val justHit = player.hurtTime > 0 && player.hurtTime == player.maxHurtTime
        if (justHit && (lastHitAt == Int.MIN_VALUE || tick - lastHitAt > 2)) {
            if (lastHitAt != Int.MIN_VALUE) {
                val delta = tick - lastHitAt
                if (delta in 3..200) hitInterval = delta
            }
            lastHitAt = tick
            predictedAt = if (hitInterval > 0) tick + hitInterval else Int.MIN_VALUE
        }

        // 3) 伤害冷却结束前后 kr/2 的窗口
        val cooldownWindow = lastHitAt != Int.MIN_VALUE &&
                tick >= lastHitAt + damageCooldown - half &&
                tick <= lastHitAt + damageCooldown + half

        // 4) 预测的下次命中前后 kr/2
        val predictWindow = predictedAt != Int.MIN_VALUE &&
                tick >= predictedAt - half && tick <= predictedAt + half

        return tick <= swingBlockUntil || cooldownWindow || predictWindow
    }

    /** 找目标：优先 KillAura 的当前目标，否则最近的玩家 */
    private fun findEnemy(): EntityLivingBase? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        val auraTarget = KillAura.target
        if (auraTarget != null && auraTarget.isEntityAlive && player.getDistanceToEntity(auraTarget) <= range) {
            return auraTarget
        }

        return world.playerEntities
            .asSequence()
            .filter { it !== player && it.isEntityAlive }
            .filter { player.getDistanceToEntity(it) <= range }
            .minByOrNull { player.getDistanceToEntity(it) }
    }

    /**
     * 模拟右键（合法路径）：
     * 不自己拼 C08/C07，而是把「使用键」的按键状态设成按下，
     * 让 MC 自己的 processKeyBinds → rightClickMouse 去发包，
     * 这样服务端看到的和真人按住右键完全一致（含槽位同步、使用状态、延迟计时）。
     */
    private fun startBlock() {
        val player = mc.thePlayer ?: return
        if (player.isBlocking) {
            blocking = true
            return
        }
        val stack = player.heldItem ?: return
        if (stack.item !is ItemSword) return

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, true)
        blocking = true
    }

    /**
     * 松开：把按键状态设回弹起，并让 MC 自己走 onStoppedUsingItem
     * （原版就是发 C07 RELEASE_USE_ITEM + 清 itemInUse，不是我们手搓）。
     */
    private fun stopBlock() {
        if (!blocking) return
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
        val player = mc.thePlayer ?: return
        if (player.isBlocking) {
            mc.playerController.onStoppedUsingItem(player)
        }
        blocking = false
    }
}
