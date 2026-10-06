package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.gui.ScaledResolution
import java.awt.Color

object ClientDetector : Module("ClientDetector", Category.MISC) {

    var FF = 1 // 神权模式开关

    // 服务器返回的在线玩家列表（由主程序更新）
    val onlineClientNames = mutableSetOf<String>()

    @Volatile
    var clientId: String = ""

    // ---------- 显示面板设置 ----------
    private val displayPanel by boolean("DisplayPanel", true)
    private val panelX by int("PanelX", 5, 0..1000) { displayPanel }
    private val panelY by int("PanelY", 5, 0..1000) { displayPanel }
    private val panelWidth by int("PanelWidth", 160, 50..400) { displayPanel }
    private val panelCornerRadius by float("PanelCornerRadius", 8f, 0f..20f) { displayPanel }
    private val panelBackgroundColor by color("PanelBackgroundColor", Color(0, 0, 0, 160)) { displayPanel }
    private val panelShadowColor by color("PanelShadowColor", Color(0, 0, 0, 255)) { displayPanel }
    private val panelShadowStrength by float("PanelShadowStrength", 4f, 0f..10f) { displayPanel }
    private val panelShadowMask by boolean("PanelShadowMask", true) { displayPanel }
    private val panelBlur by boolean("PanelBlur", true) { displayPanel }

    // 渲染面板
    val onRender2D = handler<Render2DEvent> {
        if (!displayPanel) return@handler

        val lines = synchronized(onlineClientNames) { onlineClientNames.toList() }
        drawPanel(
            x = panelX.toFloat(),
            y = panelY.toFloat(),
            width = panelWidth.toFloat(),
            cornerRadius = panelCornerRadius,
            backgroundColor = panelBackgroundColor,
            shadowColor = panelShadowColor,
            shadowStrength = panelShadowStrength,
            shadowMask = panelShadowMask,
            blur = panelBlur,
            title = "SameClient Players(global)",
            lines = lines
        )
    }

    private fun drawPanel(
        x: Float, y: Float, width: Float, cornerRadius: Float,
        backgroundColor: Color, shadowColor: Color, shadowStrength: Float,
        shadowMask: Boolean, blur: Boolean, title: String, lines: List<String>
    ) {
        val sr = ScaledResolution(mc)
        val lineHeight = Fonts.minecraftFont.FONT_HEIGHT + 2f
        val height = lineHeight * (lines.size + 1) + 8f

        if (blur && BlurSettings.active && BlurSettings.clientDetector) {
            val scale = sr.scaleFactor.toFloat()
            BlurUtils.drawOffsetBlur(
                x * scale, y * scale,
                width * scale, height * scale,
                samples = BlurSettings.passes,
                strength = 0f,
                radius = cornerRadius * scale
            )
        }

        if (shadowStrength > 0) {
            GlowUtils.drawGlow(
                x, y, width, height,
                blurRadius = (shadowStrength * 2f).toInt(),
                color = shadowColor,
                cornerRadius = cornerRadius,
                onlyBorder = false,
                mask = shadowMask
            )
        }

        drawRoundedRect(x, y, x + width, y + height, backgroundColor.rgb, cornerRadius)

        Fonts.minecraftFont.drawString(title, x + 5f, y + 4f, Color.WHITE.rgb, true)
        var drawY = y + 4f + lineHeight
        for (line in lines) {
            Fonts.minecraftFont.drawString(line, x + 5f, drawY, Color.WHITE.rgb, true)
            drawY += lineHeight
        }
    }
}