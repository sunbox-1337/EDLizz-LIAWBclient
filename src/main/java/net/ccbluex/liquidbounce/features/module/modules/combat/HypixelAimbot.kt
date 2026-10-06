/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.rotationDifference
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.toRotation
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.input.Mouse

/**
 * HypixelAimbot —— 参考 Aimbot 写的"硬锁"版。
 *
 * 逻辑：
 * 1. 取 范围内 + FOV 内 + 射线可见（[net.minecraft.entity.player.EntityPlayer.canEntityBeSeen]）的敌人；
 * 2. **硬锁**「射线距离最短」（即最近）的那个，直接怼上去（不做平滑）；
 * 3. **Multi 模式**：把附近所有敌人都塞进 [hurtList]；
 * 4. **hurt 逻辑**：MC 攻击有 10 tick 受伤间隔。被我们打过的目标在冷却内**不瞄**，
 *    优先瞄其它敌人；如果范围内没有别的敌人，才回头瞄他。
 *
 * AutoBlock 读 [target] / [readyToAttack]，并在攻击后调 [markHurt]。
 */
object HypixelAimbot : Module("HypixelAimbot", Category.COMBAT) {

    private val range by float("Range", 4.4F, 1F..8F)
    private val fov by float("FOV", 180F, 1F..180F)
    private val multi by boolean("Multi", false)
    private val hurtDelay by int("HurtDelay", 10, 1..20)
    private val horizontalAim by boolean("HorizontalAim", true)
    private val verticalAim by boolean("VerticalAim", true)
    private val predictEnemyPosition by float("PredictEnemyPosition", 1.5f, -1f..2f)
    private val onlyOnLeftClick by boolean("OnlyOnLeftClick", true)

    /** 被攻击过的实体 → 剩余 hurt 冷却 tick（期间不瞄他，除非没别的目标）。 */
    val hurtList = HashMap<Entity, Int>()

    /** 当前硬锁的目标（AutoBlock 读这个）。 */
    var target: Entity? = null
        private set

    /** 是否已经瞄准好、可以发攻击信号（AutoBlock 用）。 */
    var readyToAttack = false
        private set

    /** AutoBlock 打完一个目标后调用，把他放进 hurt 冷却。 */
    fun markHurt(entity: Entity) {
        hurtList[entity] = hurtDelay
    }

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.POST) return@handler

        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        // 递减 hurt 冷却
        val iterator = hurtList.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val left = entry.value - 1
            if (left <= 0) iterator.remove() else entry.setValue(left)
        }

        // 按住左键期间每一 tick 都持续瞄（不是点一下只瞄一帧）
        val holdingLeft = mc.gameSettings.keyBindAttack.isKeyDown || Mouse.isButtonDown(0)
        if (onlyOnLeftClick && !holdingLeft) {
            target = null
            readyToAttack = false
            return@handler
        }

        // 1. 候选：范围内 + FOV 内 + 射线可见
        val candidates = world.loadedEntityList.filter { e ->
            e is EntityLivingBase && isSelected(e, true) &&
                player.getDistanceToEntityBox(e) <= range &&
                rotationDifference(e) <= fov &&
                player.canEntityBeSeen(e)
        }
        if (candidates.isEmpty()) {
            target = null
            readyToAttack = false
            return@handler
        }

        // 3. Multi：附近所有人都进 hurt 列表
        if (multi) candidates.forEach { hurtList.putIfAbsent(it, hurtDelay) }

        // 4. 优先没在 hurt 冷却里的；都没有则回头瞄最近的
        val cooled = candidates.filter { !hurtList.containsKey(it) }
        val pick = (if (cooled.isNotEmpty()) cooled else candidates)
            .minByOrNull { player.getDistanceToEntityBox(it) } ?: return@handler

        target = pick

        // 2. 硬锁（含敌人位移预测）
        val prediction = pick.currPos.subtract(pick.prevPos).times(2 + predictEnemyPosition.toDouble())
        val destination = toRotation(pick.hitBox.offset(prediction).center, true) ?: return@handler

        destination.toPlayer(player, horizontalAim, verticalAim)

        // 不要一直发信号：只有目标过了受伤间隔(hurtTime<=0)才 ready，AutoBlock 才会攻击
        readyToAttack = (pick as? EntityLivingBase)?.hurtTime?.let { it <= 0 } == true
    }

    override fun onDisable() {
        hurtList.clear()
        target = null
        readyToAttack = false
    }
}
