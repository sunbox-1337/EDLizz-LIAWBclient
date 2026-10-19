/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.misc.GameDetector
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.Side
import net.ccbluex.liquidbounce.ui.client.hud.element.Side.Horizontal
import net.ccbluex.liquidbounce.ui.client.hud.element.Side.Vertical
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.extensions.safeDiv
import net.ccbluex.liquidbounce.utils.render.*
import net.ccbluex.liquidbounce.utils.render.ColorUtils.fade
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawImage
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.animation.AnimationUtil
import net.ccbluex.liquidbounce.utils.render.shader.shaders.GradientFontShader
import net.ccbluex.liquidbounce.utils.render.shader.shaders.GradientShader
import net.ccbluex.liquidbounce.utils.render.shader.shaders.RainbowFontShader
import net.ccbluex.liquidbounce.utils.render.shader.shaders.RainbowShader
import net.ccbluex.liquidbounce.utils.extras.GlowUtils2
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager.resetColor
import java.awt.Color
import kotlin.math.roundToInt

@ElementInfo(name = "Arraylist", single = true)
class Arraylist(
    x: Double = 0.0, y: Double = 0.0, scale: Float = 1F,
    side: Side = Side(Horizontal.RIGHT, Vertical.UP),
) : Element("Arraylist", x, y, scale, side) {

    // ==================== 原有设置 ====================
    private val textColorMode by choices(
        "Text-Mode", arrayOf("Custom", "Fade", "Random", "Rainbow", "Gradient"), "Custom"
    )
    private val textColors = ColorSettingsInteger(this, "TextColor") { textColorMode == "Custom" }.with(blueRibbon)
    private val textFadeColors = ColorSettingsInteger(this, "Text-Fade") { textColorMode == "Fade" }.with(0, 111, 255)

    private val textFadeDistance by int("Text-Fade-Distance", 50, 0..100) { textColorMode == "Fade" }

    private val gradientTextSpeed by float("Text-Gradient-Speed", 1f, 0.5f..10f) { textColorMode == "Gradient" }
    private val textGradientDirection by choices("Text-Gradient-Direction", arrayOf("Horizontal", "Vertical"), "Vertical") { textColorMode == "Gradient" }
    private val textGradientDistance by float("Text-Gradient-Distance", 120F, 10F..600F) { textColorMode == "Gradient" }

    private val maxTextGradientColors by int(
        "Max-Text-Gradient-Colors", 4, 1..MAX_GRADIENT_COLORS
    ) { textColorMode == "Gradient" }
    private val textGradColors =
        ColorSettingsFloat.create(this, "Text-Gradient") { textColorMode == "Gradient" && it <= maxTextGradientColors }

    private val rectMode by choices("Rect-Mode", arrayOf("None", "Left", "Right", "Outline"), "Right")
    private val roundedRectRadius by float("RoundedRect-Radius", 0F, 0F..20F) { rectMode !in setOf("None", "Outline") }
    private val rectColorMode by choices(
        "Rect-ColorMode", arrayOf("Custom", "Fade", "Random", "Rainbow", "Gradient"), "Custom"
    ) { rectMode != "None" }
    private val rectColors =
        ColorSettingsInteger(this, "RectColor", applyMax = true) { isCustomRectSupported }.with(blueRibbon)
    private val rectFadeColors = ColorSettingsInteger(this, "Rect-Fade", applyMax = true) { rectColorMode == "Fade" }

    private val rectFadeDistance by int("Rect-Fade-Distance", 50, 0..100) { rectColorMode == "Fade" }

    private val gradientRectSpeed by float("Rect-Gradient-Speed", 1f, 0.5f..10f) { isCustomRectGradientSupported }

    private val maxRectGradientColors by int(
        "Max-Rect-Gradient-Colors", 4, 1..MAX_GRADIENT_COLORS
    ) { isCustomRectGradientSupported }
    private val rectGradColors = ColorSettingsFloat.create(
        this, "Rect-Gradient"
    ) { isCustomRectGradientSupported && it <= maxRectGradientColors }

    private val roundedBackgroundRadius by float("RoundedBackGround-Radius", 1F, 0F..20F) { bgColors.color().alpha > 0 }

    // 空格模式：相邻两行矩形之间留空隙（把每行矩形实际渲染高度缩到右侧 Rect 的高度）
    private val rectSpacing by boolean("Rect-Spacing", false) { bgColors.color().alpha > 0 }

    private val backgroundMode by choices(
        "Background-Mode", arrayOf("Custom", "Fade", "Random", "Rainbow", "Gradient"), "Custom"
    )
    private val shadowcheck by boolean("Shadowcheck", false)
    private val shadowStrength by float("ShadowStrength", 0.5f, 0.02f..2.0f)

    /** 1px 内阴影：模糊/裁切偶尔会留下一条透明边，用内描边盖住 */
    private val innerShadow by boolean("InnerShadow1px", false)

    /** 阴影模糊半径（像素）：最低 1px，支持"超级小"的细边阴影 */
    private val shadowRadius: Int
        get() = (shadowStrength * 13F).toInt().coerceAtLeast(1)
    private val shadowCheckColor by color("ShadowCheckColor", Color(0, 0, 0, 120)) { shadowcheck }
    private val shadowMask by boolean("ShadowMask", false) { shadowcheck }

    /** 阴影颜色跟随该行文字颜色（渐变的行取自定义文字色） */
    private val shadowFollowText by boolean("ShadowFollowText", false) { shadowcheck || glow }
    private val glow by boolean("Glow", false)

    private val bgColors =
        ColorSettingsInteger(this, "BackgroundColor") { backgroundMode == "Custom" }.with(Color.BLACK.withAlpha(150))
    private val bgFadeColors = ColorSettingsInteger(this, "Background-Fade") { backgroundMode == "Fade" }

    private val bgFadeDistance by int("Background-Fade-Distance", 50, 0..100) { backgroundMode == "Fade" }

    private val gradientBackgroundSpeed by float(
        "Background-Gradient-Speed", 1f, 0.5f..10f
    ) { backgroundMode == "Gradient" }

    private val maxBackgroundGradientColors by int(
        "Max-Background-Gradient-Colors", 4, 1..MAX_GRADIENT_COLORS
    ) { backgroundMode == "Gradient" }
    private val bgGradColors = ColorSettingsFloat.create(
        this, "Background-Gradient"
    ) { backgroundMode == "Gradient" && it <= maxBackgroundGradientColors }

    // Icons
    private val displayIcons by boolean("DisplayIcons", true)
    private val iconShadows by boolean("IconShadows", true) { displayIcons }
    private val xDistance by float("ShadowXDistance", 0F, -2F..2F) { iconShadows }
    private val yDistance by float("ShadowYDistance", 0F, -2F..2F) { iconShadows }
    private val shadowColor by color("ShadowColor", Color.BLACK.withAlpha(128), rainbow = true) { iconShadows }

    private val iconColorMode by choices(
        "IconColorMode", arrayOf("Custom", "Fade"), "Custom"
    ) { displayIcons }
    private val iconColor by color("IconColor", Color.WHITE) { iconColorMode == "Custom" && displayIcons }
    private val iconFadeColor by color("IconFadeColor", Color.WHITE) { iconColorMode == "Fade" && displayIcons }
    private val iconFadeDistance by int("IconFadeDistance", 50, 0..100) { iconColorMode == "Fade" && displayIcons }

    // ==================== 全局背景（仅模糊与颜色，无阴影） ====================
    private val globalBackground by boolean("GlobalBackground", false)
    private val globalBgColor by color("GlobalBgColor", Color.BLACK.withAlpha(128)) { globalBackground }
    private val globalBgRadius by float("GlobalBgRadius", 3F, 0F..20F) { globalBackground }

    private fun isColorModeUsed(value: String) = value in listOf(textColorMode, rectMode, backgroundMode, iconColorMode)

    private val saturation by float("Random-Saturation", 0.9f, 0f..1f) { isColorModeUsed("Random") }
    private val brightness by float("Random-Brightness", 1f, 0f..1f) { isColorModeUsed("Random") }
    private val rainbowX by float("Rainbow-X", -1000F, -2000F..2000F) { isColorModeUsed("Rainbow") }
    private val rainbowY by float("Rainbow-Y", -1000F, -2000F..2000F) { isColorModeUsed("Rainbow") }
    private val gradientX by float("Gradient-X", -1000F, -2000F..2000F) { isColorModeUsed("Gradient") }
    private val gradientY by float("Gradient-Y", -1000F, -2000F..2000F) { isColorModeUsed("Gradient") }

    private val tags by boolean("Tags", true)
    private val tagsStyle by choices("TagsStyle", arrayOf("[]", "()", "<>", "-", "|", "Space"), "Space") {
        tags
    }.onChanged { updateTagDetails() }
    private val tagsCase by choices("TagsCase", arrayOf("Normal", "Uppercase", "Lowercase"), "Normal") { tags }
    private val tagsArrayColor by boolean("TagsArrayColor", false) {
        tags
    }.onChanged { updateTagDetails() }

    private val font by font("Font", Fonts.fontSemibold35)
    private val textShadow by boolean("ShadowText", true)
    private val moduleCase by choices("ModuleCase", arrayOf("Normal", "Uppercase", "Lowercase"), "Normal")
    private val space by float("Space", 1F, 0F..5F)
    private val textHeight by float("TextHeight", 11F, 1F..20F)
    private val textY by float("TextY", 3.25F, 0F..20F)

    private val textGlow by boolean("TextGlow", false)
    private val rectGlow by boolean("RectGlow", false)
    private val textGlowMode by choices("TextGlowMode", arrayOf("Normal", "Brightness"), "Normal") { textGlow }
    private val textGlowStrength by float("TextGlowStrength", 8F, 1F..55F) { textGlow }
    private val textGlowSpread by float("TextGlowSpread", 6F, 0F..150F) { textGlow }
    private val textGlowOpacity by float("TextGlowOpacity", 100F, 0F..220F) { textGlow }
    private val textGlowAdaptColor by boolean("TextGlowAdaptColor", true) { textGlow }
    private val textGlowColor by color("TextGlowColor", Color(255, 255, 255, 110)) { textGlow && !textGlowAdaptColor }

    private fun drawTextGlow(text: String, x: Float, y: Float, color: Int) {
        if (!textGlow) return
        val base = if (textGlowAdaptColor) {
            if ((color ushr 24) and 0xFF == 0) Color(255, 255, 255, 255).rgb else color
        } else {
            Color(textGlowColor.red, textGlowColor.green, textGlowColor.blue, textGlowColor.alpha).rgb
        }

        var alpha = ((base ushr 24) and 0xFF) * (textGlowOpacity / 100f)
        // Brightness 模式：文字越亮发光越实，越暗发光越淡
        if (textGlowMode == "Brightness") {
            val brightness = maxOf((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF) / 255f
            alpha *= brightness
        }
        val finalColor = (base and 0x00FFFFFF) or (alpha.toInt().coerceIn(0, 255) shl 24)

        TextGlowUtils.drawGlowText(
            text, x, y, finalColor, font,
            textGlowStrength, textGlowSpread, false, finalColor
        )
    }

    private fun gradientAt(colors: List<Color>, t: Float): Int {
        val n = colors.size
        if (n == 0) return -1
        if (n == 1) return colors[0].rgb
        var tt = t - Math.floor(t.toDouble()).toFloat()
        if (tt < 0f) tt += 1f
        val scaled = tt * n
        val fl = Math.floor(scaled.toDouble()).toFloat()
        val i1 = fl.toInt() % n
        val i2 = (i1 + 1) % n
        val frac = scaled - fl
        val c1 = colors[i1]
        val c2 = colors[i2]
        fun lerp(a: Int, b: Int) = (a + (b - a) * frac).toInt().coerceIn(0, 255)
        return Color(
            lerp(c1.red, c2.red), lerp(c1.green, c2.green),
            lerp(c1.blue, c2.blue), lerp(c1.alpha, c2.alpha)
        ).rgb
    }

    /** 直接用真实颜色逐字符绘制渐变文字（并发光），方向/滚动可切换。 */
    private fun drawGradientText(text: String, startX: Float, y: Float, shadow: Boolean) {
        val colors = textGradColors.take(maxTextGradientColors).map { it.color }
        if (colors.isEmpty()) {
            font.drawString(text, startX, y, textColors.color().rgb, shadow)
            return
        }
        // 渐变距离：一次完整颜色循环跨越的像素数（越大越缓、越小越密）
        val distance = textGradientDistance.coerceAtLeast(1f)
        // 滚动偏移；速度由 Text-Gradient-Speed 控制（竖直/水平共用）
        val scroll = (System.currentTimeMillis() % 100000L) / 1000f * gradientTextSpeed * 0.1f
        fun tAt(cx: Float) =
            if (textGradientDirection == "Horizontal") (cx - startX) / distance else y / distance

        // 逐字符解析并尊重 § 颜色代码：默认走渐变；遇到 §x 颜色代码就切到该颜色，§r 复位回渐变。
        // 这样 " §7副标题" 会真的画成灰色，而不是把 §7 当普通字符画出来（之前那个多余的 "7"）。
        fun eachGlyph(action: (ch: Char, x: Float, color: Int) -> Unit) {
            var i = 0
            var x = startX
            var code: Int? = null
            while (i < text.length) {
                val ch = text[i]
                if (ch == '\u00a7' && i + 1 < text.length) {
                    when (val c = text[i + 1].lowercaseChar()) {
                        'r' -> code = null
                        in '0'..'9', in 'a'..'f' -> code = codeToRgb(c)
                        else -> Unit // k/l/m/n/o 等格式代码不改变颜色
                    }
                    i += 2
                    continue
                }
                val gradient = gradientAt(colors, tAt(x) + scroll)
                // 代码色只替换 RGB，alpha 仍取渐变算出来的值，保证不透明度设置依旧生效
                val color = code?.let { (it and 0x00FFFFFF) or (gradient and -0x1000000) } ?: gradient
                action(ch, x, color)
                x += font.getCharWidth(ch).toFloat()
                i++
            }
        }

        if (textGlow) eachGlyph { ch, gx, color -> drawTextGlow(ch.toString(), gx, y, color) }
        eachGlyph { ch, cx, color -> font.drawString(ch.toString(), cx, y, color, shadow) }
    }

    /** § 颜色代码 → RGB（不含 alpha）。 */
    private fun codeToRgb(code: Char): Int = when (code) {
        '0' -> 0x000000
        '1' -> 0x0000AA
        '2' -> 0x00AA00
        '3' -> 0x00AAAA
        '4' -> 0xAA0000
        '5' -> 0xAA00AA
        '6' -> 0xFFAA00
        '7' -> 0xAAAAAA
        '8' -> 0x555555
        '9' -> 0x5555FF
        'a' -> 0x55FF55
        'b' -> 0x55FFFF
        'c' -> 0xFF5555
        'd' -> 0xFF55FF
        'e' -> 0xFFFF55
        else -> 0xFFFFFF
    }

    /** 给 rect 画一圈柔光（必须在 rect 之前作为底层调用）。 */
    private fun drawRectGlow(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float) {
        // 渐变/彩虹模式下 rectColor 只是占位 0（真正颜色由 shader 上），此时不画 rect 光晕，免得画成黑线
        if ((color ushr 24) and 0xFF == 0) return

        val steps = textGlowStrength.toInt().coerceIn(2, 55)
        val base = textGlowOpacity.coerceIn(0f, 220f) / 100f
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        // 由外向内叠加深度递减的实心矩形形成光晕（用 drawRect，避免圆角/tessellator 在窄条上退化）
        for (i in steps downTo 1) {
            val expand = i * 0.6f
            val alpha = (base * 255f * (1f - i.toFloat() / (steps + 1)) / 3f).toInt().coerceIn(0, 255)
            if (alpha <= 0) continue
            drawRect(left - expand, top - expand, right + expand, bottom + expand, Color(r, g, b, alpha).rgb)
        }
    }

    private val animation by choices("Animation", arrayOf("Slide", "Smooth"), "Smooth") { tags }
    private val animationSpeed by float("AnimationSpeed", 0.2F, 0.01F..1F) { animation == "Smooth" }

    companion object : Configurable("StandaloneArraylist") {
        val spacedModulesValue = boolean("SpacedModules", false)
    }

    private val spacedModules: Boolean by +spacedModulesValue

    private val inactiveStyle by choices(
        "InactiveModulesStyle", arrayOf("Normal", "Color", "Hide"), "Color"
    ) { GameDetector.state }

    private var x2 = 0
    private var y2 = 0F

    private lateinit var tagPrefix: String
    private lateinit var tagSuffix: String

    private var modules = emptyList<Module>()

    private val inactiveColor = Color(255, 255, 255, 100).rgb

    /** 吸附到整数像素：矩形坐标只用整像素，动画本身保留小数没有关系 */
    private fun snap(value: Float): Float = value.roundToInt().toFloat()

    /** 文本基线偏移也取整，保证整行只在整像素上移动 */
    private val textOffsetY: Float
        get() = snap(textY)

    /**
     * 阴影颜色：默认用 ShadowCheckColor，
     * 打开 ShadowFollowText 后取该行文字实际用的颜色（渐变/彩虹没有单一颜色，退回自定义文字色）。
     */
    private fun shadowColorFor(markAsInactive: Boolean, textRgb: Int): Color {
        if (!shadowFollowText) return shadowCheckColor

        return Color(if (markAsInactive) inactiveColor else textRgb, true)
    }

    /** 1px 内阴影：沿矩形内侧画一圈 1px 实心边，盖住模糊/裁切留下的透明缝 */
    private fun drawInnerShadow(rect: FloatArray) {
        val color = shadowCheckColor.rgb
        drawRect(rect[0], rect[1], rect[2], rect[1] + 1f, color)
        drawRect(rect[0], rect[3] - 1f, rect[2], rect[3], color)
        drawRect(rect[0], rect[1], rect[0] + 1f, rect[3], color)
        drawRect(rect[2] - 1f, rect[1], rect[2], rect[3], color)
    }

    private val isCustomRectSupported
        get() = rectMode != "None" && rectColorMode == "Custom"

    private val isCustomRectGradientSupported
        get() = rectMode != "None" && rectColorMode == "Gradient"

    init {
        updateTagDetails()
    }

    fun updateTagDetails() {
        val pair: Pair<String, String> = when (tagsStyle) {
            "[]", "()", "<>" -> tagsStyle[0].toString() to tagsStyle[1].toString()
            "-", "|" -> tagsStyle[0] + " " to ""
            else -> "" to ""
        }

        tagPrefix = (if (tagsArrayColor) " " else " §7") + pair.first
        tagSuffix = pair.second
    }

    private fun getDisplayString(module: Module): String {
        val moduleName = when (moduleCase) {
            "Uppercase" -> module.getName().uppercase()
            "Lowercase" -> module.getName().lowercase()
            else -> module.getName()
        }

        var tag = module.tag ?: ""

        tag = when (tagsCase) {
            "Uppercase" -> tag.uppercase()
            "Lowercase" -> tag.lowercase()
            else -> tag
        }

        val moduleTag = if (tags && !module.tag.isNullOrEmpty()) tagPrefix + tag + tagSuffix else ""

        return moduleName + moduleTag
    }

    override fun drawElement(): Border? {
        assumeNonVolatile {
            val delta = deltaTime
            val padding = if (displayIcons) 15 else 0

            // 动画更新
            for (module in moduleManager) {
                val shouldShow = (!module.isHidden && module.state && (inactiveStyle != "Hide" || module.isActive))
                if (!shouldShow && module.slide <= 0f) continue
                val displayString = getDisplayString(module)
                val width = font.getStringWidth(displayString) + padding

                when (animation) {
                    "Slide" -> {
                        module.slideStep += if (shouldShow) delta / 4F else -delta / 4F
                        module.slide = if (shouldShow) {
                            if (module.slide < width) AnimationUtils.easeOut(module.slideStep, width.toFloat()) * width else width.toFloat()
                        } else {
                            AnimationUtils.easeOut(module.slideStep, width.toFloat()) * width
                        }
                        module.slide = module.slide.coerceIn(0F, width.toFloat())
                        module.slideStep = module.slideStep.coerceIn(0F, width.toFloat())
                    }
                    "Smooth" -> {
                        val target = if (shouldShow) width.toDouble() else -width / 5.0
                        module.slide = AnimationUtil.base(module.slide.toDouble(), target, animationSpeed.toDouble()).toFloat()
                    }
                }
            }

            val textSpacer = textHeight + space
            // 空格模式：每行矩形上下各内缩 1px → 行与行之间留出 2px 空隙，矩形高度 ≈ 右侧 Rect 的高度
            val rectInset = if (rectSpacing) 1F else 0F
            val textCustomColor = textColors.color().rgb
            val rectCustomColor = rectColors.color().rgb
            val backgroundCustomColor = bgColors.color().rgb

            val rainbowOffset = System.currentTimeMillis() % 10000 / 10000F
            val rainbowX = 1f safeDiv rainbowX
            val rainbowY = 1f safeDiv rainbowY

            val gradientOffset = System.currentTimeMillis() % 10000 / 10000F
            val gradientX = 1f safeDiv gradientX
            val gradientY = 1f safeDiv gradientY

            // ---------- 计算整体边界（用于全局背景） ----------
            var totalMinX = Float.MAX_VALUE
            var totalMaxX = -Float.MAX_VALUE
            var totalMinY = Float.MAX_VALUE
            var totalMaxY = -Float.MAX_VALUE
            // 收集每行背景矩形，最后合并成一张整体阴影（避免逐行阴影在上下方向互相渗透）
            val shadowRects = ArrayList<FloatArray>()
            // 本趟算出的每行 y 会被下面绘制趟复用，保证阴影与实际绘制的行完全对齐
            val rowYPositions = FloatArray(modules.size)
            // 每行的背景矩形（已吸附到整数像素），第二趟直接复用同一个矩形
            val rowRects = ArrayList<FloatArray>(modules.size)

            modules.forEachIndexed { index, module ->
                var yPos = (if (side.vertical == Vertical.DOWN) -textSpacer else textSpacer) *
                        if (side.vertical == Vertical.DOWN) index + 1 else index
                if (animation == "Smooth") {
                    // 原先 yAnim 在这趟与绘制趟各推进一次；这里合并到一处推进两次，
                    // 既保持原来的动画速度，又让阴影与绘制使用同一坐标
                    module.yAnim = AnimationUtil.base(module.yAnim.toDouble(), yPos.toDouble(), 0.2).toFloat()
                    module.yAnim = AnimationUtil.base(module.yAnim.toDouble(), yPos.toDouble(), 0.2).toFloat()
                    yPos = module.yAnim
                }

                // 动画本身保留小数推进，但落点取整：整行只在整像素上移动
                yPos = snap(yPos)
                rowYPositions[index] = yPos
                val displayString = getDisplayString(module)
                val width = font.getStringWidth(displayString)
                val slide = snap(module.slide)

                val xPos = when (side.horizontal) {
                    Horizontal.RIGHT, Horizontal.MIDDLE -> -slide - if (displayIcons) 2 else 3
                    Horizontal.LEFT -> -(width - slide) + if (rectMode == "Left") 6 else 3
                }

                val moduleMinX = if (side.horizontal == Horizontal.RIGHT || side.horizontal == Horizontal.MIDDLE) {
                    xPos - if (rectMode == "Right") 5 else 2
                } else {
                    // 与第二趟实际绘制的背景左边对齐（Left 模式多 1px）
                    if (rectMode == "Left") 1f else 0f
                }
                val moduleMaxX = if (side.horizontal == Horizontal.RIGHT || side.horizontal == Horizontal.MIDDLE) {
                    if (rectMode == "Right") -3F else -1F
                } else {
                    xPos + width + if (rectMode == "Right") 4 else 1
                }

                // 吸附到整数像素：阴影、模糊遮罩、背景填充共用同一个矩形，避免半像素造成的 1px 透明缝
                val rowLeft = snap(moduleMinX)
                val rowRight = snap(moduleMaxX)
                val rowTop = snap(yPos + rectInset)
                val rowBottom = snap(yPos + textSpacer - rectInset)

                totalMinX = minOf(totalMinX, rowLeft)
                totalMaxX = maxOf(totalMaxX, rowRight)
                totalMinY = minOf(totalMinY, rowTop)
                totalMaxY = maxOf(totalMaxY, rowBottom)

                val rowRect = floatArrayOf(rowLeft, rowTop, rowRight, rowBottom)
                shadowRects.add(rowRect)
                rowRects.add(rowRect)
            }

            // ---------- 整体阴影：整块只画一次，内部无接缝，不会上下污染 ----------
            if (shadowcheck && bgColors.color().alpha > 0 && shadowRects.isNotEmpty()) {
                GlowUtils.drawCompositeGlow(
                    shadowRects,
                    shadowRadius,
                    shadowColorFor(false, textCustomColor),
                    mask = shadowMask
                )
            }

            // ---------- 背景模糊：整块只跑一次，再用模板遮罩把每行区域切出来 ----------
            if (BlurSettings.active && BlurSettings.arraylist && !globalBackground && modules.isNotEmpty()) {
                val sr = ScaledResolution(mc)
                val screenScale = sr.scaleFactor.toFloat()
                val elementScale = this.scale
                // HUD: glScalef(scale) 再 glTranslated(renderX, renderY) → 局部点 L 的屏幕位置是 (renderX + L) * scale
                val screenRects = shadowRects.map { r ->
                    floatArrayOf(
                        ((renderX + r[0]) * elementScale * screenScale).toFloat(),
                        ((renderY + r[1]) * elementScale * screenScale).toFloat(),
                        ((renderX + r[2]) * elementScale * screenScale).toFloat(),
                        ((renderY + r[3]) * elementScale * screenScale).toFloat()
                    )
                }

                // 整块 = 所有行的并集：只模糊这一次，模板再把行与行之间的空隙切掉
                val unionLeft = screenRects.minOf { it[0] }
                val unionTop = screenRects.minOf { it[1] }
                val unionRight = screenRects.maxOf { it[2] }
                val unionBottom = screenRects.maxOf { it[3] }

                BlurUtils.drawOffsetBlur(
                    unionLeft, unionTop, unionRight - unionLeft, unionBottom - unionTop,
                    samples = BlurSettings.passes,
                    strength = 0f,
                    radius = roundedBackgroundRadius * elementScale * screenScale,
                    rects = screenRects,
                    liquidGlass = BlurSettings.arraylistLiquidGlass
                )
            }

            // ---------- 绘制全局背景（仅模糊与颜色） ----------
            if (globalBackground && modules.isNotEmpty()) {
                val bgLeft = totalMinX
                val bgTop = totalMinY
                val bgRight = totalMaxX
                val bgBottom = totalMaxY
                val bgWidth = bgRight - bgLeft
                val bgHeight = bgBottom - bgTop

                if (BlurSettings.active && BlurSettings.arraylist) {
                    val sr = ScaledResolution(mc)
                    val screenScale = sr.scaleFactor.toFloat()
                    val elementScale = this.scale
                    // HUD 的变换顺序是 glScalef(scale) 再 glTranslated(renderX, renderY)，
                    // 所以局部点 L 的实际屏幕位置是 (renderX + L) * scale —— renderX 自己也要乘 scale。
                    val screenX = ((renderX + bgLeft) * elementScale * screenScale).toFloat()
                    val screenY = ((renderY + bgTop) * elementScale * screenScale).toFloat()
                    val screenW = bgWidth * elementScale * screenScale
                    val screenH = bgHeight * elementScale * screenScale
                    BlurUtils.drawOffsetBlur(
                        screenX, screenY, screenW, screenH,
                        samples = BlurSettings.passes,
                        strength = 0f,
                        radius = globalBgRadius * elementScale * screenScale,
                        liquidGlass = BlurSettings.arraylistLiquidGlass
                    )
                }

                drawRoundedRect(
                    bgLeft, bgTop, bgRight, bgBottom,
                    globalBgColor.rgb, globalBgRadius
                )
            }

            // ---------- 第二遍遍历：绘制模块文字与装饰 ----------
            modules.forEachIndexed { index, module ->
                // 位置已在上一趟（边界/阴影）算好并含动画，直接复用，避免两趟各自推进导致阴影与行错位
                val yPos = rowYPositions[index]
                val moduleColor = Color.getHSBColor(module.hue, saturation, brightness).rgb

                val textFadeColor = fade(textFadeColors, index * textFadeDistance, 100).rgb
                val bgFadeColor = fade(bgFadeColors, index * bgFadeDistance, 100).rgb
                val rectFadeColor = fade(rectFadeColors, index * rectFadeDistance, 100).rgb
                val iconFadeColor = fade(iconFadeColor, index * iconFadeDistance, 100).rgb

                val markAsInactive = inactiveStyle == "Color" && !module.isActive

                // 这一行文字实际用的颜色（阴影跟随文字时取它）
                val rowTextRgb = when (textColorMode) {
                    "Random" -> moduleColor
                    "Fade" -> textFadeColor
                    else -> textCustomColor
                }

                val displayString = getDisplayString(module)
                val displayStringWidth = font.getStringWidth(displayString)

                val previousDisplayString = getDisplayString(modules[(if (index > 0) index else 1) - 1])
                val previousDisplayStringWidth = font.getStringWidth(previousDisplayString)

                when (side.horizontal) {
                    Horizontal.RIGHT, Horizontal.MIDDLE -> {
                        val xPos = -snap(module.slide) - if (displayIcons) 2 else 3

                        if (glow && bgColors.color().alpha > 0) {
                            GlowUtils2.drawAcrylicBlur(
                                xPos - if (rectMode == "Right") 5 else 2,
                                yPos + rectInset,
                                if (rectMode == "Right") (-3F - (xPos - 5)) else (-1F - (xPos - 2)),
                                textSpacer - 2F * rectInset,
                                shadowRadius,
                                shadowColorFor(markAsInactive, rowTextRgb),
                                1F
                            )
                        }

                        GradientShader.begin(
                            !markAsInactive && backgroundMode == "Gradient",
                            gradientX,
                            gradientY,
                            bgGradColors.toColorArray(maxBackgroundGradientColors),
                            gradientBackgroundSpeed,
                            gradientOffset
                        ).use {
                            RainbowShader.begin(backgroundMode == "Rainbow", rainbowX, rainbowY, rainbowOffset).use {
                                val rowRect = rowRects[index]
                                drawRoundedRect(
                                    rowRect[0],
                                    rowRect[1],
                                    rowRect[2],
                                    rowRect[3],
                                    when (backgroundMode) {
                                        "Gradient" -> 0
                                        "Rainbow" -> 0
                                        "Random" -> moduleColor
                                        "Fade" -> bgFadeColor
                                        else -> backgroundCustomColor
                                    },
                                    roundedBackgroundRadius,
                                    if (rectMode == "Left") {
                                        RenderUtils.RoundedCorners.NONE
                                    } else {
                                        RenderUtils.RoundedCorners.LEFT_ONLY
                                    }
                                )

                                if (innerShadow) drawInnerShadow(rowRect)
                            }
                        }

                        if (textColorMode == "Gradient" && !markAsInactive) {
                            // 直接按字符计算渐变色（文字 + 发光都用真实颜色），不再依赖 shader
                            drawGradientText(displayString, xPos + 1 - if (rectMode == "Right") 3 else 0, yPos + textOffsetY, textShadow)
                        } else run {
                        if (textGlow) {
                            val glowTextColor = if (markAsInactive) inactiveColor else when (textColorMode) {
                                "Gradient", "Rainbow" -> 0
                                "Random" -> moduleColor
                                "Fade" -> textFadeColor
                                else -> textCustomColor
                            }
                            drawTextGlow(displayString, xPos + 1 - if (rectMode == "Right") 3 else 0, yPos + textOffsetY, glowTextColor)
                        }

                        GradientFontShader.begin(
                            !markAsInactive && textColorMode == "Gradient",
                            gradientX,
                            gradientY,
                            textGradColors.toColorArray(maxTextGradientColors),
                            gradientTextSpeed,
                            gradientOffset
                        ).use {
                            RainbowFontShader.begin(
                                !markAsInactive && textColorMode == "Rainbow", rainbowX, rainbowY, rainbowOffset
                            ).use {
                                font.drawString(
                                    displayString,
                                    xPos + 1 - if (rectMode == "Right") 3 else 0,
                                    yPos + textOffsetY,
                                    if (markAsInactive) inactiveColor
                                    else when (textColorMode) {
                                        "Gradient" -> 0
                                        "Rainbow" -> 0
                                        "Random" -> moduleColor
                                        "Fade" -> textFadeColor
                                        else -> textCustomColor
                                    },
                                    textShadow,
                                )
                            }
                        }
                        }

                        GradientShader.begin(
                            !markAsInactive && isCustomRectGradientSupported,
                            gradientX,
                            gradientY,
                            rectGradColors.toColorArray(maxRectGradientColors),
                            gradientRectSpeed,
                            gradientOffset
                        ).use {
                            if (rectMode != "None") {
                                RainbowShader.begin(
                                    !markAsInactive && rectColorMode == "Rainbow", rainbowX, rainbowY, rainbowOffset
                                ).use {
                                    val rectColor = if (markAsInactive) inactiveColor
                                    else when (rectColorMode) {
                                        "Gradient" -> 0
                                        "Rainbow" -> 0
                                        "Random" -> moduleColor
                                        "Fade" -> rectFadeColor
                                        else -> rectCustomColor
                                    }

                                    if (rectGlow) {
                                        val (rl, rt, rr, rb) = when (rectMode) {
                                            "Left" -> listOf(xPos - 5, yPos, xPos - 2, yPos + textSpacer)
                                            "Right" -> listOf(-3.0F, yPos + 1, -1.5F, yPos + textSpacer - 1)
                                            else -> listOf(
                                                xPos - 3 - (previousDisplayStringWidth - displayStringWidth),
                                                yPos - 1F, 0F, yPos + textSpacer + 1
                                            )
                                        }
                                        drawRectGlow(rl, rt, rr, rb, rectColor, roundedRectRadius)
                                    }

                                    when (rectMode) {
                                        "Left" -> drawRoundedRect(
                                            xPos - 5,
                                            yPos,
                                            xPos - 2,
                                            yPos + textSpacer,
                                            rectColor,
                                            roundedRectRadius,
                                            RenderUtils.RoundedCorners.LEFT_ONLY
                                        )

                                        "Right" -> drawRoundedRect(
                                            -3.0F,
                                            yPos+1,
                                            -1.5F,
                                            yPos + textSpacer-1,
                                            rectColor,
                                            roundedRectRadius,
                                            if (modules.lastIndex == 0) {
                                                RenderUtils.RoundedCorners.RIGHT_ONLY
                                            } else when (module) {
                                                modules.first() -> RenderUtils.RoundedCorners.TOP_RIGHT_ONLY
                                                modules.last() -> RenderUtils.RoundedCorners.BOTTOM_RIGHT_ONLY
                                                else -> RenderUtils.RoundedCorners.NONE
                                            }
                                        )

                                        "Outline" -> {
                                            drawRect(-1F, yPos - 1F, 0F, yPos + textSpacer, rectColor)
                                            drawRect(xPos - 3, yPos, xPos - 2, yPos + textSpacer, rectColor)

                                            if (module == modules.first()) {
                                                drawRect(xPos - 3, yPos - 1F, 0F, yPos, rectColor)
                                            }

                                            drawRect(
                                                xPos - 3 - (previousDisplayStringWidth - displayStringWidth),
                                                yPos,
                                                xPos - 2,
                                                yPos + 1,
                                                rectColor
                                            )

                                            if (module == modules.last()) {
                                                drawRect(
                                                    xPos - 3, yPos + textSpacer, 0F, yPos + textSpacer + 1, rectColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Horizontal.LEFT -> {
                        val width = font.getStringWidth(displayString)
                        val xPos = -(width - snap(module.slide)) + if (rectMode == "Left") 6 else 3

                        if (glow && bgColors.color().alpha > 0) {
                            GlowUtils2.drawAcrylicBlur(
                                xPos - if (rectMode == "Right") 5 else 2,
                                yPos + rectInset,
                                if (rectMode == "Right") (-3F - (xPos - 5)) else (-1F - (xPos - 2)),
                                textSpacer - 2F * rectInset,
                                shadowRadius,
                                shadowColorFor(markAsInactive, rowTextRgb),
                                1F
                            )
                        }

                        GradientShader.begin(
                            !markAsInactive && backgroundMode == "Gradient",
                            gradientX,
                            gradientY,
                            bgGradColors.toColorArray(maxBackgroundGradientColors),
                            gradientBackgroundSpeed,
                            gradientOffset
                        ).use {
                            RainbowShader.begin(backgroundMode == "Rainbow", rainbowX, rainbowY, rainbowOffset).use {
                                val rowRect = rowRects[index]
                                drawRoundedRect(
                                    rowRect[0],
                                    rowRect[1],
                                    rowRect[2],
                                    rowRect[3],
                                    when (backgroundMode) {
                                        "Gradient" -> 0
                                        "Rainbow" -> 0
                                        "Random" -> moduleColor
                                        "Fade" -> bgFadeColor
                                        else -> backgroundCustomColor
                                    },
                                    roundedBackgroundRadius,
                                    if (rectMode == "Right") {
                                        RenderUtils.RoundedCorners.NONE
                                    } else {
                                        RenderUtils.RoundedCorners.RIGHT_ONLY
                                    }
                                )

                                if (innerShadow) drawInnerShadow(rowRect)
                            }
                        }

                        if (textColorMode == "Gradient" && !markAsInactive) {
                            drawGradientText(displayString, xPos - 1, yPos + textOffsetY, textShadow)
                        } else run {
                        if (textGlow) {
                            val glowTextColor = if (markAsInactive) inactiveColor else when (textColorMode) {
                                "Gradient", "Rainbow" -> 0
                                "Random" -> moduleColor
                                "Fade" -> textFadeColor
                                else -> textCustomColor
                            }
                            drawTextGlow(displayString, xPos - 1, yPos + textOffsetY, glowTextColor)
                        }

                        GradientFontShader.begin(
                            !markAsInactive && textColorMode == "Gradient",
                            gradientX,
                            gradientY,
                            textGradColors.toColorArray(maxTextGradientColors),
                            gradientTextSpeed,
                            gradientOffset
                        ).use {
                            RainbowFontShader.begin(
                                !markAsInactive && textColorMode == "Rainbow", rainbowX, rainbowY, rainbowOffset
                            ).use {
                                font.drawString(
                                    displayString, xPos - 1, yPos + textOffsetY, if (markAsInactive) inactiveColor
                                    else when (textColorMode) {
                                        "Gradient" -> 0
                                        "Rainbow" -> 0
                                        "Random" -> moduleColor
                                        "Fade" -> textFadeColor
                                        else -> textCustomColor
                                    }, textShadow
                                )
                            }
                        }
                        }

                        GradientShader.begin(
                            !markAsInactive && isCustomRectGradientSupported,
                            gradientX,
                            gradientY,
                            rectGradColors.toColorArray(maxRectGradientColors),
                            gradientRectSpeed,
                            gradientOffset
                        ).use {
                            RainbowShader.begin(
                                !markAsInactive && rectColorMode == "Rainbow", rainbowX, rainbowY, rainbowOffset
                            ).use {
                                if (rectMode != "None") {
                                    val rectColor = if (markAsInactive) inactiveColor
                                    else when (rectColorMode) {
                                        "Gradient" -> 0
                                        "Rainbow" -> 0
                                        "Random" -> moduleColor
                                        "Fade" -> rectFadeColor
                                        else -> rectCustomColor
                                    }

                                    if (rectGlow) {
                                        val (rl, rt, rr, rb) = when (rectMode) {
                                            "Left" -> listOf(0F, yPos, 3F, yPos + textSpacer)
                                            "Right" -> listOf(xPos + width + 2, yPos, xPos + width + 4, yPos + textSpacer)
                                            else -> listOf(-1F, yPos - 1F, xPos + width + 2, yPos + textSpacer + 1)
                                        }
                                        drawRectGlow(rl, rt, rr, rb, rectColor, roundedRectRadius)
                                    }

                                    when (rectMode) {
                                        "Left" -> drawRoundedRect(
                                            0F,
                                            yPos,
                                            3F,
                                            yPos + textSpacer,
                                            rectColor,
                                            roundedRectRadius,
                                            if (modules.lastIndex == 0) {
                                                RenderUtils.RoundedCorners.LEFT_ONLY
                                            } else when (module) {
                                                modules.first() -> RenderUtils.RoundedCorners.TOP_LEFT_ONLY
                                                modules.last() -> RenderUtils.RoundedCorners.BOTTOM_LEFT_ONLY
                                                else -> RenderUtils.RoundedCorners.NONE
                                            }
                                        )

                                        "Right" -> drawRoundedRect(
                                            xPos + width + 2,
                                            yPos,
                                            xPos + width + 2 + 2,
                                            yPos + textSpacer,
                                            rectColor,
                                            roundedRectRadius,
                                            RenderUtils.RoundedCorners.RIGHT_ONLY
                                        )

                                        "Outline" -> {
                                            drawRect(-1F, yPos - 1F, 0F, yPos + textSpacer, rectColor)
                                            drawRect(
                                                xPos + width + 1,
                                                yPos - 1F,
                                                xPos + width + 2,
                                                yPos + textSpacer,
                                                rectColor
                                            )

                                            if (module == modules.first()) {
                                                drawRect(xPos + width + 2, yPos - 1, xPos + width + 2, yPos, rectColor)
                                                drawRect(-1F, yPos - 1, xPos + width + 2, yPos, rectColor)
                                            }

                                            drawRect(
                                                xPos + width + 1,
                                                yPos - 1,
                                                xPos + width + 2 + (previousDisplayStringWidth - displayStringWidth),
                                                yPos,
                                                rectColor
                                            )

                                            if (module == modules.last()) {
                                                drawRect(
                                                    xPos + width + 1,
                                                    yPos + textSpacer,
                                                    xPos + width + 2,
                                                    yPos + textSpacer + 1,
                                                    rectColor
                                                )
                                                drawRect(
                                                    -1F,
                                                    yPos + textSpacer,
                                                    xPos + width + 2,
                                                    yPos + textSpacer + 1,
                                                    rectColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (displayIcons) {
                    val width = font.getStringWidth(displayString)

                    val side = snap(
                        if (side.horizontal == Side.Horizontal.LEFT) {
                            (-width + module.slide) / 6 + if (rectMode == "Left") 3 else 0
                        } else {
                            -module.slide - 2 + width + if (rectMode == "Right") 0 else 2
                        }
                    )

                    val resource = module.category.iconResourceLocation

                    if (iconShadows) {
                        drawImage(resource, side + xDistance, yPos + yDistance, 12, 12, shadowColor)
                    }

                    val iconColor = if (markAsInactive) {
                        inactiveColor
                    } else when (iconColorMode) {
                        "Gradient" -> 0
                        "Rainbow" -> 0
                        "Fade" -> iconFadeColor
                        else -> this.iconColor.rgb
                    }

                    drawImage(resource, side, yPos, 12, 12, Color(iconColor, true))
                }
            }

            // Draw border
            if (mc.currentScreen is GuiHudDesigner) {
                x2 = Int.MIN_VALUE

                if (modules.isEmpty()) {
                    return if (side.horizontal == Horizontal.LEFT) Border(0F, -1F, 20F, 20F)
                    else Border(0F, -1F, -20F, 20F)
                }

                for (module in modules) {
                    when (side.horizontal) {
                        Horizontal.RIGHT, Horizontal.MIDDLE -> {
                            val xPos = -module.slide.toInt() - 2
                            if (x2 == Int.MIN_VALUE || xPos < x2) x2 = xPos
                        }

                        Horizontal.LEFT -> {
                            val xPos = module.slide.toInt() + 16
                            if (x2 == Int.MIN_VALUE || xPos > x2) x2 = xPos
                        }
                    }
                }

                y2 = (if (side.vertical == Vertical.DOWN) -textSpacer else textSpacer) * modules.size

                return Border(0F, 0F, x2 - 7F, y2 - if (side.vertical == Vertical.DOWN) 1F else 0F)
            }
        }

        resetColor()
        return null
    }

    override fun updateElement() {
        modules = moduleManager.filter { it.slide > 0 && !it.isHidden }
            .sortedBy { -font.getStringWidth(getDisplayString(it)) }
    }
}