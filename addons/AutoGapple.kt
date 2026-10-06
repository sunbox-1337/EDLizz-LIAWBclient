/*
 * NekoBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/RouQingNeko1024/NekoBounce
 * Code By GoldBounce,Lizz,NightSky,FDP
 * https://github.com/SkidderMC/FDPClient
 * https://github.com/qm123pz/NightSky-Client
 * https://github.com/bzym2/GoldBounce/
 */
//SKID SuperBounce
//By NekoBanka
package net.ccbluex.liquidbounce.features.module.modules.addons

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts.fontGoogleSans35
import net.ccbluex.liquidbounce.utils.client.ClientUtils.chat
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.ViaPacket
import net.ccbluex.liquidbounce.utils.extensions.distanceToEntityBox
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.movement.StuckUtils
import net.ccbluex.liquidbounce.utils.render.GlowUtils.drawGlow
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedGradientRectCorner
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemAppleGold
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.S12PacketEntityVelocity
import java.awt.Color
import java.util.*

object AutoGapple : Module("AutoGapple", Category.ADDONS) {

    val stuck by boolean("Stuck", true)
    val velStopStuck by boolean("StopStuckWhenFaceS12", true) { stuck }
    val roxyStuck by boolean("RoxyStuck", true) { stuck }
    val noMove by boolean("noMove", true)
    val noJump by boolean("noJump", true)
    val noCancelC02 by boolean("NoCancelC02", false)
    val noCancelC0E by boolean("NoCancelC0E", false)
    val slowrelease by boolean("SlowRelease", true)
    val smart by boolean("SmartSlowRelease", true) { slowrelease }
    val hurttime by int("SmartHurttime", 3, 0..9) { smart }
    val colddown by int("ReleaseColdDown", 3, 0..10) { smart }
    val maxTicks by int("ReleaseMaxTicks", 3, 0..10) { smart }
    val delay by int("ReleaseDelay", 3, 1..10) { slowrelease }
    val progressbar by boolean("ProgressBar", true)

    private val packets = LinkedList<Packet<*>>()
    private var c03s = 0
    private var stuckTime = 0
    private var skipStuck = false
    private var cd = 0
    private var lastTime = 0L
    private var skip = false

    override fun onEnable() {
        if (stuck && roxyStuck) StuckUtils.stuck()
        lastTime = System.currentTimeMillis()
    }

    override fun onDisable() {
        release()
        StuckUtils.stopStuck()
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
                sendPacket(packet, false)
            }
        }
        c03s = 0
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is S12PacketEntityVelocity && packet.entityId == mc.thePlayer?.entityId && velStopStuck && stuck) {
            if (roxyStuck) StuckUtils.moveTicks++ else skipStuck = true
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

    val onTickEnd = handler<TickEndEvent> {
        packets.add(C01PacketChatMessage("test"))
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

    val onMoveMath = handler<MoveMathEvent> {
        if (!roxyStuck && stuck) {
            if (stuckTime < 18) {
                if (!skipStuck) {
                    it.cancelEvent()
                    stuckTime++
                }
            } else if (skipStuck) {
                skipStuck = false
            } else {
                stuckTime = 0
            }
        }
    }

    val onGameTick = handler<GameTickEvent> {
        val slot = getGAppleSlot()
        if (slot == -1) {
            toggle()
            return@handler
        }
        if (c03s >= 32) {
            sendPacket(C09PacketHeldItemChange(slot), false)
            sendPacket(C08PacketPlayerBlockPlacement(mc.thePlayer?.inventoryContainer?.getSlot(slot + 36)?.stack), false)
            if (ViaLoadingBase.getInstance().targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_9)) {
                ViaPacket.sendOffHandUseItem()
            }
            release()
            sendPacket(C09PacketHeldItemChange(mc.thePlayer?.inventory?.currentItem ?: 0), false)
            val time = System.currentTimeMillis() - lastTime
            chat("Eaten Gapple Time: $time")
            lastTime = System.currentTimeMillis()
            skip = true
        } else if (!noCancelC02) {
            if (slowrelease && !smart && mc.thePlayer?.ticksExisted?.rem(delay) == 0) {
                while (packets.isNotEmpty()) {
                    val p = packets.poll()
                    if (p is C01PacketChatMessage) continue
                    if (p is C03PacketPlayer) c03s--
                    sendPacket(p, false)
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
                            val entity = p.getEntity(mc.theWorld)
                            if (entity is EntityLivingBase && entity.hurtTime <= hurttime &&
                                player.distanceToEntityBox(entity) <= 6.0
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
                                val entity = p.getEntity(mc.theWorld)
                                if (entity is EntityLivingBase && entity.hurtTime <= hurttime &&
                                    player.distanceToEntityBox(entity) <= 6.0
                                ) {
                                    b = true
                                }
                            }
                            if (p is C03PacketPlayer) c03s--
                            sendPacket(p, false)
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
        showGrimShadow(startX - 2f, startY - 2f, progressLength + 4f, 11f, 0.3f)
        drawRoundedRect(startX, startY, startX + progressLength, startY + 7f, Color(0, 0, 0, 128).rgb, 2f)
        if (currentProgress != 0f) {
            drawRoundedGradientRectCorner(
                startX, startY, startX + currentProgress, startY + 7f, 3f,
                Color(76, 157, 240).rgb, Color(53, 200, 167).rgb
            )
        }
        fontGoogleSans35.drawString("$percent%", startX + progressLength + 5f, startY, Color.WHITE.rgb, true)
    }

    private fun showGrimShadow(x: Float, y: Float, w: Float, h: Float, strength: Float) {
        drawGlow(x, y, w, h, (strength * 13f).toInt(), Color(0, 0, 0, 120))
    }
}