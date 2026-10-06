package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.minecraft.item.ItemBucket
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import kotlin.random.Random

object AutoWaterFall : Module("AutoWaterFall", Category.MOVEMENT) {

    private val fallDist by float("FallDistance", 3f, 1f..10f)
    private val switchBack by boolean("SwitchBack", false) // 建议关闭，减少多余操作

    private var stage = 0       // 0=待触发, 1=已低头, 2=已放置, 3=已恢复
    private var tickCounter = 0
    private var nextStageTick = 0
    private var prevSlot = -1
    private var prevYaw = 0f
    private var prevPitch = 0f

    override fun onEnable() { reset() }
    override fun onDisable() { reset() }

    private fun reset() {
        stage = 0
        tickCounter = 0
        nextStageTick = 0
        prevSlot = -1
    }

    val onMotion = handler<MotionEvent> { e ->
        if (e.eventState != EventState.PRE) return@handler
        val p = mc.thePlayer ?: return@handler
        if (p.isSpectator || !p.isEntityAlive) return@handler

        // 等待落地重置
        if (stage == 3 && p.onGround) {
            if (switchBack && prevSlot != -1 && p.inventory.currentItem != prevSlot)
                p.inventory.currentItem = prevSlot
            reset()
            return@handler
        }

        // 触发检测
        if (stage == 0 && p.motionY < 0 && !p.onGround && p.fallDistance >= fallDist) {
            // 找水桶
            var slot = -1
            for (i in 0..8) {
                val s = p.inventory.getStackInSlot(i)
                if (s != null && s.item is ItemBucket) { slot = i; break }
            }
            if (slot == -1) return@handler

            prevSlot = p.inventory.currentItem
            prevYaw = p.rotationYaw
            prevPitch = p.rotationPitch
            if (p.inventory.currentItem != slot) p.inventory.currentItem = slot

            // 随机延迟 1~4 tick 后低头
            nextStageTick = Random.nextInt(1, 5)
            stage = 1
            tickCounter = 0
            return@handler
        }

        // 执行状态机
        if (stage in 1..2) {
            tickCounter++
            if (tickCounter < nextStageTick) return@handler
            tickCounter = 0

            when (stage) {
                1 -> {
                    // 低头（静默）
                    sendPacket(C03PacketPlayer.C05PacketPlayerLook(prevYaw, 85f, p.onGround))
                    nextStageTick = Random.nextInt(1, 3) // 低头后 1~2 tick 放水
                    stage = 2
                }
                2 -> {
                    // 放水
                    p.heldItem?.let { if (it.item is ItemBucket) sendPacket(C08PacketPlayerBlockPlacement(it)) }
                    nextStageTick = Random.nextInt(2, 5) // 放置后 2~4 tick 抬头
                    stage = 3
                }
            }
            return@handler
        }

        // 放水后延迟抬头
        if (stage == 3 && tickCounter >= nextStageTick) {
            sendPacket(C03PacketPlayer.C05PacketPlayerLook(prevYaw, prevPitch, p.onGround))
            // 等待落地重置（已在开头处理）
        }
    }
}