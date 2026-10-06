package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.ui.client.hud.HUD.addNotification
import net.ccbluex.liquidbounce.ui.client.hud.HUD.notifications
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.Side
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification.Companion.maxTextLength
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.client.ClientUtils
import net.ccbluex.liquidbounce.utils.extensions.lerpWith
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorder
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation
import java.awt.Color

@ElementInfo(name = "Notifications", single = true, priority = -1)
class Notifications(
    x: Double = 0.0, y: Double = 30.0, scale: Float = 1F, side: Side = Side(Side.Horizontal.RIGHT, Side.Vertical.DOWN)
) : Element("Notifications", x, y, scale, side) {

    val styles by choices("Styles", arrayOf("Liquidbounce", "Classic"), "Liquidbounce")

    // ===== 阴影选项（对所有风格开放） =====
    val shadowCheck by boolean("ShadowCheck", true)
    val shadowStrength by int("ShadowStrength", 1, 1..2) { shadowCheck }
    val shadowColor by color("ShadowColor", Color(0, 0, 0, 120)) { shadowCheck }
    val shadowSpread by float("ShadowSpread", 1F, 0.5F..3F) { shadowCheck }
    val shadowOnlyBorder by boolean("ShadowOnlyBorder", false) { shadowCheck }
    val shadowMask by boolean("ShadowMask", true) { shadowCheck && !shadowOnlyBorder }

    val horizontalFade by choices("HorizontalFade", arrayOf("InOnly", "OutOnly", "Both", "None"), "OutOnly")
    val padding by int("Padding", 5, -1..20)
    val roundRadius by float("RoundRadius", 3f, 0f..20f)
    val color by color("BackgroundColor", Color.BLACK.withAlpha(128))
    val renderBorder by boolean("RenderBorder", false)
    val borderColor by color("BorderColor", Color.BLUE.withAlpha(255)) { renderBorder }
    val borderWidth by float("BorderWidth", 2f, 0.5F..5F) { renderBorder }

    private val exampleNotification = Notification("Example Title", "Example Description")
    private var index = 0

    override fun updateElement() {
        if (mc.currentScreen is GuiHudDesigner && ClientUtils.runTimeTicks % 60 == 0) {
            exampleNotification.severityType = SeverityType.entries[++index % SeverityType.entries.size]
        }
    }

    override fun drawElement(): Border? {
        var verticalOffset = 0f
        maxTextLength = maxOf(100, notifications.maxOfOrNull { it.textLength } ?: 0)

        val sr = ScaledResolution(mc)
        val screenScale = sr.scaleFactor.toFloat()
        val elemRenderX = renderX.toFloat()
        val elemRenderY = renderY.toFloat()
        val elemScale = this.scale

        notifications.removeIf { notification ->
            if (notification != exampleNotification) {
                notification.y = (notification.y..verticalOffset).lerpWith(RenderUtils.deltaTimeNormalized())
            }

            // 模糊背景
            if (BlurSettings.active && BlurSettings.notifications) {
                val (localX, localY, localW, localH) = getNotificationBounds(notification)
                val blurX = ((elemRenderX + localX) * elemScale) * screenScale
                val blurY = ((elemRenderY + localY) * elemScale) * screenScale
                val blurW = localW * elemScale * screenScale
                val blurH = localH * elemScale * screenScale
                BlurUtils.drawOffsetBlur(blurX, blurY, blurW, blurH, BlurSettings.passes, 0f, roundRadius * elemScale * screenScale)
            }

            notification.drawNotification(this).also { if (!it) verticalOffset += Notification.MAX_HEIGHT + padding }
        }

        if (mc.currentScreen is GuiHudDesigner) {
            if (exampleNotification !in notifications) {
                index = 0
                addNotification(exampleNotification)
            }
            exampleNotification.fadeState = Notification.FadeState.STAY
            exampleNotification.textLength = Fonts.fontSemibold40.getStringWidth(exampleNotification.longestString)
            val notificationHeight = Notification.MAX_HEIGHT
            exampleNotification.y = 0F
            return Border(-(maxTextLength.toFloat() + 24 + 20), -notificationHeight.toFloat(), 0F, 0F)
        }

        return null
    }

    private fun getNotificationBounds(notification: Notification): Quad {
        val currentX = calcCurrentX(notification)
        return if (styles == "Liquidbounce") {
            val extraSpace = 4f
            Quad(
                x = -currentX - extraSpace,
                y = -notification.y - Notification.MAX_HEIGHT,
                w = currentX + extraSpace,
                h = Notification.MAX_HEIGHT.toFloat()
            )
        } else {
            val extraSpace = 4f
            val txWd = Fonts.fontGoogleSans45.getStringWidth(notification.title + ' ' + notification.description)
            val ofst = 145f
            val x1 = -currentX - extraSpace - txWd - 15f + ofst
            val y1 = -notification.y - Notification.MAX_HEIGHT + 2f
            val x2 = -currentX - extraSpace - 1f + ofst
            val y2 = -notification.y - 7f
            Quad(x = x1, y = y1, w = x2 - x1, h = y2 - y1)
        }
    }

    private fun calcCurrentX(notification: Notification): Float {
        return when (notification.fadeState) {
            Notification.FadeState.IN ->
                if (horizontalFade in arrayOf("InOnly", "Both")) notification.x
                else notification.textLength + Notification.ICON_SIZE + 16f
            Notification.FadeState.OUT ->
                if (horizontalFade in arrayOf("OutOnly", "Both")) notification.x
                else notification.textLength + Notification.ICON_SIZE + 16f
            else -> notification.x
        }
    }

    data class Quad(val x: Float, val y: Float, val w: Float, val h: Float)

    enum class SeverityType(val path: ResourceLocation) {
        SUCCESS(ResourceLocation("${CLIENT_NAME.lowercase()}/notifications/success.png")),
        RED_SUCCESS(ResourceLocation("${CLIENT_NAME.lowercase()}/notifications/redsuccess.png")),
        INFO(ResourceLocation("${CLIENT_NAME.lowercase()}/notifications/info.png")),
        WARNING(ResourceLocation("${CLIENT_NAME.lowercase()}/notifications/warning.png")),
        ERROR(ResourceLocation("${CLIENT_NAME.lowercase()}/notifications/error.png"))
    }
}

