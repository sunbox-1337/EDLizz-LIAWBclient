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
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.ui.font.GameFontRenderer
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.extensions.lerpWith
import net.ccbluex.liquidbounce.utils.extensions.safeDiv
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.ColorSettingsInteger
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.withOutline
import net.minecraft.client.gui.ScaledResolution
import java.awt.Color
import kotlin.math.nextDown

@ElementInfo(name = "Keystrokes")
class Keystrokes : Element("Keystrokes", 2.0, 34.0) {
    private val radius by float("RectangleRound-Radius", 3F, 0F..20F)
    private val textColors = ColorSettingsInteger(this, "Text", applyMax = true)
    private val rectColors = ColorSettingsInteger(this, "Rectangle").with(a = 150)
    private val pressColors = ColorSettingsInteger(this, "Press").with(Color.BLUE)
    private val renderBorder by boolean("RenderBorder", false)
    private val borderColors = ColorSettingsInteger(this, "Border") { renderBorder }.with(Color.BLUE)
    private val borderWidth by float("BorderWidth", 1.5F, 0.5F..5F) { renderBorder }
    private val onPressAnimation by choices(
        "OnPressAnimationMode", arrayOf("None", "Shrink", "Fill", "ReverseFill"), "Fill"
    )
    private val shrinkPercentage by int("ShrinkPercentage", 90, 50..100, suffix = "%") { onPressAnimation == "Shrink" }
    private val shrinkSpeed by int("ShrinkSpeed", 2, 0..5, suffix = "Ticks") { onPressAnimation == "Shrink" }

    private var shadow by boolean("Text-Shadow", true)
    private val font by font("Font", Fonts.fontSemibold35)

    // ===== 阴影（裁切 + 自定义颜色）与模糊 =====
    private val showShadow by boolean("Shadow", false)
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { showShadow }
    private val shadowStrength by float("ShadowStrength", 1F, 0.5F..3F) { showShadow }
    private val shadowMask by boolean("ShadowMask", true) { showShadow }

    private val textColor
        get() = textColors.color()

    private val rectColor
        get() = rectColors.color()

    private val pressColor
        get() = pressColors.color()

    private val borderColor
        get() = borderColors.color()

    private val nonFillModes = arrayOf("None", "Shrink")

    data class GridKey(
        val row: Int,
        val column: Int,
        val text: String,
        var scale: Float = 1f,
        val keystrokes: Keystrokes,
        var color: Color = keystrokes.rectColor,
        var normalT: Float = 0F
    ) {
        fun updateState(isPressed: Boolean) {
            val min = (keystrokes.shrinkPercentage / 100f).nextDown()
            val targetScale = if (isPressed && keystrokes.onPressAnimation in keystrokes.nonFillModes) min else 1f
            val deltaTime = RenderUtils.deltaTimeNormalized(keystrokes.shrinkSpeed).takeIf { it != 0.0 } ?: 1F

            normalT = (normalT..if (isPressed) 0f else 1f).lerpWith(RenderUtils.deltaTimeNormalized())

            scale = (scale..targetScale).lerpWith(deltaTime)

            val t = 1f - (scale - min safeDiv 1f - min)

            color = if (keystrokes.onPressAnimation !in keystrokes.nonFillModes) {
                keystrokes.rectColor
            } else {
                ColorUtils.interpolateColor(keystrokes.rectColor, keystrokes.pressColor, t)
            }
        }
    }

    private val GridKey.textWidth: Int
        get() = font.getStringWidth(this.text)

    // row -> column -> key
    private val gridLayout = arrayOf(
        GridKey(1, 1, "W", keystrokes = this),
        GridKey(2, 0, "A", keystrokes = this),
        GridKey(2, 1, "S", keystrokes = this),
        GridKey(2, 2, "D", keystrokes = this),
        GridKey(3, 1, "Space", keystrokes = this)
    )

