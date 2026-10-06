package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.features.module.modules.render.MusicPlayer
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.CustomTexture
import net.ccbluex.liquidbounce.utils.render.GlowUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawTexture
import net.ccbluex.liquidbounce.utils.render.music.MusicTrack
import net.minecraft.client.gui.ScaledResolution
import java.awt.Color

/**
 * 音乐播放器 HUD。
 *
 * 面板：圆角背景 + 可选背景模糊（受 BlurSettings 控制）+ GlowUtils 阴影（支持 ShadowMask）。
 * 内容：封面、标题、"第 N/M 首" 标签、进度条与 "已播放 / 总时长"。
 * 实际播放逻辑在 [MusicPlayer] 模块里。
 */
@ElementInfo(name = "MusicPlayer")
class MusicPlayerElement : Element("MusicPlayer", 2.0, 2.0, 1F) {

    private val width by float("Width", 190f, 120f..340f)
    private val height by float("Height", 78f, 48f..180f)
    private val radius by float("Radius", 8f, 0f..24f)
    private val bgColor by color("Background", Color(16, 16, 16, 170))
    private val textColor by color("TextColor", Color(255, 255, 255))
    private val subTextColor by color("SubTextColor", Color(175, 180, 190))

    private val showCover by boolean("ShowCover", true)
    private val coverSize by float("CoverSize", 56f, 24f..110f) { showCover }
    private val coverRadius by float("CoverRadius", 6f, 0f..16f) { showCover }

    private val showLabel by boolean("ShowIndexLabel", true)
    private val showProgress by boolean("ShowProgress", true)
    private val barHeight by float("BarHeight", 4f, 1f..10f) { showProgress }
    private val barBackground by color("BarBackground", Color(255, 255, 255, 45)) { showProgress }
    private val barColor by color("BarColor", Color(80, 160, 255)) { showProgress }

    private val showBlur by boolean("Blur", true)
    private val showGlow by boolean("Glow", true)
    private val glowColor by color("GlowColor", Color(0, 0, 0, 130)) { showGlow }
    private val glowStrength by int("GlowStrength", 14, 1..40) { showGlow }
    private val glowMask by boolean("GlowMask", true) { showGlow }

    private val font by font("TitleFont", Fonts.fontSemibold40)
    private val subFont by font("SubFont", Fonts.fontSemibold35)

    private var coverTexture: CustomTexture? = null
    private var coverTrack: MusicTrack? = null

    override fun drawElement(): Border {
        if (!MusicPlayer.state) return Border(0f, 0f, 0f, 0f)

        val w = width
        val h = height
        val pad = 6f

        val x1 = 0f
        val y1 = 0f
        val x2 = w
        val y2 = h

        // 阴影（GlowUtils，支持 ShadowMask）
        if (showGlow) {
            GlowUtils.drawGlow(x1, y1, x2 - x1, y2 - y1, glowStrength, glowColor, radius, false, glowMask)
        }

        // 背景模糊（开关在 BlurSettings 模块里）
        if (showBlur && BlurSettings.active) {
            val sr = ScaledResolution(mc)
            val screenScale = sr.scaleFactor.toFloat()
            val elementScale = this.scale
            val screenX = ((renderX + x1) * elementScale * screenScale).toFloat()
            val screenY = ((renderY + y1) * elementScale * screenScale).toFloat()
            val screenW = (x2 - x1) * elementScale * screenScale
            val screenH = (y2 - y1) * elementScale * screenScale
            BlurUtils.drawOffsetBlur(
                screenX, screenY, screenW, screenH,
                samples = BlurSettings.passes,
                strength = 0f,
                radius = radius * elementScale * screenScale
            )
        }

        // 圆角背景
        drawRoundedRect(x1, y1, x2, y2, bgColor.rgb, radius)

        val track = MusicPlayer.currentTrack
        val total = MusicPlayer.tracks.size
        val index = MusicPlayer.currentIndex

        // 封面
        var textX = pad
        if (showCover) {
            val size = coverSize
            val cx = pad
            val cy = (h - size) / 2f
            val texture = resolveCoverTexture(track)
            if (texture != null) {
                drawTexture(texture.textureId, cx.toInt(), cy.toInt(), size.toInt(), size.toInt())
            } else {
                drawRoundedRect(cx, cy, cx + size, cy + size, barBackground.rgb, coverRadius)
            }
            textX = pad + size + pad
        }

        val barY = h - pad - barHeight

        // 标题
        font.drawString(track?.displayName ?: "无音乐", textX, pad + 2f, textColor.rgb, true)

        // 第几首
        if (showLabel) {
            val label = if (track != null) "第 ${index + 1}/$total 首" else "播放列表为空"
            subFont.drawString(label, textX, pad + 22f, subTextColor.rgb, true)
        }

        // 进度条 + 播放到哪了
        if (showProgress) {
            val barX1 = pad
            val barX2 = w - pad
            val barRadius = barHeight / 2f
            drawRoundedRect(barX1, barY, barX2, barY + barHeight, barBackground.rgb, barRadius)

            val position = MusicPlayer.position
            val duration = MusicPlayer.duration
            val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
            if (progress > 0f) {
                val fillX2 = barX1 + (barX2 - barX1) * progress
                drawRoundedRect(barX1, barY, fillX2, barY + barHeight, barColor.rgb, barRadius)
            }

            val timeText = "${formatTime(position)} / ${formatTime(duration)}"
            subFont.drawString(timeText, textX, barY - 12f, subTextColor.rgb, true)
        }

        return Border(x1, y1, x2, y2)
    }

    private fun resolveCoverTexture(track: MusicTrack?): CustomTexture? {
        if (track == null) {
            coverTexture?.unload()
            coverTexture = null
            coverTrack = null
            return null
        }

        if (coverTrack !== track) {
            coverTexture?.unload()
            coverTexture = null
            coverTrack = track
        }

        if (coverTexture == null) {
            MusicPlayer.currentCover?.let { coverTexture = CustomTexture(it) }
        }
        return coverTexture
    }

    override fun destroyElement() {
        coverTexture?.unload()
        coverTexture = null
        coverTrack = null
    }

    private fun formatTime(seconds: Float): String {
        if (seconds <= 0f || seconds.isNaN() || seconds.isInfinite()) return "0:00"
        val total = seconds.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }
}
