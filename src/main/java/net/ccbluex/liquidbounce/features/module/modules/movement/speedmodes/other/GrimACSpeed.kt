package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.riseskid.grimspeed.MoveUtil
import net.minecraft.entity.EntityLivingBase

object GrimACSpeed : SpeedMode("GrimAC") {

    override fun onStrafe() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        // 1. 碰撞加速：检测与附近生物的碰撞
        val boundingBoxSize = Speed.grimBoundingBoxSize.toDouble()
        val inPlayerSpeed = Speed.grimInPlayerSpeed.toDouble()

        val colliding = world.loadedEntityList
            .filter { it is EntityLivingBase && it != player }
            .any { entity ->
                player.entityBoundingBox
                    .expand(boundingBoxSize, boundingBoxSize, boundingBoxSize)
                    .intersectsWith(entity.entityBoundingBox)
            }

        if (colliding) {
            MoveUtil.moveFlying(inPlayerSpeed)
        }

        // 2. 无条件微小加速
        val moveFlyingIncrease = Speed.grimMoveFlyingIncrease.toDouble()
        MoveUtil.moveFlying(moveFlyingIncrease)
    }
}