package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.PlayerTickEvent
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemAppleGold
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C01PacketChatMessage
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0EPacketClickWindow
import net.minecraft.network.play.server.S12PacketEntityVelocity
import java.awt.Color
import java.util.LinkedList

object AutoGapple : Module("AutoGapple", Category.COMBAT) {

    // 设置项
    private val stuck by boolean("Stuck", true)
    private val velStopStuck by boolean("StopStuckWhenFaceS12", true) { stuck }
    private val roxyStuck by boolean("RoxyStuck", true) { stuck }
    private val noMove by boolean("noMove", true)
    private val noJump by boolean("noJump", true)
    private val noCancelC02 by boolean("NoCancelC02", false)
    private val noCancelC0E by boolean("NoCancelC0E", false)
    private val slowrelease by boolean("SlowRelease", true)
    private val smart by boolean("SmartSlowRelease", true) { slowrelease }
    private val hurttime by int("SmartHurttime", 3, 0..9) { smart }
    private val colddown by int("ReleaseColdDown", 3, 0..10) { smart }
    private val maxTicks by int("ReleaseMaxTicks", 3, 0..10) { smart }
    private val delay by int("ReleaseDelay", 3, 1..10) { slowrelease }
    private val progressbar by boolean("ProgressBar", true)

    // 内部状态
    private val packets = LinkedList<Packet<*>>()
    private var c03s = 0
    private var stuckTime = 0
    private var skipStuck = false
    private var cd = 0
    private var lastTime = 0L
    private var skip = false
    private var skipTicks = 0  // 代替 StuckUtils 的跳跃帧数

    override fun onEnable() {
        if (stuck && roxyStuck) skipTicks = 1  // 简单卡住一帧
        lastTime = System.currentTimeMillis()
    }

    override fun onDisable() {
        release()
        skipTicks = 0
        skipStuck = false
        stuckTime = 0
        skip = false
    }

    override val tag: String
        get() = c03s.toString()

    private fun getGAppleSlot(): Int {
        for (i in 0..8) {
            val stack = mc.thePlayer?.inventory?.getStackInSlot(i) ?: continue
            if (stack.item is ItemAppleGold) return i
        }
        return -1
    }

    private fun release() {
        while (packets.isNotEmpty()) {
            val packet = packets.poll()
            if (packet !is C01PacketChatMessage && packet !is C09PacketHeldItemChange) {
                PacketUtils.sendPacket(packet, false)
            }
        }
        c03s = 0
    }

