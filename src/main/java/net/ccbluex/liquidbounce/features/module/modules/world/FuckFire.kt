package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.minecraft.block.BlockFire
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import kotlin.math.roundToInt

object FuckFire : Module("FuckFire", Category.WORLD) {

    // 扫描半径
    private val range by float("Range", 3.0f, 1.0f..6.0f)

    // 旋转设置：默认服务器端旋转，instant 可能默认 true，若不满足可后续调整
    private val rotationSettings = RotationSettings(this)

    private var lastAttackTick = 0

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        if (player.ticksExisted - lastAttackTick < 2) return@handler

        val firePos = findNearestFire(player, world) ?: return@handler

        val center = Vec3(firePos.x + 0.5, firePos.y + 0.5, firePos.z + 0.5)
        val rotation = RotationUtils.toRotation(center)

        // 设置服务器端旋转（静默）
        setTargetRotation(rotation, rotationSettings)

        sendPacket(C0APacketAnimation())
        sendPacket(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, firePos, EnumFacing.DOWN))
        sendPacket(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, firePos, EnumFacing.DOWN))

        lastAttackTick = player.ticksExisted
    }

    private fun findNearestFire(player: EntityPlayer, world: WorldClient): BlockPos? {
        val radius = range.roundToInt()
        var closestPos: BlockPos? = null
        var closestDistSq = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = BlockPos(player.posX + x, player.posY + y, player.posZ + z)
                    val blockState = world.getBlockState(pos)
                    if (blockState.block is BlockFire) {
                        val distSq = player.getDistanceSq(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq
                            closestPos = pos
                        }
                    }
                }
            }
        }

        return closestPos
    }
}