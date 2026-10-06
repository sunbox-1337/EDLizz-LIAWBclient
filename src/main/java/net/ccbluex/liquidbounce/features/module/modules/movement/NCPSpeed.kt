package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.C03PacketPlayer
import kotlin.random.Random

object NCPSpeed : Module("NCPSpeed", Category.MOVEMENT) {

    private val jumpHeight by float("JumpHeight", 0.42f, 0.1f..1.0f)
    private val hSpeed by float("HSpeed", 0.47f, 0.1f..1.0f)

    private val stopIntervalMin by int("StopIntervalMin", 1, 1..200)
    private val stopIntervalMax by int("StopIntervalMax", 200, 1..200)

    private val stopDurationMin by int("StopDurationMin", 0, 0..20)
    private val stopDurationMax by int("StopDurationMax", 20, 0..20)

    private var ticksUntilStop = 0
    private var stopTicksLeft = 0

    // 移动判断辅助
    private val EntityPlayer.isMoving: Boolean
        get() = moveForward != 0f || moveStrafing != 0f

    override fun onEnable() {
        ticksUntilStop = Random.nextInt(stopIntervalMin, stopIntervalMax + 1)
        stopTicksLeft = 0
    }

    override fun onDisable() {
        stopTicksLeft = 0
    }

    val onGameTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        if (!player.isMoving || !player.onGround) return@handler

        // NCPHop 式跳跃加速
        player.motionY = jumpHeight.toDouble()
        MovementUtils.strafe(hSpeed)

        // 停止发包倒计时
        if (stopTicksLeft > 0) {
            stopTicksLeft--
        } else {
            ticksUntilStop--
            if (ticksUntilStop <= 0) {
                stopTicksLeft = Random.nextInt(stopDurationMin, stopDurationMax + 1)
                ticksUntilStop = Random.nextInt(stopIntervalMin, stopIntervalMax + 1)
            }
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        if (stopTicksLeft > 0 && event.packet is C03PacketPlayer) {
            event.cancelEvent()
        }
    }
}