    // 计算与目标的距离（代替 distanceToEntityBox 扩展）
    private fun distanceToEntityBox(entity: EntityLivingBase): Double {
        val player = mc.thePlayer ?: return Double.MAX_VALUE
        val dx = player.posX - entity.posX
        val dy = player.posY - entity.posY
        val dz = player.posZ - entity.posZ
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is S12PacketEntityVelocity && packet.entityID == mc.thePlayer?.entityId && velStopStuck && stuck) {
            skipStuck = true  // 受到击退时停止卡住
        }
        if (event.eventType == EventState.RECEIVE) return@handler
        if (packet is C01PacketChatMessage) return@handler
        if (packet is C07PacketPlayerDigging || packet is C08PacketPlayerBlockPlacement) return@handler
        if ((packet is C02PacketUseEntity || packet is C0APacketAnimation) && noCancelC02) return@handler
        if (packet is C0EPacketClickWindow && noCancelC0E) return@handler
        if (packet is C03PacketPlayer) {
            if (skip) {
                skip = false
                return@handler
            }
            c03s++
        }
        packets.add(packet)
        event.cancelEvent()
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        if (noMove) {
            event.originalInput.moveStrafe = 0f
            event.originalInput.moveForward = 0f
        } else {
            event.originalInput.moveStrafe *= 0.2f
            event.originalInput.moveForward *= 0.2f
        }
        if (noJump) event.originalInput.jump = false
    }

    val onPlayerTick = handler<PlayerTickEvent> { event ->
        if (skipTicks > 0) {
            event.cancelEvent()
            skipTicks--
        }
    }

    val onGameTick = handler<GameTickEvent> {
        val slot = getGAppleSlot()
        if (slot == -1) {
            toggle()
            return@handler
        }

        // 卡住逻辑：如果 stuck 且非 roxyStuck，则停止移动18 ticks
        if (!roxyStuck && stuck) {
            if (stuckTime < 18) {
                if (!skipStuck) {
                    skipTicks = 1  // 保持卡住
                    stuckTime++
                }
            } else if (skipStuck) {
                skipStuck = false
            } else {
                stuckTime = 0
            }
        }

        if (c03s >= 32) {
            PacketUtils.sendPacket(C09PacketHeldItemChange(slot), false)
            PacketUtils.sendPacket(
                C08PacketPlayerBlockPlacement(mc.thePlayer?.inventoryContainer?.getSlot(slot + 36)?.stack),
                false
            )
            release()
            PacketUtils.sendPacket(C09PacketHeldItemChange(mc.thePlayer?.inventory?.currentItem ?: 0), false)
            val time = System.currentTimeMillis() - lastTime
            mc.thePlayer?.sendChatMessage("Eaten Gapple Time: $time")
            lastTime = System.currentTimeMillis()
            skip = true
        } else if (!noCancelC02) {
            // 慢速释放
            if (slowrelease && !smart && mc.thePlayer?.ticksExisted?.rem(delay) == 0) {
                while (packets.isNotEmpty()) {
                    val p = packets.poll()
                    if (p is C01PacketChatMessage) continue
                    if (p is C03PacketPlayer) c03s--
                    PacketUtils.sendPacket(p, false)
                }
            }
            if (slowrelease && smart) {
                if (cd >= colddown) {
                    var attack = false
                    var b = false
                    var ticks = 0
                    val player = mc.thePlayer ?: return@handler
                    for (p in packets) {
                        if (ticks >= maxTicks) break
                        if (p is C01PacketChatMessage) ticks++
                        if (p is C02PacketUseEntity) {
                            val entity = p.getEntityFromWorld(mc.theWorld)
                            if (entity is EntityLivingBase && entity.hurtTime <= hurttime &&
                                distanceToEntityBox(entity) <= 6.0
                            ) {
                                attack = true
                                break
                            }
                        }
                    }
                    if (attack) {
                        while (packets.isNotEmpty()) {
                            val p = packets.poll()
                            if (p is C01PacketChatMessage && b) break
                            if (p is C01PacketChatMessage) continue
                            if (p is C02PacketUseEntity) {
                                val entity = p.getEntityFromWorld(mc.theWorld)
                                if (entity is EntityLivingBase && entity.hurtTime <= hurttime &&
                                    distanceToEntityBox(entity) <= 6.0
                                ) {
                                    b = true
                                }
                            }
                            if (p is C03PacketPlayer) c03s--
                            PacketUtils.sendPacket(p, false)
                        }
                        cd = 0
                    } else {
                        cd++
                    }
                } else {
                    cd++
                }
            }
        }
    }

    val onRender2D = handler<Render2DEvent> {
        if (!progressbar) return@handler
        val sr = ScaledResolution(mc)
        val width = sr.scaledWidth.toFloat()
        val height = sr.scaledHeight.toFloat()
        drawGrimProgressBar(width, height)
    }

    private fun drawGrimProgressBar(width: Float, height: Float) {
        val progressLength = 140f
        val startY = height / 4f * 3f
        val startX = width / 2f - progressLength / 2f
        val progressRatio = (c03s / 32f).coerceIn(0f, 1f)
        val currentProgress = progressLength * progressRatio
        val percent = (progressRatio * 100).toInt()
        // 简单阴影（用黑色描边替代发光）
        RenderUtils.drawRoundedRect(
            startX - 2f,
            startY - 2f,
            startX + progressLength + 2f,
            startY + 9f,
            Color(0, 0, 0, 80).rgb,
            2f
        )
        RenderUtils.drawRoundedRect(startX, startY, startX + progressLength, startY + 7f, Color(0, 0, 0, 128).rgb, 2f)
        if (currentProgress != 0f) {
            RenderUtils.drawRoundedGradientRectCorner(
                startX, startY, startX + currentProgress, startY + 7f, 3f,
                Color(76, 157, 240).rgb, Color(53, 200, 167).rgb
            )
        }
        Fonts.fontGoogleSans35.drawString("$percent%", startX + progressLength + 5f, startY, Color.WHITE.rgb, true)
    }
}