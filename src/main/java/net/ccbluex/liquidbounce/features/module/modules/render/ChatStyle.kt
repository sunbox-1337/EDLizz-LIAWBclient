/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import java.awt.Color

/**
 * ChatStyle —— 只改原版聊天框的背景颜色与外观，聊天文字 / 链接点击 / 滚轮全部走原版。
 *
 * 不再从日志扒聊天，也不自己画聊天框。MixinGuiNewChat 会把原版 drawChat 里那个背景矩形
 * （其边界本来就是原版按当前显示行数算出来的）重定向到这里，所以背景会随行数自动伸缩。
 */
object ChatStyle : Module("ChatStyle", Category.RENDER) {

    private val bgColor by color("BackgroundColor", Color(0, 0, 0, 160))
    private val cornerRadius by float("CornerRadius", 4f, 0f..20f)

    private val shadow by boolean("Shadow", true)
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 255)) { shadow }
    private val shadowStrength by float("ShadowStrength", 4f, 0f..10f) { shadow }
    private val heavyShadow by boolean("HeavyShadow", false) { shadow }
    private val shadowOnlyBorder by boolean("ShadowOnlyBorder", false) { shadow }
    private val shadowMask by boolean("ShadowMask", true) { shadow && !shadowOnlyBorder }

    /**
     * 由 MixinGuiNewChat 调用，画整块聊天背景。
     *
     * [left]/[top]/[right]/[bottom] 是聊天**变换后**的局部坐标（用来画圆角矩形/阴影）；
     * [screenX]/[screenY]/[screenW]/[screenH] 是同一矩形换算到**屏幕像素**的位置（模糊是在屏幕空间采样的）。
     */
    fun drawBackground(
        left: Int, top: Int, right: Int, bottom: Int,
        screenX: Float, screenY: Float, screenW: Float, screenH: Float
    ) {
        val l = left.toFloat()
        val t = top.toFloat()
        val w = (right - left).toFloat()
        val h = (bottom - top).toFloat()

        if (BlurSettings.active && BlurSettings.chatStyle) {
            val pixPerLocal = if (right != left) screenW / (right - left) else 1f
            BlurUtils.drawOffsetBlur(screenX, screenY, screenW, screenH, BlurSettings.passes, 0f, cornerRadius * pixPerLocal)
        }

        if (shadow) {
            val blurRadius = if (heavyShadow) (shadowStrength * 3f).toInt() else (shadowStrength * 2f).toInt()
            if (blurRadius > 0) {
                GlowUtils.drawGlow(l, t, w, h, blurRadius, shadowColor, cornerRadius, shadowOnlyBorder, shadowMask)
            }
        }

        drawRoundedRect(l, t, l + w, t + h, bgColor.rgb, cornerRadius)
    }

    /** ChatStyle 关闭时由 mixin 调用，保持原版每行的纯色背景。 */
    fun drawPlainRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        drawRect(left, top, right, bottom, color)
    }
}
