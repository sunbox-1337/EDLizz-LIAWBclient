package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.ccbluex.liquidbounce.features.module.modules.render.InfoDisplay

object GhostMove : Module("GhostMove", Category.MOVEMENT) {
    private val mode by choices("Mode", arrayOf("Teleport", "Break"), "Teleport")
    private val stopAll by boolean("StopAllPackets", false)
    private val distance by float("Distance", 2f, 0.5f..10f) { mode == "Teleport" }

    private var blockC03 = false
    private var blockAll = false

    override fun onEnable() {
        blockC03 = true
        if (stopAll) blockAll = true
    }

    override fun onDisable() {
        // 执行动作（此时依然拦截发包，避免意外发送移动包）
        when (mode) {
            "Teleport" -> {
                val player = mc.thePlayer ?: return
                player.setPosition(player.posX, player.posY - distance, player.posZ)
            }
            "Break" -> {
                val player = mc.thePlayer ?: return
                player.rotationPitch = 90f  // 低头
                val posBelow = BlockPos(player.posX, player.posY - 1, player.posZ)
                if (!mc.theWorld.isAirBlock(posBelow)) {
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, posBelow, EnumFacing.UP)
                    )
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, posBelow, EnumFacing.UP)
                    )
                }
            }
        }
        // 恢复所有发包
        blockC03 = false
        blockAll = false
        InfoDisplay.showLog("try to move out！",2000)
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (blockC03 && packet is C03PacketPlayer) {
            event.cancelEvent()
        } else if (blockAll && packet !is C03PacketPlayer) {
            event.cancelEvent()
        }
    }
}