    override fun drawElement(): Border {
        val options = mc.gameSettings

        val movementKeys = mapOf(
            "Space" to options.keyBindJump,
            "W" to options.keyBindForward,
            "A" to options.keyBindLeft,
            "S" to options.keyBindBack,
            "D" to options.keyBindRight
        )

        val padding = 3F

        val fontHeight = (font as? GameFontRenderer)?.height ?: font.FONT_HEIGHT
        val maxCharWidth = gridLayout.maxOf { it.textWidth }

        val boxSize = maxOf(fontHeight, maxCharWidth)

        // 按键显示是多个独立的圆角矩形（W/A/S/D + Space，中间有间隙），模糊与阴影都按"每个按键"处理
        val keyRects = ArrayList<FloatArray>()
        gridLayout.forEach { gridKey ->
            val gx = gridKey.column * (boxSize + padding)
            val gy = gridKey.row * (boxSize + padding)
            val sx = if (gridKey.row == 3) 0F else gx
            val ex = if (gridKey.row == 3) 2 * (boxSize + padding) + boxSize else gx + boxSize
            keyRects.add(floatArrayOf(sx, gy, ex, gy + boxSize))
        }

        // 背景模糊：每个按键都是独立的圆角矩形，逐个按键做模糊
        if (BlurSettings.active && BlurSettings.keystrokes) {
            val sr = ScaledResolution(mc)
            val screenScale = sr.scaleFactor.toFloat()
            val elementScale = this.scale
            for (r in keyRects) {
                BlurUtils.drawOffsetBlur(
                    ((renderX + r[0]) * elementScale * screenScale).toFloat(),
                    ((renderY + r[1]) * elementScale * screenScale).toFloat(),
                    (r[2] - r[0]) * elementScale * screenScale,
                    (r[3] - r[1]) * elementScale * screenScale,
                    samples = BlurSettings.passes,
                    strength = 0f,
                    radius = radius * elementScale * screenScale
                )
            }
        }

        // 阴影：各按键矩形合并成一张（避免相邻阴影互相渗透），圆角 + 裁切 + 自定义颜色
        if (showShadow) {
            GlowUtils.drawCompositeGlow(
                keyRects,
                (shadowStrength * 13F).toInt(),
                shadowColor,
                mask = shadowMask,
                cornerRadius = radius
            )
        }

        gridLayout.forEach { gridKey ->
            val (row, col, key, scale, _, color) = gridKey

            val currentX = col * (boxSize + padding)
            val currentY = row * (boxSize + padding)

            val (startX, endX) = if (row == 3) {
                // Fill from the first row until the last (Space button)
                0F to 2 * (boxSize + padding) + boxSize
            } else currentX to currentX + boxSize

            val isPressed = movementKeys[key]?.isKeyDown == true
            gridKey.updateState(isPressed)

            val scaledBoxSize = boxSize * if (onPressAnimation == "None") 1f else scale
            val scaledPadding = (boxSize - scaledBoxSize) / 2

            val adjustedStartX = startX + scaledPadding
            val adjustedEndX = endX - scaledPadding
            val adjustedY = currentY + scaledPadding

            RenderUtils.drawRoundedRect(
                adjustedStartX, adjustedY, adjustedEndX, adjustedY + scaledBoxSize, color.rgb, radius
            )

            if (onPressAnimation !in nonFillModes) {
                val reverse = onPressAnimation != "Fill"

                withOutline(main = {
                    val size = boxSize * gridKey.normalT.let { if (!reverse) 1 - it else it }
                    val padding1 = (boxSize - size) / 2

                    val adjustedStartX1 = startX + padding1
                    val adjustedEndX1 = endX - padding1
                    val adjustedY1 = currentY + padding1

                    RenderUtils.drawRoundedRect(
                        adjustedStartX1,
                        adjustedY1,
                        adjustedEndX1,
                        adjustedY1 + size,
                        if (reverse) 0 else pressColor.rgb,
                        radius
                    )
                }, toOutline = {
                    RenderUtils.drawRoundedRect(
                        adjustedStartX,
                        adjustedY,
                        adjustedEndX,
                        adjustedY + scaledBoxSize,
                        if (reverse) pressColor.rgb else 0,
                        radius
                    )
                })
            }

            if (renderBorder) {
                RenderUtils.drawRoundedBorder(
                    adjustedStartX,
                    adjustedY,
                    adjustedEndX,
                    adjustedY + scaledBoxSize,
                    borderWidth,
                    borderColor.rgb,
                    radius
                )
            }

            val textX = (adjustedStartX + adjustedEndX) / 2 - (font.getStringWidth(key) / 2)
            val textY = adjustedY + (scaledBoxSize / 2) - (fontHeight / 2)

            font.drawString(key, textX, textY + if (font == mc.fontRendererObj) 0 else 2, textColor.rgb, shadow)
        }

        return Border(0F, boxSize + padding, boxSize * 3 + padding * 2, boxSize * 4 + padding * 3)
    }
}
