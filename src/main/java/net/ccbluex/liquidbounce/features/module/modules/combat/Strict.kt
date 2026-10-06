package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.C03PacketPlayer
import kotlin.math.atan2
import kotlin.math.sqrt

object Strict : Module("Strict", Category.COMBAT) {

    private val range by float("Range", 4f, 1f..6f)
    private val playersOnly by boolean("PlayersOnly", true)
    private val lockPitch by boolean("Pitch", true)

    // 缓存的最近目标
    private var nearestTarget: EntityLivingBase? = null

    /**
     * 每 Tick 寻找最近的有效目标
     */
    val onGameTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        nearestTarget = world.loadedEntityList
            .filter { it is EntityLivingBase && it != player && it.isEntityAlive }
            .filter { !playersOnly || it is EntityPlayer }
            .minByOrNull { player.getDistanceToEntityBox(it) }
            ?.let { it as EntityLivingBase }
    }

    /**
     * 发送移动包时，静默替换其中的视角
     */
    val onPacket = handler<PacketEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        val target = nearestTarget ?: return@handler

        // 检查目标是否仍在范围内
        if (player.getDistanceToEntityBox(target) > range) {
            nearestTarget = null
            return@handler
        }

        val packet = event.packet
        if (packet is C03PacketPlayer) {
            // 计算朝向目标的角度
            val diffX = target.posX - player.posX
            val diffY = target.posY + target.eyeHeight - (player.posY + player.eyeHeight)
            val diffZ = target.posZ - player.posZ
            val horizontalDist = sqrt(diffX * diffX + diffZ * diffZ)

            val targetYaw = Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90f
            val targetPitch = -Math.toDegrees(atan2(diffY, horizontalDist)).toFloat()

            // 替换包中的 yaw 和 pitch
            packet.yaw = targetYaw
            if (lockPitch) packet.pitch = targetPitch
        }
    }

    override val tag: String
        get() = "${range}m"
}