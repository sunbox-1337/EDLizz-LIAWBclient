package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Items
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement

object AutoLava : Module("AutoLava", Category.COMBAT) {

    // 目标选择
    private val range by float("Range", 4f, 1f..8f)
    private val fov by float("FOV", 180f, 0f..180f)
    private val playersOnly by boolean("PlayersOnly", true)
    private val throughWalls by boolean("ThroughWalls", false)

    // 状态
    private var target: EntityLivingBase? = null
    private var placed = false
    private var stage = 0            // 0=空闲,1=已发旋转,2=已发放置,3=已恢复
    private var prevSlot = -1
    private var prevYaw = 0f
    private var prevPitch = 0f

    override fun onDisable() { reset() }

    private fun reset() {
        target = null
        placed = false
        stage = 0
        prevSlot = -1
    }

    val onMotion = handler<MotionEvent> { e ->
        if (e.eventState != EventState.PRE) return@handler
        val p = mc.thePlayer ?: return@handler
        if (p.isSpectator || !p.isEntityAlive) return@handler

        // 更新目标
        if (target == null || !isValid(target!!)) {
            target = findTarget()
            placed = false
            stage = 0
        }
        if (target == null) return@handler
        if (placed) return@handler // 每个目标只放一次

        when (stage) {
            0 -> {
                val lavaSlot = findLavaBucket()
                if (lavaSlot == -1) return@handler
                prevSlot = p.inventory.currentItem
                prevYaw = p.rotationYaw
                prevPitch = p.rotationPitch
                if (p.inventory.currentItem != lavaSlot) p.inventory.currentItem = lavaSlot

                val (yaw, pitch) = getRotations(target!!)
                sendPacket(C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, p.onGround))
                stage = 1
            }
            1 -> {
                p.heldItem?.let { sendPacket(C08PacketPlayerBlockPlacement(it)) }
                stage = 2
            }
            2 -> {
                sendPacket(C03PacketPlayer.C05PacketPlayerLook(prevYaw, prevPitch, p.onGround))
                if (prevSlot != -1 && p.inventory.currentItem != prevSlot) p.inventory.currentItem = prevSlot
                placed = true
                stage = 3
            }
        }
    }

    private fun findTarget(): EntityLivingBase? {
        val p = mc.thePlayer ?: return null
        var best: EntityLivingBase? = null
        var bestDist = range
        for (entity in mc.theWorld?.loadedEntityList ?: return null) {
            if (entity !is EntityLivingBase || entity == p || !entity.isEntityAlive) continue
            if (playersOnly && entity !is EntityPlayer) continue
            val dist = p.getDistanceToEntity(entity)
            if (dist > range) continue
            if (fov != 180f && RotationUtils.rotationDifference(entity) > fov) continue
            if (!throughWalls && !p.canEntityBeSeen(entity)) continue
            if (dist < bestDist) { bestDist = dist; best = entity }
        }
        return best
    }

    private fun isValid(entity: EntityLivingBase): Boolean {
        val p = mc.thePlayer ?: return false
        if (!entity.isEntityAlive) return false
        val dist = p.getDistanceToEntity(entity)
        if (dist > range) return false
        if (fov != 180f && RotationUtils.rotationDifference(entity) > fov) return false
        if (!throughWalls && !p.canEntityBeSeen(entity)) return false
        return true
    }

    private fun findLavaBucket(): Int {
        for (i in 0..8) {
            val stack = mc.thePlayer?.inventory?.getStackInSlot(i) ?: continue
            if (stack.item === Items.lava_bucket) return i
        }
        return -1
    }

    // 瞄准目标身体中心
    private fun getRotations(target: EntityLivingBase): Pair<Float, Float> {
        val p = mc.thePlayer ?: return 0f to 0f
        val dx = target.posX - p.posX
        val dy = (target.posY + target.height / 2) - (p.posY + p.eyeHeight)
        val dz = target.posZ - p.posZ
        val dist = Math.sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(Math.atan2(dz, dx)).toFloat() - 90f
        val pitch = (-Math.toDegrees(Math.atan2(dy, dist))).toFloat()
        return yaw to pitch
    }
}