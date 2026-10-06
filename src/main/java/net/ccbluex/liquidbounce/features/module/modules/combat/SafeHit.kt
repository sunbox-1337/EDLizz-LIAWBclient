package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.searchCenter
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C0APacketAnimation
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object SafeHit : Module("SafeHit", Category.COMBAT) {

    // ---------- 目标选择 ----------
    private val range by float("Range", 3.5f, 1f..8f)
    private val fov by float("FOV", 180f, 0f..180f)
    private val playersOnly by boolean("PlayersOnly", true)
    private val throughWalls by boolean("ThroughWalls", false)

    // ---------- 攻击控制 ----------
    private val aimOnly by boolean("AimOnly", false)
    private val clickOnly by boolean("ClickOnly", true)  // 仅按住攻击键时工作
    private val attackDelayMin by int("AttackDelayMin", 8, 1..20)
    private val attackDelayMax by int("AttackDelayMax", 14, 1..20)
    private val swing by boolean("Swing", true)

    // ---------- 圆圈视觉 ----------
    private val drawCircle by boolean("DrawCircle", true)
    private val circleRadius by float("CircleRadius", 25f, 10f..500f) { drawCircle }
    private val circleColor by color("CircleColor", Color(255, 255, 255, 150)) { drawCircle }

    // ---------- 旋转设置 ----------
    private val options = RotationSettings(this).withoutKeepRotation()

    // ---------- 内部状态 ----------
    private var target: EntityLivingBase? = null
    private var attackTicks = 0
    private var originalRotation: Rotation? = null

    override fun onDisable() {
        target = null
        originalRotation = null
    }

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.PRE) return@handler

        val player = mc.thePlayer ?: return@handler
        if (player.isSpectator || !player.isEntityAlive) return@handler

        // ClickOnly 检查：未按住攻击键则清空目标并不做任何事
        if (clickOnly && !mc.gameSettings.keyBindAttack.isKeyDown) {
            target = null
            originalRotation = null
            return@handler
        }

        // 寻找目标
        val newTarget = findTarget()
        target = if (aimOnly) newTarget
        else (target?.takeIf { it.isEntityAlive && player.getDistanceToEntity(it) <= range && isSelected(it, true) } ?: newTarget)

        if (target == null) {
            originalRotation = null
            return@handler
        }

        if (aimOnly) {
            // 仅瞄准模式：持续锁定目标
            val rotation = searchCenter(
                target!!.entityBoundingBox,
                false, false, null, false,
                range, range,
                if (throughWalls) range else 0f,
                listOf("Body", "Body"), 0f..0f
            )
            if (rotation != null) {
                setTargetRotation(rotation, options, 1)
            }
            return@handler
        }

        // 自动攻击模式
        if (attackTicks > 0) {
            attackTicks--
            return@handler
        }

        // 保存原始视角
        originalRotation = currentRotation ?: Rotation(player.rotationYaw, player.rotationPitch)

        // 计算攻击旋转
        val rotation = searchCenter(
            target!!.entityBoundingBox,
            false, false, null, false,
            range, range,
            if (throughWalls) range else 0f,
            listOf("Body", "Body"), 0f..0f
        ) ?: return@handler

        // 转向敌人并攻击
        setTargetRotation(rotation, options, 1)
        doAttack(target!!)

        // 攻击后立即恢复原视角
        originalRotation?.let { setTargetRotation(it, options, 1) }
        originalRotation = null

        // 随机下一次攻击间隔（tick）
        attackTicks = Random.nextInt(attackDelayMin, attackDelayMax + 1)
    }

    // 绘制屏幕中央圆圈（同之前）
    val onRender2D = handler<Render2DEvent> {
        if (!drawCircle) return@handler
        val sr = net.minecraft.client.gui.ScaledResolution(mc)
        val cx = sr.scaledWidth / 2f
        val cy = sr.scaledHeight / 2f
        val r = circleRadius
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        GL11.glEnable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glLineWidth(2f)
        val color = circleColor
        GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        for (i in 0..60) {
            val angle = 2.0 * Math.PI * i / 60
            GL11.glVertex2f(cx + sin(angle).toFloat() * r, cy + cos(angle).toFloat() * r)
        }
        GL11.glEnd()
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_BLEND)
        GL11.glPopAttrib()
    }

    private fun findTarget(): EntityLivingBase? {
        val player = mc.thePlayer ?: return null
        var best: EntityLivingBase? = null
        var bestDist = range
        for (entity in mc.theWorld?.loadedEntityList ?: return null) {
            if (entity is EntityLivingBase && entity != player && entity.isEntityAlive) {
                if (playersOnly && entity !is EntityPlayer) continue
                if (!isSelected(entity, true)) continue
                val dist = player.getDistanceToEntity(entity)
                if (dist > range) continue
                if (fov != 180f && RotationUtils.rotationDifference(entity) > fov) continue
                if (!throughWalls && !player.canEntityBeSeen(entity)) continue
                if (dist < bestDist) { bestDist = dist; best = entity }
            }
        }
        return best
    }

    private fun doAttack(entity: EntityLivingBase) {
        val player = mc.thePlayer ?: return
        if (swing) {
            sendPacket(C0APacketAnimation())
            player.swingItem()
        }
        sendPacket(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))
    }
}