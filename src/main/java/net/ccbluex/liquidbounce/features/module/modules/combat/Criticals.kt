/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C0APacketAnimation

object Criticals : Module("Criticals", Category.COMBAT) {

    val mode by choices(
        "Mode",
        arrayOf(
            "Packet",
            "NCPPacket",
            "BlocksMC",
            "BlocksMC2",
            "NoGround",
            "Hop",
            "TPHop",
            "Jump",
            "LowJump",
            "CustomMotion",
            "Visual",
            "DownInAttack"
        ),
        "Packet"
    )

    val delay by int("Delay", 0, 0..500)
    private val hurtTime by int("HurtTime", 10, 0..10)
    private val customMotionY by float("Custom-Y", 0.2f, 0.01f..0.42f) { mode == "CustomMotion" }
    private val isInAir by boolean("onlyInAir", false) { mode == "Packet" }

    // DownInAttack 专用偏移量（微调越高越易暴击，越低越隐蔽）
    private val downOffset by float("DownOffset", 0.0625f, 0.0001f..0.42f) { mode == "DownInAttack" }

    val msTimer = MSTimer()
    private var processing = false   // 防止递归

    override fun onEnable() {
        if (mode == "NoGround")
            mc.thePlayer.tryJump()
    }

    val onAttack = handler<AttackEvent> { event ->
        if (event.targetEntity is EntityLivingBase) {
            val thePlayer = mc.thePlayer ?: return@handler
            val entity = event.targetEntity as EntityLivingBase

            if (!thePlayer.onGround || thePlayer.isOnLadder || thePlayer.isInWeb || thePlayer.isInLiquid ||
                thePlayer.ridingEntity != null || entity.hurtTime > hurtTime ||
                Fly.handleEvents() || !msTimer.hasTimePassed(delay)
            )
                return@handler

            val (x, y, z) = thePlayer

            when (mode.lowercase()) {
                "packet" -> {
                    if (isInAir && thePlayer.onGround) return@handler
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.0625, z, true),
                        C04PacketPlayerPosition(x, y, z, false)
                    )
                    thePlayer.onCriticalHit(entity)
                }

                "ncppacket" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.11, z, false),
                        C04PacketPlayerPosition(x, y + 0.1100013579, z, false),
                        C04PacketPlayerPosition(x, y + 0.0000013579, z, false)
                    )
                    thePlayer.onCriticalHit(entity)
                }

                "blocksmc" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.001091981, z, true),
                        C04PacketPlayerPosition(x, y, z, false)
                    )
                }

                "blocksmc2" -> {
                    if (thePlayer.ticksExisted % 4 == 0) {
                        sendPackets(
                            C04PacketPlayerPosition(x, y + 0.0011, z, true),
                            C04PacketPlayerPosition(x, y, z, false)
                        )
                    }
                }

                "hop" -> {
                    thePlayer.motionY = 0.1
                    thePlayer.fallDistance = 0.1f
                    thePlayer.onGround = false
                }

                "tphop" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.02, z, false),
                        C04PacketPlayerPosition(x, y + 0.01, z, false)
                    )
                    thePlayer.setPosition(x, y + 0.01, z)
                }

                "jump" -> thePlayer.motionY = 0.42
                "lowjump" -> thePlayer.motionY = 0.3425
                "custommotion" -> thePlayer.motionY = customMotionY.toDouble()
                "visual" -> thePlayer.onCriticalHit(entity)
            }

            msTimer.reset()
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is C03PacketPlayer && mode == "NoGround") {
            packet.onGround = false
        }

        // DownInAttack 模式：拦截攻击包，插入微小跳跃发包
        if (mode.lowercase() == "downinattack" && !processing) {
            if (packet is C02PacketUseEntity && packet.action == C02PacketUseEntity.Action.ATTACK) {
                val thePlayer = mc.thePlayer ?: return@handler
                val rawEntity = packet.getEntityFromWorld(mc.theWorld) ?: return@handler
                if (rawEntity !is EntityLivingBase) return@handler
                val entity = rawEntity as EntityLivingBase

                if (!thePlayer.onGround || thePlayer.isOnLadder || thePlayer.isInWeb || thePlayer.isInLiquid ||
                    thePlayer.ridingEntity != null || entity.hurtTime > hurtTime ||
                    Fly.handleEvents() || !msTimer.hasTimePassed(delay)
                ) return@handler

                processing = true

                val (x, y, z) = thePlayer
                val offset = downOffset

                // 取消原始攻击包
                event.cancelEvent()

                // 1. 瞬间抬高（onGround=true），2. 落回原地（onGround=false），制造下落距离
                sendPacket(C04PacketPlayerPosition(x, y + offset, z, true))
                sendPacket(C04PacketPlayerPosition(x, y, z, false))

                // 3. 发送挥动动画和攻击包
                sendPacket(C0APacketAnimation())
                sendPacket(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))

                // 4. 客户端暴击效果
                thePlayer.onCriticalHit(entity)

                msTimer.reset()
                processing = false
            }
        }
    }

    override val tag
        get() = mode
}