/*
 * 目标加速（TargetBoost）—— 移植自 MoonLight Speed 的 EntityCollide 模式。
 *
 * 原理：1.9+ 实体碰撞会互相推挤（MCPK：Entities now push the player when colliding）。
 * 统计自己碰撞箱（可选扩大）范围内的活体目标数量，按数量朝视角方向加冲量：
 * boost = strength * collisions。挤的目标越多冲得越快，
 * 在 Grim/1.9+ 服务器上是纯物理结果，不直接改 motion，难以归因。
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import kotlin.math.cos
import kotlin.math.sin

object TargetBoost : SpeedMode("TargetBoost") {

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return
        if (!player.isMoving) return

        val expand = if (Speed.targetBoostExpand) 1.0 else 0.8
        val box = player.entityBoundingBox.expand(expand, expand, expand)

        var collisions = 0
        for (entity in world.loadedEntityList) {
            if (entity === player || entity !is EntityLivingBase || entity is EntityArmorStand) continue
            if (box.intersectsWith(entity.entityBoundingBox)) collisions++
        }
        if (collisions <= 0) return

        // 朝当前（服务器侧）视角方向加冲量，与 MoonLight 一致
        val yaw = Math.toRadians(
            (RotationUtils.currentRotation?.yaw ?: player.rotationYaw).toDouble()
        )
        val boost = Speed.targetBoostStrength * collisions
        player.addVelocity(-sin(yaw) * boost, 0.0, cos(yaw) * boost)
    }
}
