/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.Side
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.LBPPAnimationUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.resources.I18n
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.util.concurrent.CopyOnWriteArrayList

/**
 * CustomHUD potion effects element
 *
 * 由 render 分类的 PotionEffect 模块移植而来：一排带图标 / 名称 / 剩余时间的药水方块，
 * 支持自定义配色、整块模糊（BlurSettings → PotionEffects）以及可裁切的阴影。
 *
 * 方块以元素原点 (0, 0) 为左上角、向右下堆叠。
 */
@ElementInfo(name = "PotionEffects")
class PotionEffects(
    x: Double = 4.0, y: Double = 60.0, scale: Float = 1F,
    side: Side = Side(Side.Horizontal.LEFT, Side.Vertical.UP)
) : Element("PotionEffects", x, y, scale, side) {

    // ===== 布局 =====
    private val boxWidth by float("Box-Width", 120F, 60F..240F)
    private val boxHeight by float("Box-Height", 32F, 16F..64F)
    private val spacing by float("Spacing", 5F, 0F..16F)
    private val rectRadius by float("Rect-Radius", 5F, 0F..20F)
    private val animationSpeed by float("Animation-Speed", 0.15F, 0.01F..0.5F)

    // ===== 配色 =====
    private val backgroundColor by color("BackgroundColor", Color(40, 40, 40, 80))
    private val borderColor by color("BorderColor", Color(255, 255, 255, 0))
    private val borderStrength by float("Border-Strength", 1F, 0F..4F)

    private val potionColorName by boolean("Name-UsesPotionColor", true)
    private val nameColor by color("NameColor", Color(255, 255, 255)) { !potionColorName }
    private val durationColor by color("DurationColor", Color(255, 255, 255, 200))
    private val warnEnabled by boolean("Duration-Warn", true)
    private val warnColor by color("Duration-WarnColor", Color(255, 80, 80)) { warnEnabled }
    private val warnSeconds by int("Duration-WarnSeconds", 10, 1..60) { warnEnabled }

    private val iconEnabled by boolean("Icon", true)
    private val amplifierEnabled by boolean("Amplifier", true)

    // ===== 模糊 / 阴影 =====
    // 模糊的总开关在 BlurSettings（Enabled + PotionEffects 两个都要开）
    private val shadow by boolean("Shadow", false)
    private val shadowStrength by float("Shadow-Strength", 1F, 0.5F..2F) { shadow }
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { shadow }
    private val shadowMask by boolean("ShadowMask", true) { shadow }

    private val inventoryTexture = ResourceLocation("textures/gui/container/inventory.png")
    private val activePotions = CopyOnWriteArrayList<AnimatedPotion>()

    override fun drawElement(): Border {
        val player = mc.thePlayer ?: return Border(0F, 0F, 0F, 0F)

        updatePotionList(player.activePotionEffects.filter { Potion.potionTypes[it.potionID] != null })

        activePotions.forEach { it.update(animationSpeed) }
        activePotions.removeIf { it.isReadyToRemove() }
        activePotions.sortBy { it.effect.duration }

        if (activePotions.isEmpty()) return Border(0F, 0F, 0F, 0F)

        // 竖直堆叠：行高随出场动画收缩，行距也随之收拢
        val tops = ArrayList<Float>(activePotions.size)
        var cursor = 0F
        for (potion in activePotions) {
            tops += cursor
            cursor += (boxHeight + spacing) * potion.animationY
        }
        val totalHeight = (cursor - spacing).coerceAtLeast(0F)

        val rects = ArrayList<FloatArray>(activePotions.size)
        for (i in activePotions.indices) {
            val top = tops[i]
            val bottom = top + boxHeight * activePotions[i].animationY
            if (bottom - top >= 1F) rects += floatArrayOf(0F, top, boxWidth, bottom)
        }
        if (rects.isEmpty()) return Border(0F, 0F, 0F, 0F)

        // 模糊与阴影都按「整块」处理：相邻方块之间不会出现接缝/互相渗透
        drawBlur(rects, totalHeight)
        drawShadow(rects)

        for (r in rects) drawBackground(r)

        assumeNonVolatile {
            for (i in activePotions.indices) activePotions[i].drawContent(tops[i])
        }

        return Border(0F, 0F, boxWidth, totalHeight)
    }

    /** 整块模糊；用每个方块的矩形当模板遮罩，只保留真正有背景的区域。 */
    private fun drawBlur(rects: List<FloatArray>, totalHeight: Float) {
        if (!BlurSettings.active || !BlurSettings.potionEffects) return

        // HUD: glScalef(scale) 再 glTranslated(renderX, renderY) → 局部点 L 的屏幕位置是 (renderX + L) * scale
        val screenScale = ScaledResolution(mc).scaleFactor.toFloat()
        val elementScale = scale * screenScale

        val blurRects = ArrayList<FloatArray>(rects.size)
        for (r in rects) {
            blurRects += floatArrayOf(
                ((renderX + r[0]) * elementScale).toFloat(),
                ((renderY + r[1]) * elementScale).toFloat(),
                ((renderX + r[2]) * elementScale).toFloat(),
                ((renderY + r[3]) * elementScale).toFloat()
            )
        }

        BlurUtils.drawOffsetBlur(
            (renderX * elementScale).toFloat(),
            (renderY * elementScale).toFloat(),
            boxWidth * elementScale,
            totalHeight * elementScale,
            samples = BlurSettings.passes,
            strength = 0f,
            radius = rectRadius * elementScale
        )
    }

    private fun drawShadow(rects: List<FloatArray>) {
        if (!shadow || shadowColor.alpha <= 0) return

        GlowUtils.drawCompositeGlow(
            rects,
            (shadowStrength * 13F).toInt(),
            shadowColor,
            mask = shadowMask,
            cornerRadius = rectRadius
        )
    }

    private fun drawBackground(r: FloatArray) {
        val bg = backgroundColor
        if (bg.alpha <= 0) return

        if (borderStrength > 0F && borderColor.alpha > 0) {
            RenderUtils.drawRoundedBorderRect(
                r[0], r[1], r[2], r[3], borderStrength, bg.rgb, borderColor.rgb, rectRadius
            )
        } else {
            drawRoundedRect(r[0], r[1], r[2], r[3], bg.rgb, rectRadius)
        }
    }

    /** 把列表和玩家的实际效果同步：新效果入列，消失的效果标记为退场。 */
    private fun updatePotionList(playerEffects: List<PotionEffect>) {
        activePotions.forEach { animated ->
            if (playerEffects.none { it.potionID == animated.effect.potionID }) {
                animated.isMarkedForRemoval = true
            }
        }

        playerEffects.forEach { playerEffect ->
            val existing = activePotions.find { it.effect.potionID == playerEffect.potionID }
            if (existing == null) {
                activePotions += AnimatedPotion(playerEffect)
            } else {
                // 效果被重新施加时刷新实例
                existing.effect = playerEffect
            }
        }
    }

    /** 单个药水方块的状态与入场/退场动画。 */
    private inner class AnimatedPotion(var effect: PotionEffect) {
        var isMarkedForRemoval = false

        /** 水平滑入：从左侧屏幕外滑到 0 */
        private var animationX = -boxWidth - 10F

        /** 高度展开：0 → 1 */
        var animationY = 0F
            private set

        fun update(speed: Float) {
            val targetX = if (isMarkedForRemoval) -boxWidth - 10F else 0F
            val targetY = if (isMarkedForRemoval) 0F else 1F

            animationX = LBPPAnimationUtils.animate(targetX, animationX, speed)
            animationY = LBPPAnimationUtils.animate(targetY, animationY, speed)
        }

        fun isReadyToRemove() = isMarkedForRemoval && animationX <= -boxWidth

        fun drawContent(top: Float) {
            val potion = Potion.potionTypes[effect.potionID] ?: return

            val alpha = animationY
            val left = animationX
            val bottom = top + boxHeight * alpha
            if (alpha <= 0F || bottom - top < 1F) return

            glPushMatrix()

            // 高度展开时把内容裁掉，避免文字/图标溢出正在生长的方块
            RenderUtils.makeScissorBox(
                ((renderX + left) * scale).toFloat(),
                ((renderY + top) * scale).toFloat(),
                ((renderX + left + boxWidth) * scale).toFloat(),
                ((renderY + bottom) * scale).toFloat()
            )
            glEnable(GL_SCISSOR_TEST)

            if (iconEnabled && potion.hasStatusIcon()) {
                mc.textureManager.bindTexture(inventoryTexture)
                GlStateManager.color(1F, 1F, 1F, alpha)

                val iconX = potion.statusIconIndex % 8 * 18
                val iconY = 198 + potion.statusIconIndex / 8 * 18
                RenderUtils.drawTexturedModalRect(
                    (left + 8F).toInt(),
                    (top + (boxHeight * alpha - 18F) / 2F).toInt(),
                    iconX, iconY, 18, 18, 0F
                )

                GlStateManager.color(1F, 1F, 1F, 1F)
            }

            val textX = left + 30F
            val textY = top + (boxHeight * alpha / 2F) - 8F

            val potionName = I18n.format(potion.name) +
                    if (amplifierEnabled && effect.amplifier > 0) " ${effect.amplifier + 1}" else ""

            val nameArgb = if (potionColorName) {
                val potionColor = Color(potion.liquidColor, true)
                Color(
                    potionColor.red, potionColor.green, potionColor.blue,
                    (255F * alpha).toInt()
                ).rgb
            } else {
                Color(
                    nameColor.red, nameColor.green, nameColor.blue,
                    (nameColor.alpha * alpha).toInt()
                ).rgb
            }
            Fonts.fontSemibold40.drawString(potionName, textX, textY, nameArgb)

            val seconds = effect.duration / 20
            val durationText = String.format("%02d:%02d", seconds / 60, seconds % 60)
            val base = if (warnEnabled && seconds <= warnSeconds) warnColor else durationColor
            val durationArgb = Color(
                base.red, base.green, base.blue,
                (base.alpha * alpha).toInt()
            ).rgb
            Fonts.fontSemibold35.drawString(durationText, textX, textY + 11F, durationArgb)

            glDisable(GL_SCISSOR_TEST)
            glPopMatrix()
        }
    }
}
