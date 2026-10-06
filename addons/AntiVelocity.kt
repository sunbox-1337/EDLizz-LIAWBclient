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

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.ClientUtils.chat
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.movement.StuckUtils
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.serverRotation
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import kotlin.math.pow

object AntiVelocity : Module("AntiVelocity", Category.ADDONS) {

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
    private var skipTicks = 0
    private var lastHurtTime = 0
    private var targetEntity: Entity? = null
    private var lastAttackReach = 0.0
    private var cancelC0FTicks = 0

    override fun onDisable() {
        needVelocity = false
    }

    override val tag: String
        get() = mode

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        when {
            packet is S12PacketEntityVelocity && packet.entityId == mc.thePlayer?.entityId -> {
                when (mode) {
                    "GrimNoXZ" -> {
                        targetEntity = if (rayCast) {
                            raycastEntity(6.0, serverRotation.yaw, serverRotation.pitch) {
                                it is EntityLivingBase && it !is EntityArmorStand
                            }
                        } else KillAura.target
                        targetEntity?.let { entity ->
                            val dist = mc.thePlayer!!.distanceToEntityBox(entity)
                            if (dist > reach) targetEntity = null
                            else lastAttackReach = dist
                        }
                        needVelocity = true
                    }
                    "Grim1.17C06" -> {
                        val player = mc.thePlayer ?: return@handler
                        sendPacket(C03PacketPlayer.C06PacketPlayerPosLook(
                            player.posX, player.posY, player.posZ,
                            player.rotationYaw, player.rotationPitch, player.onGround
                        ))
                        val pos = BlockPos(player).up()
                        sendPacket(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, pos, EnumFacing.DOWN))
                        sendPacket(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, EnumFacing.DOWN))
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
                chat("Detect Lag AttackReach: $lastAttackReach")
            }
        }
    }

    val onGameTick = handler<GameTickEvent> {
        if (mode == "GrimNoXZ") {
            val player = mc.thePlayer ?: return@handler
            val wasSprinting = player.isSprinting
            if (needVelocity && player.hurtTime != 0 && targetEntity != null) {
                if (!wasSprinting && legitSprint) {
                    StuckUtils.setSkipTicks(1)
                    sendPacket(C03PacketPlayer(player.onGround))
                }
                if (!wasSprinting) {
                    sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SPRINTING))
                    if (setSprint) {
                        player.isSprinting = true
                        player.serverSprintState = true
                    }
                }
                repeat(c02s) {
                    if (BlinkUtils.isBlinking) {
                        BlinkUtils.packets.add(C0APacketAnimation())
                        BlinkUtils.packets.add(C02PacketUseEntity(targetEntity, C02PacketUseEntity.Action.ATTACK))
                    } else {
                        sendPacket(C0APacketAnimation())
                        sendPacket(C02PacketUseEntity(targetEntity, C02PacketUseEntity.Action.ATTACK))
                    }
                }
                if (!wasSprinting && stopSprint) {
                    sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SPRINTING))
                }
                player.motionX *= 0.6.pow(reduceMotion)
                player.motionZ *= 0.6.pow(reduceMotion)
            }
            needVelocity = false
        } else if (mode == "Grim1.17C06" && needVelocity) {
            val player = mc.thePlayer ?: return@handler
            sendPacket(C03PacketPlayer.C06PacketPlayerPosLook(
                player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch, player.onGround
            ))
            val pos = BlockPos(player).up()
            sendPacket(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, pos, EnumFacing.DOWN))
            sendPacket(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, EnumFacing.DOWN))
            needVelocity = false
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        if (mode == "GrimReduce") {
            val player = mc.thePlayer ?: return@handler
            if (player.hurtTime == 0) needVelocity = false
            if (needVelocity && player.hurtTime > 5 && player.hurtTime != lastHurtTime && player.onGround) {
                player.tryJump()
                chat("Player Jump")
            }
            if (!player.isMoving || !player.isSprinting) return@handler
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

    val onPlayerTick = handler<PlayerTickEvent> { event ->
        if (skipTicks > 0) {
            event.cancelEvent()
            skipTicks--
        }
    }
}