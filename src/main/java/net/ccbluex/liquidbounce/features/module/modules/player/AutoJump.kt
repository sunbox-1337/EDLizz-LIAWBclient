package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.util.BlockPos

object AutoJump : Module("AutoJump", Category.MOVEMENT) {

    // 是否只在按住前进键时触发
    private val onlyForward by boolean("OnlyForward", true)

    val onGameTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        // 必须疾跑且未蹲下
        if (!player.isSprinting || player.isSneaking) return@handler
        // 可选：仅在前方向键按下时触发
        if (onlyForward && !mc.gameSettings.keyBindForward.isKeyDown) return@handler

        val world = mc.theWorld ?: return@handler
        // 检测头顶一格（玩家位置 Y + 2）是否有方块
        val headBlockPos = BlockPos(player.posX, player.posY + 2.0, player.posZ)

        // 如果头顶不是空气且玩家在地面上，则自动跳跃
        if (!world.isAirBlock(headBlockPos) && player.onGround) {
            player.jump()
        }
    }

    override val tag: String?
        get() = if (onlyForward) "Forward" else "Always"
}