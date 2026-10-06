/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.event.async.loopSequence
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.component1
import net.ccbluex.liquidbounce.utils.extensions.component2
import net.ccbluex.liquidbounce.utils.extensions.component3
import net.ccbluex.liquidbounce.utils.extensions.eyes
import net.ccbluex.liquidbounce.utils.render.RenderUtils.draw2D
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawBlockBox
import net.minecraft.block.BlockChest
import net.minecraft.network.play.server.S24PacketBlockAction
import net.minecraft.network.play.server.S29PacketSoundEffect
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.BlockPos
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object ChestESP : Module("ChestESP", Category.RENDER) {
    private val mode by choices("Mode", arrayOf("Box", "2D"), "Box")
    private val radius by int("Radius", 40, 5..120)
    private val blockLimit by int("BlockLimit", 256, 0..2056)

    private val normalColor by color("NormalColor", Color(255, 179, 72))
    private val openColor by color("OpenColor", Color(0, 255, 0))

    private val chestPositions = ConcurrentHashMap.newKeySet<BlockPos>()
    private val openChests = ConcurrentHashMap.newKeySet<BlockPos>()

    override fun onDisable() {
        chestPositions.clear()
        openChests.clear()
    }

    val onSearch = loopSequence(dispatcher = Dispatchers.Default) {
        val (x, y, z) = mc.thePlayer?.eyes ?: return@loopSequence
        val radiusSq = radius * radius

        // 移除超出范围或不再是箱子的位置
        chestPositions.removeIf {
            it.distanceSqToCenter(x, y, z) >= radiusSq ||
                    mc.theWorld.getTileEntity(it) !is TileEntityChest
        }
        // 打开标记也同步清理：只保留仍然存在的箱子
        openChests.removeIf { !chestPositions.contains(it) }

        // 遍历所有 TileEntity 搜索箱子（普通箱子和陷阱箱子）
        val world = mc.theWorld ?: return@loopSequence
        for (te in world.tickableTileEntities) {
            if (te is TileEntityChest && !chestPositions.contains(te.pos)) {
                val distanceSq = te.pos.distanceSqToCenter(x, y, z)
                if (distanceSq <= radiusSq && chestPositions.size < blockLimit) {
                    chestPositions += te.pos
                }
            }
        }

        delay(1000)
    }

    // 监听方块动作包（箱子开合）和声音事件
    val onPacket = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is S24PacketBlockAction -> {
                // 只在箱子打开时永久标记，不因关闭而移除
                if (packet.data2 == 1) {
                    openChests.add(packet.blockPosition)
                }
            }

            is S29PacketSoundEffect -> {
                if (packet.soundName == "random.chestopen") {
                    val pos = BlockPos(packet.x, packet.y, packet.z)
                    if (mc.theWorld.getTileEntity(pos) is TileEntityChest) {
                        openChests.add(pos)
                    }
                }
            }
        }
    }

    val onRender3D = handler<Render3DEvent> {
        when (mode) {
            "Box" -> chestPositions.forEach {
                val color = if (openChests.contains(it)) openColor else normalColor
                drawBlockBox(it, color, true)
            }
            "2D" -> chestPositions.forEach {
                val color = if (openChests.contains(it)) openColor.rgb else normalColor.rgb
                draw2D(it, color, Color.BLACK.rgb)
            }
        }
    }
}