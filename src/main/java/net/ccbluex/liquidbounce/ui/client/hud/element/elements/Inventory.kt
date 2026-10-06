/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.inventory.inventorySlot
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect2
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting
import net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting
import org.lwjgl.opengl.GL11.*
import java.awt.Color

@ElementInfo(name = "Inventory")
class Inventory : Element("Inventory", 300.0, 50.0) {

    private val font by font("Font", Fonts.fontSemibold35)
    private val title by choices("Title", arrayOf("Center", "Left", "Right", "None"), "Left")
    private val titleColor = color("TitleColor", Color.WHITE) { title != "None" }
    private val roundedRectRadius by float("Rounded-Radius", 3F, 0F..20F)

    private val borderValue by boolean("Border", true)
    private val borderColor = color("BorderColor", Color.WHITE) { borderValue }
    private val backgroundColor by color("BackgroundColor", Color.BLACK.withAlpha(150))
    private val lizzBar by boolean("${CLIENT_NAME}Bar", true)

    // ===== 阴影设置 =====
    private val Shadow by boolean("Shadow", false)
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { Shadow }
    private val shadowStrength by float("ShadowStrength", 1F, 1F..2F) { Shadow }
    private val shadowSpread by float("ShadowSpread", 1F, 0.5F..3F) { Shadow }
    private val shadowOnlyBorder by boolean("ShadowOnlyBorder", false) { Shadow }
    private val shadowMask by boolean("ShadowMask", true) { Shadow && !shadowOnlyBorder }

    private val width = 174F
    private val height = 66F
    private val padding = 6F

    override fun drawElement(): Border {
        val font = font
        val startY = if (title != "None") -(padding + font.FONT_HEIGHT) else 0F
        val borderColor = borderColor.selectedColor()
        val titleColor = titleColor.selectedColor()

        // ========== 模糊背景 ==========
        if (BlurSettings.active && BlurSettings.inventory) {
            val sr = ScaledResolution(mc)
            val screenScale = sr.scaleFactor.toFloat()
            val elementScale = this.scale
            val blurX = ((renderX + 0f) * elementScale * screenScale).toFloat()
            val blurY = ((renderY + startY) * elementScale * screenScale).toFloat()
            val blurW = (width * elementScale * screenScale)
            val blurH = ((height - startY) * elementScale * screenScale)
            BlurUtils.drawOffsetBlur(
                blurX, blurY, blurW, blurH,
                samples = BlurSettings.passes,
                strength = 0f,
                radius = roundedRectRadius * elementScale * screenScale
            )
        }

        // ========== 阴影（在背景矩形之前绘制，使用 GlowUtils + mask） ==========
        // 面板实际范围是 startY..height（title 等选项会改变 startY），高度按实际计算，避免写死导致的裁切错位
        ShowShadow(0F, startY, width, height - startY)

        // ========== 背景矩形 ==========
        drawRoundedRect2(0F, startY, width, height, backgroundColor, roundedRectRadius)

        // Reset color
        resetColor()
        glColor4f(1F, 1F, 1F, 1F)

        val invDisplayName = mc.thePlayer.inventory.displayName.formattedText
        if (lizzBar) drawRoundedRect(
            0F, startY, width, 4F,
            Color(77, 139, 199, 120).rgb,
            roundedRectRadius,
            RenderUtils.RoundedCorners.TOP_ONLY
        )
        when (title.lowercase()) {
            "center" -> font.drawString(
                invDisplayName,
                width / 2 - font.getStringWidth(invDisplayName) / 2F,
                -(font.FONT_HEIGHT).toFloat(),
                titleColor.rgb,
                false
            )

            "left" -> font.drawString(invDisplayName, padding, -(font.FONT_HEIGHT).toFloat(), titleColor.rgb, false)
            "right" -> font.drawString(
                invDisplayName,
                width - padding - font.getStringWidth(invDisplayName),
                -(font.FONT_HEIGHT).toFloat(),
                titleColor.rgb,
                false
            )
        }

        // render items
        enableGUIStandardItemLighting()
        renderInv(9, 17, 6, 6, font)
        renderInv(18, 26, 6, 24, font)
        renderInv(27, 35, 6, 42, font)
        disableStandardItemLighting()
        enableAlpha()
        disableBlend()
        disableLighting()

        return Border(0F, startY, width, height)
    }

    private fun renderInv(slot: Int, endSlot: Int, x: Int, y: Int, font: FontRenderer) {
        var xOffset = x
        for (i in slot..endSlot) {
            xOffset += 18
            val stack = mc.thePlayer.inventorySlot(i).stack ?: continue

            if (mc.currentScreen is GuiHudDesigner) glDisable(GL_DEPTH_TEST)

            mc.renderItem.renderItemAndEffectIntoGUI(stack, xOffset - 18, y)
            mc.renderItem.renderItemOverlays(font, stack, xOffset - 18, y)

            if (mc.currentScreen is GuiHudDesigner) glEnable(GL_DEPTH_TEST)
        }
    }

    /**
     * 阴影绘制：使用 GlowUtils.drawGlow 并传入 mask 实现内部裁切
     */
    private fun ShowShadow(startX: Float, startY: Float, width: Float, height: Float) {
        if (!Shadow) return

        val baseBlur = (shadowStrength * 13F).toInt()
        val blurRadius = (baseBlur * shadowSpread).toInt().coerceAtLeast(1)

        GlowUtils.drawGlow(
            startX,
            startY,
            width,
            height,
            blurRadius,
            shadowColor,
            cornerRadius = roundedRectRadius,
            onlyBorder = shadowOnlyBorder,
            mask = shadowMask
        )
    }
}