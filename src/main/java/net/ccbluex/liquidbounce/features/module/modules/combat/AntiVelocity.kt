package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import kotlin.math.pow

object AntiVelocity : Module("AntiVelocity", Category.MISC) {

    val mode by choices(
        "Mode", arrayOf(
            "GrimNoXZ",
            "GrimReduce",
            "Grim1.17C06",
            "CancelC0F",
            "MatrixReduce"
        ), "GrimNoXZ"
    )

    val c02s by int("GrimNoXZC02Counts", 5, 1..16) { mode == "GrimNoXZ" }
    val rayCast by boolean("GrimNoXZRayCast", true) { mode == "GrimNoXZ" }
    val reach by float("GrimNoXZReach", 3.2f, 3.0f..6.0f) { mode == "GrimNoXZ" }
    val legitSprint by boolean("GrimNoXZLegitSprint", false) { mode == "GrimNoXZ" }
    val stopSprint by boolean("GrimNoXZStopSprint", true) { mode == "GrimNoXZ" }
    val setSprint by boolean("GrimNoXZSetSprint", false) { mode == "GrimNoXZ" }
    val reduceMotion by int("GrimNoXZMotion", 5, 1..16) { mode == "GrimNoXZ" }
    val lagDebug by boolean("GrimNoXZLagDebug", true) { mode == "GrimNoXZ" }
    val cancelC0FCounts by int("CancelC0FCounts", 6, 1..16) { mode == "CancelC0F" }

    private var needVelocity = false
    private var lastHurtTime = 0
    private var targetEntity: Entity? = null
    private var lastAttackReach = 0.0
    private var cancelC0FTicks = 0
    private var skipTicks = 0   // 代替 StuckUtils 的空中停住帧数

    override fun onDisable() {
        needVelocity = false
    }

    override val tag: String
        get() = mode

    // 用原版 getDistanceToEntity 替代扩展函数
    private fun getDistanceToEntityBox(entity: Entity): Double {
        return mc.thePlayer?.getDistanceToEntity(entity)?.toDouble() ?: Double.MAX_VALUE
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        when {
            packet is S12PacketEntityVelocity && packet.entityID == mc.thePlayer?.entityId -> {
                when (mode) {
                    "GrimNoXZ" -> {
                        targetEntity = if (rayCast) {
                            RaycastUtils.raycastEntity(
                                6.0,
                                RotationUtils.serverRotation.yaw,
                                RotationUtils.serverRotation.pitch
                            ) {
                                it is EntityLivingBase && it !is EntityArmorStand
                            }
                        } else KillAura.target
                        targetEntity?.let { entity ->
                            val dist = getDistanceToEntityBox(entity)
                            if (dist > reach) targetEntity = null
                            else lastAttackReach = dist
                        }
                        needVelocity = true
                    }
                    "Grim1.17C06" -> {
                        val player = mc.thePlayer ?: return@handler
                        PacketUtils.sendPacket(
                            C03PacketPlayer.C06PacketPlayerPosLook(
                                player.posX, player.posY, player.posZ,
                                player.rotationYaw, player.rotationPitch, player.onGround
                            )
                        )
                        val pos = BlockPos(player).up()
                        PacketUtils.sendPacket(
                            C07PacketPlayerDigging(
                                C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK,
                                pos,
                                EnumFacing.DOWN
                            )
                        )
                        PacketUtils.sendPacket(
                            C07PacketPlayerDigging(
                                C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                                pos,
                                EnumFacing.DOWN
                            )
                        )
                        needVelocity = true
                        event.cancelEvent()
                    }
                    "CancelC0F" -> {
                        cancelC0FTicks = cancelC0FCounts
                        event.cancelEvent()
                    }
                    "GrimReduce" -> needVelocity = true
                    "MatrixReduce" -> {
                        packet.motionX = (packet.motionX * 0.33).toInt()
                        packet.motionZ = (packet.motionZ * 0.33).toInt()
                        if (mc.thePlayer?.onGround == true) {
                            packet.motionX = (packet.motionX * 0.86).toInt()
                            packet.motionZ = (packet.motionZ * 0.86).toInt()
                        }
                    }
                }
            }
            cancelC0FTicks > 0 && packet is C0FPacketConfirmTransaction -> {
                event.cancelEvent()
                cancelC0FTicks--
            }
            packet is S08PacketPlayerPosLook && lagDebug -> {
                mc.thePlayer?.sendChatMessage("Detect Lag AttackReach: $lastAttackReach")
            }
        }
    }

    val onGameTick = handler<GameTickEvent> {
        if (mode == "GrimNoXZ") {
            val player = mc.thePlayer ?: return@handler
            val wasSprinting = player.isSprinting
            if (needVelocity && player.hurtTime != 0 && targetEntity != null) {
                if (!wasSprinting && legitSprint) {
                    skipTicks = 1   // 空中停住一帧，不发送移动包
                    PacketUtils.sendPacket(C03PacketPlayer(player.onGround))
                }
                if (!wasSprinting) {
                    PacketUtils.sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SPRINTING))
                    if (setSprint) {
                        player.isSprinting = true
                        player.serverSprintState = true
                    }
                }
                repeat(c02s) {
                    PacketUtils.sendPacket(C0APacketAnimation())
                    PacketUtils.sendPacket(C02PacketUseEntity(targetEntity, C02PacketUseEntity.Action.ATTACK))
                }
                if (!wasSprinting && stopSprint) {
                    PacketUtils.sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SPRINTING))
                }
                player.motionX *= 0.6.pow(reduceMotion)
                player.motionZ *= 0.6.pow(reduceMotion)
            }
            needVelocity = false
        } else if (mode == "Grim1.17C06" && needVelocity) {
            val player = mc.thePlayer ?: return@handler
            PacketUtils.sendPacket(
                C03PacketPlayer.C06PacketPlayerPosLook(
                    player.posX, player.posY, player.posZ,
                    player.rotationYaw, player.rotationPitch, player.onGround
                )
            )
            val pos = BlockPos(player).up()
            PacketUtils.sendPacket(
                C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK,
                    pos,
                    EnumFacing.DOWN
                )
            )
            PacketUtils.sendPacket(
                C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                    pos,
                    EnumFacing.DOWN
                )
            )
            needVelocity = false
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        if (mode == "GrimReduce") {
            val player = mc.thePlayer ?: return@handler
            if (player.hurtTime == 0) needVelocity = false
            if (needVelocity && player.hurtTime > 5 && player.hurtTime != lastHurtTime && player.onGround) {
                player.jump()          // 替换 tryJump
                mc.thePlayer?.sendChatMessage("Player Jump")
            }
            // 替换 isMoving
            val isMoving = player.moveForward != 0f || player.moveStrafing != 0f
            if (!isMoving || !player.isSprinting) return@handler
            if (player.hurtTime != lastHurtTime) {
                when (player.hurtTime) {
                    9 -> player.motionX *= 0.8.also { player.motionZ *= 0.8 }
                    8 -> player.motionX *= 0.11.also { player.motionZ *= 0.11 }
                    7 -> player.motionX *= 0.4.also { player.motionZ *= 0.4 }
                    4 -> player.motionX *= 0.37.also { player.motionZ *= 0.37 }
                }
                lastHurtTime = player.hurtTime
            }
        }
    }

    // 空中停住逻辑：取消 PlayerTickEvent 以达到不发送移动包的效果
    val onPlayerTick = handler<PlayerTickEvent> { event ->
        if (skipTicks > 0) {
            event.cancelEvent()
            skipTicks--
        }
    }
}