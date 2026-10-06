package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.text.SimpleDateFormat
import java.util.*

object InfoDisplay : Module("InfoDisplay", Category.RENDER, defaultHidden = true) {
    // 遮罩位置偏移（相对于屏幕中心）
    private val offsetX by int("OffsetX", 0, -300..300)
    private val offsetY by int("OffsetY", 0, -300..300)

    // 遮罩大小（0-300）
    private val rectWidth by int("Width", 300, 100..600)
    private val rectHeight by int("Height", 80, 10..300)

    // 遮罩背景颜色与不透明度
    private val backgroundColor by color("BackgroundColor", Color(0, 0, 0, 160))
    private val opacity by int("Opacity", 160, 0..255)

    // 阴影设置
    private val shadow by boolean("Shadow", true)
    private val shadowStrength by float("ShadowStrength", 4f, 0f..10f) { shadow }
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { shadow }

    // 日志文字颜色
    private val textColor by color("TextColor", Color.WHITE)

    // ===== 日志状态 =====
    private var currentLog: LogEntry? = null
    private val timeFormatter = SimpleDateFormat("HH:mm:ss")

    /**
     * 显示一条日志（仅当模块已启用时生效，否则忽略）
     * @param text 日志内容
     * @param duration 显示时长（毫秒），默认 5000
     */
    fun showLog(text: String, duration: Long = 5000L) {
        if (!state) return  // 模块未开启时直接忽略
        currentLog = LogEntry(text, System.currentTimeMillis(), duration)
    }

    override fun onEnable() {
        currentLog = null
    }

    override fun onDisable() {
        currentLog = null
    }

    val onRender = handler<Render2DEvent> {
        val entry = currentLog ?: return@handler

        // 检查日志是否过期
        val now = System.currentTimeMillis()
        if (now - entry.timestamp > entry.duration) {
            currentLog = null
            return@handler
        }

        // 计算遮罩位置
        val sr = ScaledResolution(mc)
        val centerX = sr.scaledWidth / 2f
        val centerY = sr.scaledHeight / 2f
        val x = centerX - rectWidth / 2f + offsetX
        val y = centerY - rectHeight / 2f + offsetY

        val bgColor = Color(backgroundColor.red, backgroundColor.green, backgroundColor.blue, opacity)

        // 绘制阴影
        if (shadow) {
            GlowUtils.drawGlow(
                x, y,
                rectWidth.toFloat(), rectHeight.toFloat(),
                (shadowStrength * 3).toInt(),
                shadowColor
            )
        }

        // 绘制半透明背景矩形（覆盖内部阴影）
        drawRect(x, y, x + rectWidth, y + rectHeight, bgColor.rgb)

        // 计算文字透明度（最后 1 秒淡出）
        val elapsed = now - entry.timestamp
        val remaining = (entry.duration - elapsed).coerceAtLeast(0L)
        val alpha = if (remaining < 1000L) (remaining / 1000f).coerceIn(0f, 1f) else 1f
        val finalTextColor = Color(
            textColor.red,
            textColor.green,
            textColor.blue,
            (textColor.alpha * alpha).toInt()
        )

        // 准备时间前缀
        val timeStr = "[${timeFormatter.format(Date(entry.timestamp))}]"
        val fullText = "$timeStr ${entry.text}"

        // 计算文字位置：垂直居中，水平左对齐（留出边距）
        val textX = x + 10f
        val textY = y + (rectHeight - Fonts.fontRegular35.FONT_HEIGHT) / 2f

        // 绘制文字
        Fonts.fontRegular35.drawString(
            fullText,
            textX,
            textY,
            finalTextColor.rgb,
            true // 阴影
        )
    }

    /** 绘制纯色矩形 */
    private fun drawRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
        worldRenderer.pos(left.toDouble(), bottom.toDouble(), 0.0).color(r, g, b, a).endVertex()
        worldRenderer.pos(right.toDouble(), bottom.toDouble(), 0.0).color(r, g, b, a).endVertex()
        worldRenderer.pos(right.toDouble(), top.toDouble(), 0.0).color(r, g, b, a).endVertex()
        worldRenderer.pos(left.toDouble(), top.toDouble(), 0.0).color(r, g, b, a).endVertex()
        tessellator.draw()

        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
    }
}

/** 日志条目 */
private data class LogEntry(
    val text: String,
    val timestamp: Long,
    val duration: Long
)