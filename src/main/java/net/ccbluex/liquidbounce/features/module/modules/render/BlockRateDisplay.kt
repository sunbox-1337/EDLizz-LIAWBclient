package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemSword
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.awt.Color

object BlockRateDisplay : Module("BlockRateDisplay", Category.RENDER) {

    private var myTotalAttacks = 0
    private var mySuccessfulBlocks = 0
    private var myLastHurtTime = 0
    private var myLastAttackTime = 0L

    private var enemyTotalAttacks = 0
    private var enemySuccessfulBlocks = 0
    private var pendingAttackTarget: EntityLivingBase? = null
    private var lastEnemyHurtTime = 0

    private val posX by int("PosX", 0, -500..500)
    private val posY by int("PosY", 0, -500..500)
    private val bgColor by color("BackgroundColor", Color(0, 0, 0, 180))
    private val myBarColor by color("MyBarColor", Color(0, 255, 0))
    private val enemyBarColor by color("EnemyBarColor", Color(255, 100, 0))
    private val borderColor by color("BorderColor", Color.WHITE)
    private val cornerRadius by float("CornerRadius", 6f, 0f..15f)

    private val shadow by boolean("Shadow", true)
    private val shadowStrength by float("ShadowStrength", 12f, 0f..25f) { shadow }
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { shadow }
    private val shadowSpread by float("ShadowSpread", 1F, 0.5F..3F) { shadow }
    private val shadowOnlyBorder by boolean("ShadowOnlyBorder", false) { shadow }
    private val shadowMask by boolean("ShadowMask", true) { shadow && !shadowOnlyBorder }

    private val autoReset by boolean("AutoReset", false)
    private val resetDelay by int("ResetDelay", 10, 1..60) { autoReset }

    override fun onEnable() {
        resetMyStats()
        resetEnemyStats()
        MinecraftForge.EVENT_BUS.register(this)
    }

    override fun onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this)
        resetMyStats()
        resetEnemyStats()
    }

    private fun resetMyStats() {
        myTotalAttacks = 0
        mySuccessfulBlocks = 0
        myLastHurtTime = 0
        myLastAttackTime = 0L
    }

    private fun resetEnemyStats() {
        enemyTotalAttacks = 0
        enemySuccessfulBlocks = 0
        pendingAttackTarget = null
        lastEnemyHurtTime = 0
    }

    @SubscribeEvent
    fun onLivingHurt(event: LivingHurtEvent) {
        if (event.entityLiving === mc.thePlayer) {
            recordMyBlock()
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        val target = event.targetEntity as? EntityLivingBase ?: return@handler
        if (target == mc.thePlayer) return@handler

        if (target != pendingAttackTarget) {
            enemyTotalAttacks = 0
            enemySuccessfulBlocks = 0
            lastEnemyHurtTime = 0
        }
        pendingAttackTarget = target
        lastEnemyHurtTime = target.hurtTime
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler

        if (!player.isDead) {
            val current = player.hurtTime
            if (current > 0 && (myLastHurtTime == 0 || current > myLastHurtTime)) {
                recordMyBlock()
            }
            myLastHurtTime = current
        } else {
            myLastHurtTime = 0
        }

        val enemy = pendingAttackTarget
        if (enemy != null && !enemy.isDead) {
            val currentHurt = enemy.hurtTime
            if (currentHurt > 0 && (lastEnemyHurtTime == 0 || currentHurt > lastEnemyHurtTime)) {
                enemyTotalAttacks++

                if (enemy is EntityPlayer && enemy.isUsingItem && enemy.heldItem?.item is ItemSword) {
                    enemySuccessfulBlocks++
                }
                pendingAttackTarget = null
            }
            lastEnemyHurtTime = currentHurt
        } else {
            resetEnemyStats()
        }

        if (autoReset && myLastAttackTime > 0 &&
            System.currentTimeMillis() - myLastAttackTime > resetDelay * 1000L) {
            resetMyStats()
        }
    }

    private fun recordMyBlock() {
        val player = mc.thePlayer ?: return
        myTotalAttacks++
        myLastAttackTime = System.currentTimeMillis()

        if (player.isUsingItem && player.heldItem?.item is ItemSword) {
            mySuccessfulBlocks++
            return
        }
        val ka = LiquidBounce.moduleManager.getModule(KillAura::class.java) as? KillAura
        if (ka != null && ka.isCurrentlyBlocking) {
            mySuccessfulBlocks++
        }
    }

    val onRender2D = handler<Render2DEvent> {
        val sr = ScaledResolution(mc)
        val x = sr.scaledWidth / 2f + posX
        val y = sr.scaledHeight / 2f + posY

        val barWidth = 130f
        val barHeight = 12f
        val textGap = 4f
        val rowHeight = barHeight + 10f + textGap
        val padTop = 8f
        val padBottom = 8f
        val bgWidth = 170f
        val bgHeight = padTop + rowHeight + padBottom

        val bgX = x - bgWidth / 2f
        val bgY = y - bgHeight / 2f
        val radius = cornerRadius

        if (BlurSettings.active && BlurSettings.blockRateDisplay) {
            val scale = sr.scaleFactor.toFloat()
            BlurUtils.drawOffsetBlur(
                bgX * scale, bgY * scale,
                bgWidth * scale, bgHeight * scale,
                BlurSettings.passes, 0f, radius * scale
            )
        }

        if (shadow) {
            GlowUtils.drawGlow(
                bgX, bgY, bgWidth, bgHeight,
                (shadowStrength * shadowSpread).toInt(),
                shadowColor,
                cornerRadius = radius,
                onlyBorder = shadowOnlyBorder,
                mask = shadowMask
            )
        }

        RenderUtils.drawRoundedRect(bgX, bgY, bgX + bgWidth, bgY + bgHeight, bgColor.rgb, radius)
        RenderUtils.drawRoundedBorderRect(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 1.5f, borderColor.rgb, 0, radius)

        fun drawRow(offsetY: Float, label: String, ratio: Float, current: Int, total: Int, color: Color) {
            val textY = bgY + offsetY
            mc.fontRendererObj.drawString(
                "$label ${"%.1f".format(ratio * 100)}% ($current/$total)",
                bgX + 8f, textY, Color.WHITE.rgb, true
            )
            val barY = textY + 10f
            val barX = bgX + 8f
            val endX = bgX + bgWidth - 8f
            val barRadius = (radius * 0.5f).coerceAtLeast(0f)
            RenderUtils.drawRoundedRect(barX, barY, endX, barY + barHeight, Color(40, 40, 40).rgb, barRadius)
            if (ratio > 0f) {
                val fillWidth = (endX - barX) * ratio
                RenderUtils.drawRoundedRect(barX, barY, barX + fillWidth, barY + barHeight, color.rgb, barRadius)
            }
        }

        val myRatio = if (myTotalAttacks > 0) mySuccessfulBlocks.toFloat() / myTotalAttacks else 0f
        val enemyRatio = if (enemyTotalAttacks > 0) enemySuccessfulBlocks.toFloat() / enemyTotalAttacks else 0f

        drawRow(padTop, "My Block Rate:", myRatio, mySuccessfulBlocks, myTotalAttacks, myBarColor)

    }
}