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

import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.serverRotation
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.item.EntityBoat
import net.minecraft.entity.item.EntityMinecart
import net.minecraft.entity.projectile.EntityFishHook
import net.minecraft.util.AxisAlignedBB

object EntitySpeed : Module("SpeedNew", Category.ADDONS) {

    val mode by choices("Mode", arrayOf("EntityCollide", "Grim"), "EntityCollide")
    val speed by int("Speed", 5, 1..8) { mode == "EntityCollide" || mode == "Grim" }

    override val tag: String
        get() = mode

    private fun getMoveYaw(yaw: Double): Double {
        var result = yaw
        if (mc.thePlayer?.moveForward ?: 0f < 0f) result += 180.0
        var forward = 1.0
        if (mc.thePlayer?.moveForward ?: 0f < 0f) forward = -0.5
        else if (mc.thePlayer?.moveForward ?: 0f > 0f) forward = 0.5
        if (mc.thePlayer?.moveStrafing ?: 0f > 0f) result -= 90.0 * forward
        if (mc.thePlayer?.moveStrafing ?: 0f < 0f) result += 90.0 * forward
        return result
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler
        when (mode) {
            "EntityCollide" -> {
                if (player.moveForward == 0f && player.moveStrafing == 0f) return@handler
                var count = 0
                for (entity in mc.theWorld.loadedEntityList) {
                    if (entity is EntityArmorStand) continue
                    if (entity !== player && entity is EntityLivingBase &&
                        player.entityBoundingBox.expand(1.0, 1.0, 1.0)
                            .intersectsWith(entity.entityBoundingBox)
                    ) count++
                }
                val yaw2 = Math.toRadians(getMoveYaw(serverRotation.yaw))
                val boost = speed * 0.01 * count
                player.addVelocity(-Math.sin(yaw2) * boost, 0.0, Math.cos(yaw2) * boost)
            }
            "Grim" -> {
                val playerBox = player.entityBoundingBox.expand(1.0, 1.0, 1.0)
                var count = 0
                for (entity in mc.theWorld.loadedEntityList) {
                    if (entity is EntityLivingBase || entity is EntityBoat || entity is EntityMinecart || entity is EntityFishHook) {
                        if (entity is EntityArmorStand) continue
                        if (entity.entityId == player.entityId) continue
                        if (!playerBox.intersectsWith(entity.entityBoundingBox)) continue
                        if (entity.entityId == -8 || entity.entityId == -1337) continue
                        count++
                    }
                }
                if (count > 0 && player.isMoving) {
                    val strafeOffset = minOf(count, 4) * speed * 0.01
                    val yaw = getMoveYaw(serverRotation.yaw).toFloat()
                    val mx = -Math.sin(Math.toRadians(yaw.toDouble()))
                    val mz = Math.cos(Math.toRadians(yaw.toDouble()))
                    player.addVelocity(mx * strafeOffset, 0.0, mz * strafeOffset)
                    if (count < 4 && KillAura.target != null && mc.gameSettings.keyBindSprint.isKeyDown) {
                        mc.gameSettings.keyBindSprint.pressed = true
                        return@handler
                    }
                }
                mc.gameSettings.keyBindSprint.pressed = mc.gameSettings.keyBindSprint.isKeyDown
            }
        }
    }
}