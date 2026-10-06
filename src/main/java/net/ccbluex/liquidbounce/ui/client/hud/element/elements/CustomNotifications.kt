package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.ui.client.hud.HUD.notifications
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.Side
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.extensions.lerpWith
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawBorder
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawImage
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.max

@ElementInfo(name = "CustomNotifications", single = true, priority = -1)
class CustomNotifications(
    x: Double = 0.0, y: Double = 30.0, scale: Float = 1F, side: Side = Side(Side.Horizontal.RIGHT, Side.Vertical.DOWN)
) : Element("CustomNotifications", x, y, scale, side) {

    // ========== 可调节选项 ==========
    private val direction by choices("Direction", arrayOf("TopToBottom", "BottomToTop", "LeftToRight", "RightToLeft"), "RightToLeft")
    private val displayTime by int("DisplayTime", 3000, 500..10000)   // 通知停留时间（毫秒）
    private val padding by int("Padding", 5, -1..20)
    private val boxHeight by int("BoxHeight", 32, 20..60)
    private val backgroundColor by color("BackgroundColor", Color(0, 0, 0, 150))
    private val borderEnabled by boolean("Border", false)
    private val borderColor by color("BorderColor", Color.WHITE) { borderEnabled }
    private val borderWidth by float("BorderWidth", 1f, 0.5f..3f) { borderEnabled }

    private val showIcon by boolean("ShowIcon", true)
    private val iconSize by int("IconSize", 24, 12..36) { showIcon }

    private val shadowEnabled by boolean("Shadow", true)
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { shadowEnabled }
    private val glowStrength by float("GlowStrength", 6f, 0f..20f) { shadowEnabled }
    private val gradientShadowRadius by float("GradientShadowRadius", 8f, 0f..30f) { shadowEnabled }

    private val progressBarEnabled by boolean("ProgressBar", true)
    private val progressBarBackground by color("ProgressBarBackground", Color(100, 100, 100, 200)) { progressBarEnabled }
    private val progressBarColor by color("ProgressBarColor", Color.WHITE) { progressBarEnabled }
    private val progressBarHeight by float("ProgressBarHeight", 2f, 1f..4f) { progressBarEnabled }

    // ========== 动画状态 ==========
    private val spawnTime = mutableMapOf<Notification, Long>()
    private val animStartTime = mutableMapOf<Notification, Long>()
    private val animProgress = mutableMapOf<Notification, Float>()
    private val ANIM_DURATION = 200L   // 滑入/滑出动画时长（毫秒）

    override fun updateElement() {
        // 清理已不存在的通知状态
        animProgress.keys.removeIf { it !in notifications }
        spawnTime.keys.removeIf { it !in notifications }
        animStartTime.keys.removeIf { it !in notifications }
    }

    override fun drawElement(): Border? {
        val sr = ScaledResolution(mc)
        val screenScale = sr.scaleFactor.toFloat()
        val elemRenderX = renderX.toFloat()
        val elemRenderY = renderY.toFloat()
        val elemScale = this.scale

        // ========== HUD Designer 模式：显示示例并返回边界 ==========
        if (mc.currentScreen is GuiHudDesigner) {
            if (notifications.isEmpty()) {
                val ex = Notification("Example Title", "Example Description")
                notifications.add(ex)
                spawnTime[ex] = System.currentTimeMillis()
                animStartTime[ex] = System.currentTimeMillis()
                animProgress[ex] = 1f
            }

            // 计算总尺寸用于边界
            var totalWidth = 0f
            var totalHeight = 0f
            for (n in notifications) {
                val w = calculateWidth(n)
                val h = boxHeight + if (progressBarEnabled) progressBarHeight + 2f else 0f
                totalWidth = max(totalWidth, w)
                totalHeight += h
            }

            val borderX1 = if (side.horizontal == Side.Horizontal.LEFT) 0f else -totalWidth
            val borderX2 = if (side.horizontal == Side.Horizontal.LEFT) totalWidth else 0f
            val borderY1 = if (side.vertical == Side.Vertical.DOWN) -totalHeight else 0f
            val borderY2 = if (side.vertical == Side.Vertical.DOWN) 0f else totalHeight

            return Border(borderX1, borderY1, borderX2, borderY2)
        }

        // ========== 正常游戏模式 ==========
        val now = System.currentTimeMillis()
        var verticalOffset = 0f
        val notifHeight = boxHeight.toFloat()
        val fullHeight = notifHeight + if (progressBarEnabled) progressBarHeight + 2f else 0f

        for (notification in notifications.toList()) {
            // 初始化
            if (!spawnTime.containsKey(notification)) {
                spawnTime[notification] = now
                animStartTime[notification] = now
                animProgress[notification] = 0f
            }

            val spawn = spawnTime[notification]!!
            val elapsed = now - spawn

            // 计算目标进度：进入阶段 progress 0→1，停留阶段 1，退出阶段 1→0
            val targetProgress = if (elapsed < ANIM_DURATION) {
                1f  // 滑入中
            } else if (elapsed < displayTime + ANIM_DURATION) {
                1f  // 停留
            } else if (elapsed < displayTime + ANIM_DURATION * 2) {
                0f  // 滑出中
            } else {
                0f  // 已结束，将被移除
            }

            // 更新进度（线性）
            var current = animProgress[notification]!!
            if (current < targetProgress) {
                current = (current + deltaTime / ANIM_DURATION).coerceAtMost(1f)
            } else if (current > targetProgress) {
                current = (current - deltaTime / ANIM_DURATION).coerceAtLeast(0f)
            }
            animProgress[notification] = current

            // 移除已完成的通知
            if (elapsed >= displayTime + ANIM_DURATION * 2 && current <= 0f) {
                notifications.remove(notification)
                animProgress.remove(notification)
                spawnTime.remove(notification)
                animStartTime.remove(notification)
                continue
            }

            // 计算宽度
            val notifWidth = calculateWidth(notification)

            // 基础位置
            val baseY = if (side.vertical == Side.Vertical.DOWN) {
                -verticalOffset - notifHeight
            } else {
                verticalOffset
            }

            // 根据进度计算偏移
            val offsetX: Float
            val offsetY: Float
            when (direction.lowercase()) {
                "toptobottom" -> {
                    offsetX = 0f
                    offsetY = -fullHeight * (1f - current)
                }
                "bottomtotop" -> {
                    offsetX = 0f
                    offsetY = fullHeight * (1f - current)
                }
                "lefttoright" -> {
                    offsetX = -notifWidth * (1f - current)
                    offsetY = 0f
                }
                "righttoleft" -> {
                    offsetX = notifWidth * (1f - current)
                    offsetY = 0f
                }
                else -> {
                    offsetX = 0f
                    offsetY = 0f
                }
            }

            val drawX = offsetX
            val drawY = baseY + offsetY

            // ========== 阴影 ==========
            if (shadowEnabled) {
                GlowUtils.drawGlow(drawX, drawY, notifWidth, notifHeight, glowStrength.toInt(), shadowColor)
                drawEdgeGradientShadow(drawX, drawY, notifWidth, notifHeight, gradientShadowRadius, shadowColor)
            }

            // ========== 模糊背景 ==========
            if (BlurSettings.active && BlurSettings.notifications) {
                val blurX = ((elemRenderX + drawX) * elemScale) * screenScale
                val blurY = ((elemRenderY + drawY) * elemScale) * screenScale
                val blurW = notifWidth * elemScale * screenScale
                val blurH = notifHeight * elemScale * screenScale
                BlurUtils.drawOffsetBlur(blurX, blurY, blurW, blurH, BlurSettings.passes, 0f)
            }

            // ========== 主矩形 ==========
            drawRect(drawX, drawY, drawX + notifWidth, drawY + notifHeight, backgroundColor.rgb)

            if (borderEnabled) {
                drawBorder(drawX, drawY, drawX + notifWidth, drawY + notifHeight, borderWidth, borderColor.rgb)
            }

            // ========== 文字和图标 ==========
            val textX = drawX + if (showIcon) iconSize + 8f else 6f
            val textY = drawY + 4f

            Fonts.fontSemibold40.drawString(notification.title, textX, textY, Color.WHITE.rgb, true)
            Fonts.fontSemibold35.drawString(notification.description, textX, textY + Fonts.fontSemibold40.fontHeight + 1, Color.WHITE.rgb, false)

            if (showIcon) {
                val iconX = drawX + 4f
                val iconY = drawY + (notifHeight - iconSize) / 2f
                drawImage(notification.severityType.path, iconX, iconY, iconSize, iconSize)
            }

            // ========== 进度条 ==========
            if (progressBarEnabled) {
                val barX = drawX
                val barY = drawY + notifHeight
                // 剩余时间比例
                val remaining = if (elapsed < displayTime + ANIM_DURATION) {
                    1f - (elapsed - ANIM_DURATION).coerceAtLeast(0L) / displayTime.toFloat()
                } else {
                    0f
                }
                val fillWidth = notifWidth * remaining.coerceIn(0f, 1f)
                drawRect(barX, barY, barX + notifWidth, barY + progressBarHeight, progressBarBackground.rgb)
                if (fillWidth > 0) {
                    drawRect(barX, barY, barX + fillWidth, barY + progressBarHeight, progressBarColor.rgb)
                }
            }

            verticalOffset += fullHeight
        }

        // 没有通知时返回 null（隐藏）
        return null
    }

    private fun calculateWidth(notification: Notification): Float {
        val titleWidth = Fonts.fontSemibold40.getStringWidth(notification.title)
        val descWidth = Fonts.fontSemibold35.getStringWidth(notification.description)
        val textWidth = max(titleWidth, descWidth)
        val iconSpace = if (showIcon) iconSize + 8f else 0f
        return textWidth + iconSpace + padding * 2
    }

    private fun drawEdgeGradientShadow(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Color) {
        if (radius <= 0f) return
        val maxAlpha = color.alpha / 255f
        if (maxAlpha <= 0f) return

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        for (i in 10 downTo 1) {
            val progress = i / 10f
            val expand = radius * progress
            val alpha = maxAlpha * (1f - progress)
            drawRect(
                x - expand, y - expand, x + w + expand, y + h + expand,
                Color(color.red, color.green, color.blue, (alpha * 255).toInt()).rgb
            )
        }

        GL11.glPopAttrib()
    }
}