class Notification(
    var title: String,
    var description: String,
    private val delay: Long = 2000L,
    var severityType: Notifications.SeverityType = Notifications.SeverityType.INFO
) {
    var x = 0F
    var y: Float = (notifications.lastOrNull()?.y ?: 0F) + MAX_HEIGHT * 2
    var textLength = 0

    val longestString
        get() = arrayOf(title, description).maxBy { Fonts.fontSemibold40.getStringWidth(it) }

    private var stay = delay
    private var fadeStep = 0F
    var fadeState = FadeState.IN

    fun replaceModuleNotification(title: String, description: String, severityType: Notifications.SeverityType) {
        if (fadeState.ordinal > 1) return
        stay = delay
        this.severityType = severityType
        this.title = title
        this.description = description
        textLength = Fonts.fontSemibold40.getStringWidth(longestString)
        maxTextLength = maxOf(textLength, maxTextLength)
        notifications.sortBy { it.stay }
    }

    companion object {
        fun informative(title: String, message: String, delay: Long = 2000L) =
            Notification(title, message, delay, Notifications.SeverityType.INFO)
        fun informative(title: Module, message: String, delay: Long = 2000L) =
            Notification(title.spacedName, message, delay, Notifications.SeverityType.INFO)
        fun error(title: Module, message: String, delay: Long = 2000L) =
            Notification(title.spacedName, message, delay, Notifications.SeverityType.ERROR)
        fun warning(title: Module, message: String, delay: Long = 2000L) =
            Notification(title.spacedName, message, delay, Notifications.SeverityType.WARNING)

        var maxTextLength = 0
        const val MAX_HEIGHT = 32
        const val ICON_SIZE = 24
    }

    enum class FadeState { IN, STAY, OUT, END }

    init {
        textLength = Fonts.fontSemibold40.getStringWidth(longestString)
        maxTextLength = maxOf(maxTextLength, textLength)
    }

    fun drawNotification(element: Notifications): Boolean {
        when (element.styles) {
            "Liquidbounce" -> {
                val notificationWidth = maxTextLength + ICON_SIZE + 16F
                val extraSpace = 4F

                val currentX = when (fadeState) {
                    FadeState.IN -> if (element.horizontalFade in arrayOf("InOnly", "Both")) x else notificationWidth
                    FadeState.OUT -> if (element.horizontalFade in arrayOf("OutOnly", "Both")) x else notificationWidth
                    else -> x
                }

                // ===== 阴影（在背景前绘制，两种风格都支持） =====
                if (element.shadowCheck) {
                    GlowUtils.drawGlow(
                        0F,
                        -y - MAX_HEIGHT,
                        currentX + extraSpace,
                        MAX_HEIGHT.toFloat(),
                        (element.shadowStrength * 13F * element.shadowSpread).toInt(),
                        element.shadowColor,
                        cornerRadius = element.roundRadius,
                        onlyBorder = element.shadowOnlyBorder,
                        mask = element.shadowMask
                    )
                }

                drawRoundedRect(0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y, element.color.rgb, element.roundRadius)

                if (element.renderBorder) {
                    drawRoundedBorder(0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y, element.borderWidth, element.borderColor.rgb, element.roundRadius)
                }

                val nearTopSpot = -y - MAX_HEIGHT + 10
                Fonts.fontSemibold40.drawString(title, ICON_SIZE + 8F - currentX, nearTopSpot - 5, Color.WHITE.rgb)
                Fonts.fontSemibold35.drawString(description, ICON_SIZE + 8F - currentX, nearTopSpot + Fonts.fontSemibold40.fontHeight - 2, Int.MAX_VALUE)
                RenderUtils.drawImage(severityType.path, -currentX + 2, -y - MAX_HEIGHT + 4, ICON_SIZE, ICON_SIZE, radius = element.roundRadius)

                val delta = deltaTime
                when (fadeState) {
                    FadeState.IN -> {
                        if (x < notificationWidth) x += delta
                        if (x >= notificationWidth) {
                            fadeState = FadeState.STAY; x = notificationWidth; fadeStep = notificationWidth
                        }
                        stay = delay
                    }
                    FadeState.STAY -> {
                        if (textLength != maxTextLength) {
                            maxTextLength = maxOf(textLength, maxTextLength)
                            x = maxTextLength + ICON_SIZE + 16F; fadeStep = x
                        }
                        stay -= delta
                        if (stay <= 0) fadeState = FadeState.OUT
                    }
                    FadeState.OUT -> if (x > 0) { x -= delta; y -= delta / 4F } else fadeState = FadeState.END
                    FadeState.END -> return true
                }
                return false
            }
            "Classic" -> {
                val notificationWidth = maxTextLength + ICON_SIZE + 16F
                val extraSpace = 4F
                val currentX = when (fadeState) {
                    FadeState.IN -> if (element.horizontalFade in arrayOf("InOnly", "Both")) x else notificationWidth
                    FadeState.OUT -> if (element.horizontalFade in arrayOf("OutOnly", "Both")) x else notificationWidth
                    else -> x
                }
                val ofst = 145f

                val (backgroundColor, borderColor, textColor) = when (severityType) {
                    Notifications.SeverityType.SUCCESS -> Triple(
                        Color(28, 148, 97).withAlpha(element.color.alpha.coerceAtLeast(180)),
                        Color(46, 170, 80).withAlpha(230),
                        Color.WHITE
                    )
                    Notifications.SeverityType.RED_SUCCESS -> Triple(
                        Color(137, 39, 39).withAlpha(element.color.alpha.coerceAtLeast(180)),
                        Color(229, 57, 53).withAlpha(230),
                        Color.WHITE
                    )
                    Notifications.SeverityType.INFO -> Triple(
                        Color(52, 152, 219).withAlpha(element.color.alpha.coerceAtLeast(180)),
                        Color(41, 128, 185).withAlpha(230),
                        Color.WHITE
                    )
                    Notifications.SeverityType.WARNING -> Triple(
                        Color(255, 193, 7).withAlpha(element.color.alpha.coerceAtLeast(180)),
                        Color(245, 166, 35).withAlpha(230),
                        Color(33, 33, 33)
                    )
                    Notifications.SeverityType.ERROR -> Triple(
                        Color(239, 83, 80).withAlpha(element.color.alpha.coerceAtLeast(180)),
                        Color(222, 50, 50).withAlpha(230),
                        Color.WHITE
                    )
                }

                val txWd = Fonts.fontGoogleSans45.getStringWidth(title + ' ' + description)

                // Classic 阴影
                if (element.shadowCheck) {
                    GlowUtils.drawGlow(
                        -currentX - extraSpace - txWd - 15f + ofst, -y - MAX_HEIGHT + 2f,
                        txWd + 17f, MAX_HEIGHT - 9f,
                        (element.shadowStrength * 13F * element.shadowSpread).toInt(),
                        element.shadowColor,
                        cornerRadius = element.roundRadius,
                        onlyBorder = element.shadowOnlyBorder,
                        mask = element.shadowMask
                    )
                }

                drawRoundedRect(-currentX - extraSpace - txWd - 15f + ofst, -y - MAX_HEIGHT + 2f, -currentX - extraSpace - 1F + ofst, -y - 7f, backgroundColor.rgb, element.roundRadius)

                if (element.renderBorder) {
                    drawRoundedBorder(-currentX - extraSpace - txWd - 15f, -y - MAX_HEIGHT, -currentX - extraSpace, -y, element.borderWidth, borderColor.rgb, element.roundRadius)
                }
                val nearTopSpot = -y - MAX_HEIGHT + 10
                Fonts.fontGoogleSans45.drawString(title + ' ' + description + '!', -currentX - extraSpace - txWd - 8f + ofst, nearTopSpot + 5 - 6F, textColor.rgb)
                val delta = deltaTime
                when (fadeState) {
                    FadeState.IN -> {
                        if (x < notificationWidth) x += delta
                        if (x >= notificationWidth) { fadeState = FadeState.STAY; x = notificationWidth; fadeStep = notificationWidth }
                        stay = delay
                    }
                    FadeState.STAY -> {
                        if (textLength != maxTextLength) { maxTextLength = maxOf(textLength, maxTextLength); x = maxTextLength + ICON_SIZE + 16F; fadeStep = x }
                        stay -= delta
                        if (stay <= 0) fadeState = FadeState.OUT
                    }
                    FadeState.OUT -> if (x > 0) { x -= delta; y -= delta / 4F } else fadeState = FadeState.END
                    FadeState.END -> return true
                }
                return false
            }
        }
        return false
    }
}