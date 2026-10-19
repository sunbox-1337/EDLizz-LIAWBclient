//2025.11.23 13:50 latest edit by RainRuin (Background & Blur add)
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.HypixelAimbot
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Target
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.extras.ColorUtils
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.opengl.GL11.*
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.withClipping
import java.awt.Color
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.*
import net.minecraft.util.EnumChatFormatting.BOLD
import net.ccbluex.liquidbounce.utils.render.Stencil
import net.ccbluex.liquidbounce.utils.render.animation.AnimationUtil
import net.minecraft.util.ResourceLocation
import net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting
import net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.potion.Potion
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings

object TargetHUD : Module("TargetHUD", Category.RENDER) {

    private val hudStyle by choices(
        "Style",
        arrayOf(
            "Lizz", "Novoline", "Compact", "Rise", "Southside",
            "Styles", "戶籍", "Myau", "Naven", "Exhibition", "Opai", "RiseModern", "Liawb"
        ),
        "RiseModern"
    )
    private val posX by int("PosX", 0, -400..400)
    private val posY by int("PosY", 0, -400..400)
    private val animSpeed by float("AnimationSpeed", 0.1F, 0.01F..0.5F)

    // 背景自定义
    private val backgroundColor by color("BackgroundColor", Color(30, 30, 30))
    private val backgroundAlpha by int("BackgroundAlpha", 180, 0..255)

    // 各样式专用设置 (完整保留，未删减)
    private val riseModernThemeColor by color("RiseModern-ThemeColor", Color(70, 130, 255)) { hudStyle == "RiseModern" }
    private val riseModernGradientColor2 by color("RiseModern-GradientColor2", Color(140, 200, 255)) { hudStyle == "RiseModern" }
    private val riseModernWLColor by boolean("RiseModern-WLColor", false) { hudStyle == "RiseModern" }

    private val novolineColorMode by choices("Novoline-Color", arrayOf("Custom", "Health", "Rainbow"), "Health") { hudStyle == "Novoline" }
    private val novolineColorRed by int("Novoline-Red", 0, 0..255) { hudStyle == "Novoline" && novolineColorMode == "Custom" }
    private val novolineColorGreen by int("Novoline-Green", 120, 0..255) { hudStyle == "Novoline" && novolineColorMode == "Custom" }
    private val novolineColorBlue by int("Novoline-Blue", 255, 0..255) { hudStyle == "Novoline" && novolineColorMode == "Custom" }
    private val novolineColorSpec by boolean("Novoline-Gradient", true) { hudStyle == "Novoline" }
    private val novolineLeftColor by color("Novoline-left-Color", Color(0, 255, 150)) { novolineColorSpec }
    private val novolineRightColor by color("Novoline-right-Color", Color(10, 80, 120)) { novolineColorSpec }

    private val lizzColorMode by choices("Lizz-Color", arrayOf("Custom", "Health"), "Health") { hudStyle == "Lizz" }
    private val lizzColorRed by int("Lizz-Red", 76, 0..255) { hudStyle == "Lizz" && lizzColorMode == "Custom" }
    private val lizzColorGreen by int("Lizz-Green", 157, 0..255) { hudStyle == "Lizz" && lizzColorMode == "Custom" }
    private val lizzColorBlue by int("Lizz-Blue", 240, 0..255) { hudStyle == "Lizz" && lizzColorMode == "Custom" }

    private val stylesShadow by boolean("Styles-Shadow", true) { hudStyle == "Styles" }

    private val arcRainbow by boolean("Arc-Rainbow", true) { hudStyle == "Arc" }
    private val arcColorRed by int("Arc-Red", 255, 0..255) { hudStyle == "Arc" && !arcRainbow }
    private val arcColorGreen by int("Arc-Green", 255, 0..255) { hudStyle == "Arc" && !arcRainbow }
    private val arcColorBlue by int("Arc-Blue", 255, 0..255) { hudStyle == "Arc" && !arcRainbow }

    private val rainbow by boolean("Myau-Rainbow", true) { hudStyle == "Myau" }
    private val borderRed by int("Myau-Border-Red", 255, 0..255) { hudStyle == "Myau" }
    private val borderGreen by int("Myau-Border-Green", 255, 0..255) { hudStyle == "Myau" }
    private val borderBlue by int("Myau-Border-Blue", 255, 0..255) { hudStyle == "Myau" }
    private val showAvatar by boolean("Myau-Show-Avatar", true) { hudStyle == "Myau" }

    private val barColorR by int("RavenB4-BarColorR", 255, 0..255) { hudStyle == "RavenB4" }
    private val barColorG by int("RavenB4-BarColorG", 255, 0..255) { hudStyle == "RavenB4" }
    private val barColorB by int("RavenB4-BarColorB", 255, 0..255) { hudStyle == "RavenB4" }
    private val animSpeedRB4 by int("RavenB4-AnimSpeed", 3, 1..10) { hudStyle == "RavenB4" }

    private val riseBarColorR by int("Rise-BarR", 70, 0..255) { hudStyle == "Rise" }
    private val riseBarColorG by int("Rise-BarG", 130, 0..255) { hudStyle == "Rise" }
    private val riseBarColorB by int("Rise-BarB", 255, 0..255) { hudStyle == "Rise" }
    private val riseBGColorR by int("Rise-BGR", 30, 0..255) { hudStyle == "Rise" }
    private val riseBGColorG by int("Rise-BGG", 30, 0..255) { hudStyle == "Rise" }
    private val riseBGColorB by int("Rise-BGB", 30, 0..255) { hudStyle == "Rise" }
    private val riseBGColorA by int("Rise-BGA", 180, 0..255) { hudStyle == "Rise" }
    private val riseAnimSpeed by int("Rise-AnimSpeed", 4, 1..10) { hudStyle == "Rise" }
    private val riseShadowCheck by boolean("Shadow", true) { hudStyle == "Rise" }
    private val riseGradient by boolean("Gradient", true) { hudStyle == "Rise" }
    private val riseBGColor2 by color("Rise-Gradient-Color2", Color(70, 130, 255)) { hudStyle == "Rise" && riseGradient }

    private val opaiThemeColor by color("Opai-themeColor", Color(242, 172, 244)) { hudStyle == "Opai" }
    private val opaiShadowCheck by boolean("Opai-ShadowCheck", false) { hudStyle == "Opai" }
    private val opaiShadowStrengh by float("Opai-shadowStrengh", 0.5f, 0.0f..1.0f) { hudStyle == "Opai" && opaiShadowCheck }
    private val opaiVanishDelay by int("Opai-VanishDelay", 300, 0..500) { hudStyle == "Opai" }

    // ================= Liawb 模式设置 =================
    private val liawbRadius by float("Liawb-Radius", 8f, 0f..20f) { hudStyle == "Liawb" }
    private val liawbBackgroundColor by color("Liawb-BG", Color(20, 20, 25, 180)) { hudStyle == "Liawb" }
    private val liawbTextColor by color("Liawb-TextColor", Color.WHITE) { hudStyle == "Liawb" }
    private val liawbRingRadius by float("Liawb-RingRadius", 15f, 8f..28f) { hudStyle == "Liawb" }
    private val liawbRingThickness by float("Liawb-RingThickness", 4f, 2f..8f) { hudStyle == "Liawb" }
    private val liawbRingColor by color("Liawb-RingActive", Color(80, 255, 120)) { hudStyle == "Liawb" }
    private val liawbRingBackColor by color("Liawb-RingInactive", Color(60, 60, 70, 200)) { hudStyle == "Liawb" }
    private val liawbNameFont by font("Liawb-NameFont", Fonts.fontSemibold40) { hudStyle == "Liawb" }
    private val liawbInfoFont by font("Liawb-InfoFont", Fonts.fontSemibold35) { hudStyle == "Liawb" }
    private val liawbShowMainHand by boolean("Liawb-MainHand", true) { hudStyle == "Liawb" }
    private val liawbShowArmor by boolean("Liawb-Armor", true) { hudStyle == "Liawb" }
    private val liawbArmorNumbers by boolean("Liawb-ArmorNumbers", true) { hudStyle == "Liawb" && liawbShowArmor }
    private val liawbArmorRing by boolean("Liawb-ArmorTotalRing", true) { hudStyle == "Liawb" && liawbShowArmor }
    private val liawbArmorRingColor by color("Liawb-ArmorRingActive", Color(120, 180, 255)) { hudStyle == "Liawb" && liawbShowArmor && liawbArmorRing }
    private val liawbArmorRingBackColor by color("Liawb-ArmorRingInactive", Color(60, 60, 70, 200)) { hudStyle == "Liawb" && liawbShowArmor && liawbArmorRing }
    private val liawbShowPotions by boolean("Liawb-Potions", true) { hudStyle == "Liawb" }
    private val liawbShadowCheck by boolean("Liawb-Shadow", true) { hudStyle == "Liawb" }
    private val liawbShadowStrength by float("Liawb-ShadowStrength", 0.5f, 0f..2f) { hudStyle == "Liawb" && liawbShadowCheck }
    private val liawbShadowColor by color("Liawb-ShadowColor", Color(0, 0, 0, 120)) { hudStyle == "Liawb" && liawbShadowCheck }
    private val liawbShadowMask by boolean("Liawb-ShadowMask", true) { hudStyle == "Liawb" && liawbShadowCheck }
    // 出现/消失弹性动画：Bounce = 透明淡入 + 从小到大 → 略小 → 过冲 → 回落；消失反向
    private val liawbAnimMode by choices("Liawb-Animation", arrayOf("Bounce", "None"), "Bounce") { hudStyle == "Liawb" }
    // HUD 整体大小
    private val liawbUiScale by float("Liawb-UIScale", 1f, 0.6f..1.6f) { hudStyle == "Liawb" }
    // 名字顶部底条（可选 + 可选颜色）
    private val liawbNameBar by boolean("Liawb-NameBar", false) { hudStyle == "Liawb" }
    private val liawbNameBarColor by color("Liawb-NameBarColor", Color(0, 0, 0, 120)) { hudStyle == "Liawb" && liawbNameBar }
    // =============== Liawb 模式设置结束 ===============

    private val riseNewShadowCheck by boolean("RiseNew-Shadow", true) { hudStyle == "RiseNew" }

    private val neonGlow by boolean("Neon-Glow", true) { hudStyle == "Neon" }
    private val neonColor = Color(76, 140, 240)

    private val decimalFormat = DecimalFormat("0.0", DecimalFormatSymbols(Locale.ENGLISH))
    private var target: EntityLivingBase? = null
    private var lastTarget: EntityLivingBase? = null
    override var hue = 0.0f

    private var easingHealth = 0F
    private var moon4EasingHealth = 0F
    private var southsideEasingHealth = 0F
    private var slideIn = 0F
    private var damageHealth = 0F
    private var stylesEasingHealth = 0F
    private var NavenEasingHealth = 0F
    private var opaiDelayCounter = 0
    private var opaiAnimX = 135F
    var AnimX = 100F

    override fun onEnable() {
        easingHealth = 0F; moon4EasingHealth = 0F; southsideEasingHealth = 0f
        slideIn = 0F; damageHealth = 0f; hue = 0.0f
        target = null; lastTarget = null
        opaiDelayCounter = 0; opaiAnimX = 135F
        stylesEasingHealth = 0F; NavenEasingHealth = 0F
    }

    // 模糊辅助函数
    private fun drawHudBlur(x: Float, y: Float, width: Float, height: Float, radius: Float = 0f) {
        if (BlurSettings.active && BlurSettings.targetHUD) {
            val sr = ScaledResolution(mc)
            val scale = sr.scaleFactor.toFloat()
            BlurUtils.drawOffsetBlur(
                x * scale, y * scale, width * scale, height * scale,
                BlurSettings.passes, 0f, radius * scale
            )
        }
    }

    private fun getBackgroundColor(): Int {
        return Color(backgroundColor.red, backgroundColor.green, backgroundColor.blue, backgroundAlpha).rgb
    }

    // Southside easing
    private fun updateSouthsideEasingHealth(targetHealth: Float, maxHealth: Float) {
        val changeAmount = abs(southsideEasingHealth - targetHealth)
        var speed = 0.02f * deltaTime
        if (changeAmount > 5) speed *= 2.0f
        else if (changeAmount > 2) speed *= 1.5f
        if (abs(southsideEasingHealth - targetHealth) < 0.1f) southsideEasingHealth = targetHealth
        else if (southsideEasingHealth > targetHealth)
            southsideEasingHealth -= min(speed * 1.2f, southsideEasingHealth - targetHealth)
        else southsideEasingHealth += min(speed, targetHealth - southsideEasingHealth)
        southsideEasingHealth = southsideEasingHealth.coerceAtMost(maxHealth)
    }

    val onRender2D = handler<Render2DEvent> {
        // 状态保护：防止 TargetHUD 影响其他模块
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        GlStateManager.pushMatrix()

        val kaTarget = KillAura.target ?: HypixelAimbot.target?.takeIf { HypixelAimbot.state }
        if (kaTarget != null && kaTarget is EntityPlayer && !AntiBot.isBot(kaTarget)) {
            target = kaTarget
        } else if (mc.currentScreen is GuiChat) {
            target = mc.thePlayer
        } else if (target != null && (KillAura.target == null || !target!!.isEntityAlive || AntiBot.isBot(target!!))) {
            target = null
        }
        if (target != lastTarget) {
            if (lastTarget != null) {
                easingHealth = lastTarget!!.health; damageHealth = lastTarget!!.health
            } else if (target != null) {
                easingHealth = target!!.health; damageHealth = target!!.health
            }
            if (target != null) {
                moon4EasingHealth = target!!.health; southsideEasingHealth = target!!.health
            }
        }
        lastTarget = target
        hue += 0.05f * deltaTime * 0.1f
        if (hue > 1F) hue = 0F
        slideIn = lerp(slideIn, if (target != null) 1F else 0F, animSpeed)
        // Liawb 弹性动画需要自己控制消失过程，绕过 slideIn 的提前 return
        val liawbFadingOut = hudStyle == "Liawb" && liawbAnimMode == "Bounce" && liawbAlpha > 0.01F
        if (slideIn < 0.01F && target == null && !liawbFadingOut) {
            GlStateManager.popMatrix()
            glPopAttrib()
            return@handler
        }

        val sr = ScaledResolution(mc)
        val x = sr.scaledWidth / 2F + posX
        val y = sr.scaledHeight / 2F + posY

        when (hudStyle.lowercase(Locale.getDefault())) {
            "lizz" -> renderLizzHUD(x, y)
            "novoline" -> renderNovolineHUD(x, y)
            "arc" -> renderArcHUD(x, y)
            "compact" -> renderCompactHUD(x, y)
            "rise" -> renderMoon4HUD(x, y)
            "southside" -> renderSouthsideHUD(x, y)
            "styles" -> renderStylesHUD(sr)
            "戶籍" -> render0x01a4HUD(sr)
            "myau" -> renderMyauHUD(sr)
            "ravenb4" -> renderRavenB4HUD(sr)
            "naven" -> renderNavenHUD(sr)
            "neon" -> renderNeonHUD(x, y)
            "exhibition" -> renderExhibitionHUD(x, y)
            "opai" -> renderOpaiHUD(sr)
            "risenew" -> renderRiseNewHUD(x, y)
            "risemodern" -> renderRiseModernHUD(x, y)
            "liawb" -> renderLiawbHUD(x, y)
        }

        GlStateManager.popMatrix()
        glPopAttrib()
    }

    // ================= 各样式渲染函数 =================
    private fun renderRiseModernHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        NavenEasingHealth = lerp(NavenEasingHealth, entity.health, animSpeed)
        val targetHurtTime = if (entity.isEntityAlive) entity.hurtTime.toFloat() else 0F
        val easingHurtTime = lerp(0F, targetHurtTime, 0.3F)
        val width = 150F; val height = 30F

        drawHudBlur(x, y, width * slideIn, height * slideIn, 3f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        ShowShadow(0F, 0F, width, height, 1F)
        drawRoundedRect(0F, 0F, width, height, getBackgroundColor(), 3f)

        val themeColor = if (riseModernWLColor) {
            when {
                mc.thePlayer.health > entity.health -> Color(0, 255, 0)
                mc.thePlayer.health < entity.health -> Color(255, 0, 0)
                else -> Color(255, 255, 0)
            }
        } else riseModernThemeColor

        RenderUtils.drawRoundedBorderRect(0F, 0F, width, height, 2.5F, Color(0, 0, 0, 0).rgb, themeColor.rgb, 3f)

        val avatarSize = 24F; val padding = 3F
        val avatarX = padding; val avatarY = (height - avatarSize) / 2
        val scale = 1 - easingHurtTime / 10f
        val deepRed = Color(255, 0, 0, 200)
        val headColor = ColorUtils.interpolateColor(deepRed, Color.WHITE, scale.coerceIn(0f, 1f))
        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            drawRoundedHead(it.locationSkin, avatarX.toInt(), avatarY.toInt(), avatarSize.toInt(), avatarSize.toInt(), headColor, 3f)
        }

        val rightAreaX = avatarX + avatarSize + padding; val rightAreaWidth = width - rightAreaX - padding
        Fonts.fontGoogleSans40.drawString(entity.name, rightAreaX, 5F, Color.WHITE.rgb, false)
        val healthText = "${entity.health.toInt()}"
        val healthTextWidth = Fonts.fontGoogleSans40.getStringWidth(healthText)
        Fonts.fontGoogleSans40.drawString(healthText, width - padding - healthTextWidth, 5F, themeColor.rgb, false)

        val barY = height - padding - 6F; val barHeight = 6F; val barWidth = rightAreaWidth
        val healthPercent = (NavenEasingHealth / entity.maxHealth).coerceIn(0F, 1F)
        val barFillWidth = barWidth * healthPercent
        drawRoundedRect(rightAreaX, barY, rightAreaX + barWidth, barY + barHeight, Color(50, 50, 50, 150).rgb, 2f)
        if (barFillWidth > 0)
            RenderUtils.drawRoundedGradientRectCorner(rightAreaX, barY, rightAreaX + barFillWidth, barY + barHeight, 2f, themeColor.rgb, riseModernGradientColor2.rgb)

        GlStateManager.popMatrix()
    }

    private fun renderRiseNewHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        NavenEasingHealth = lerp(NavenEasingHealth, entity.health, animSpeed)
        val width = 150F; val height = 30F

        drawHudBlur(x, y, width * slideIn, height * slideIn, 5f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        if (riseNewShadowCheck) ShowShadow(0F, 0F, width, height, 0.3F)
        drawRoundedRect(0F, 0F, width, height, getBackgroundColor(), 5f)

        val themeColor = Color(70, 130, 255); val gradientColor2 = Color(140, 200, 255)
        RenderUtils.drawRoundedBorderRect(0F, 0F, width, height, 1.5F, themeColor.rgb, Color(0, 0, 0, 0).rgb, 5f)

        val avatarSize = 24F; val padding = 3F; val avatarX = padding; val avatarY = (height - avatarSize) / 2
        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            drawRoundedHead(it.locationSkin, avatarX.toInt(), avatarY.toInt(), avatarSize.toInt(), avatarSize.toInt(), Color.WHITE, 4f)
        }

        val rightAreaX = avatarX + avatarSize + padding; val rightAreaWidth = width - rightAreaX - padding
        Fonts.fontGoogleSans40.drawString(entity.name, rightAreaX, 5F, Color.WHITE.rgb, false)
        val healthText = "${entity.health.toInt()}"
        val healthTextWidth = Fonts.fontGoogleSans40.getStringWidth(healthText)
        Fonts.fontGoogleSans40.drawString(healthText, width - padding - healthTextWidth, 5F, themeColor.rgb, false)

        val barY = height - padding - 6F; val barHeight = 6F; val barWidth = rightAreaWidth
        val healthPercent = (NavenEasingHealth / entity.maxHealth).coerceIn(0F, 1F)
        val barFillWidth = barWidth * healthPercent
        drawRoundedRect(rightAreaX, barY, rightAreaX + barWidth, barY + barHeight, Color(50, 50, 50, 150).rgb, 3f)
        if (barFillWidth > 0)
            RenderUtils.drawRoundedGradientRectCorner(rightAreaX, barY, rightAreaX + barFillWidth, barY + barHeight, 3f, themeColor.rgb, gradientColor2.rgb)

        GlStateManager.popMatrix()
    }

    private fun renderExhibitionHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        val width = 120F; val height = 45F; val modelSize = 40F

        drawHudBlur(x, y, width * slideIn, height * slideIn)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        RenderUtils.drawRect(0F, 0F, width, height, getBackgroundColor())
        RenderUtils.drawRect(0F, 0F, width, 1F, Color(40, 40, 40, 255).rgb)
        RenderUtils.drawRect(0F, height - 1F, width, height, Color(40, 40, 40, 255).rgb)
        RenderUtils.drawRect(0F, 0F, 1F, height, Color(40, 40, 40, 255).rgb)
        RenderUtils.drawRect(width - 1F, 0F, width, height, Color(40, 40, 40, 255).rgb)

        val modelX = 2.5F; val modelY = (height - modelSize) / 2
        renderPlayerModel(entity, modelX, modelY, modelSize)

        val infoX = modelX + modelSize + 2F; val infoWidth = width - infoX - 5F
        val name = entity.name; val nameY = 5F
        mc.fontRendererObj.drawStringWithShadow(name, infoX, nameY, Color.WHITE.rgb)
        val healthText = "HP: ${decimalFormat.format(entity.health)}"
        val distance = mc.thePlayer.getDistanceToEntity(entity)
        val distanceText = "Dist: ${decimalFormat.format(distance)}"
        val infoText = "$healthText | $distanceText"
        val infoY = nameY + mc.fontRendererObj.FONT_HEIGHT + 2F
        GlStateManager.pushMatrix(); GlStateManager.translate(infoX, infoY, 0F); GlStateManager.scale(0.8F, 0.8F, 0.8F)
        mc.fontRendererObj.drawStringWithShadow(infoText, 0F, 0F, Color.WHITE.rgb)
        GlStateManager.popMatrix()

        val barY = infoY + 8F; val barHeight = 5F; val barWidth = infoWidth
        val healthPercent = entity.health / entity.maxHealth
        val barColor = when {
            healthPercent > 2.0 / 3.0 -> Color(0, 180, 0)
            healthPercent > 1.0 / 3.0 -> Color(255, 255, 0)
            else -> Color(255, 0, 0)
        }
        RenderUtils.drawRect(infoX, barY, infoX + barWidth, barY + barHeight, Color(40, 40, 40).rgb)
        RenderUtils.drawRect(infoX, barY, infoX + barWidth * healthPercent, barY + barHeight, barColor.rgb)
        var currentX = infoX + 4F
        while (currentX < infoX + barWidth) {
            RenderUtils.drawRect(currentX, barY, currentX + 1F, barY + barHeight, Color(10, 10, 10).rgb)
            currentX += 5F
        }
        GlStateManager.popMatrix()
    }

    private fun renderPlayerModel(entity: EntityLivingBase, x: Float, y: Float, size: Float) {
        GlStateManager.pushMatrix()
        GlStateManager.translate(x + size / 2, y + size, 0F)
        GlStateManager.scale(size / 2, size / 2, size / 2)
        GlStateManager.rotate(180F, 0F, 0F, 1F)
        GlStateManager.rotate(135F, 0F, 1F, 0F)
        RenderHelper.enableStandardItemLighting()
        GlStateManager.enableDepth()
        GlStateManager.disableCull()
        mc.renderManager.setRenderShadow(false)
        try { mc.renderManager.renderEntityWithPosYaw(entity, 0.0, 0.0, 0.0, 0F, 1.0F) }
        catch (e: Exception) { RenderUtils.drawRect(x, y, x + size, y + size, Color.RED.rgb) }
        mc.renderManager.setRenderShadow(true)
        GlStateManager.enableCull()
        GlStateManager.disableDepth()
        RenderHelper.disableStandardItemLighting()
        GlStateManager.popMatrix()
    }

    private fun renderLizzHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        easingHealth = lerp(easingHealth, entity.health, animSpeed)
        val width = 135F; val height = 35F; val avatarSize = 30F; val padding = 1F

        drawHudBlur(x, y, width * slideIn, height * slideIn)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        ShowShadow(0F, 0F, width, height, 0.3F)
        RenderUtils.drawRect(padding, padding, width - padding, height - padding, getBackgroundColor())

        val avatarX = padding + 2F; val avatarY = padding + 2F
        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            Target().drawHead(it.locationSkin, avatarX.toInt(), avatarY.toInt(), avatarSize.toInt(), avatarSize.toInt(), Color.WHITE)
        }
        val rightAreaX = avatarX + avatarSize + 5F; val rightAreaWidth = width - rightAreaX - padding - 2F
        Fonts.fontRegular35.drawString(entity.name, rightAreaX, avatarY + 1, Color.WHITE.rgb)
        val winOrLose = if (mc.thePlayer.health > entity.health) "W" else "L"
        val wlColor = if (winOrLose == "W") Color(0, 255, 0) else Color(255, 0, 0)
        Fonts.fontRegular35.drawString(winOrLose, width - 15F, avatarY + 1, wlColor.rgb)

        val barY = avatarY + 5 + Fonts.fontRegular35.fontHeight + 3F; val barHeight = 8F; val barWidth = rightAreaWidth
        RenderUtils.drawRect(rightAreaX, barY, rightAreaX + barWidth, barY + barHeight, Color(50, 50, 50).rgb)
        val healthPercent = (easingHealth / entity.maxHealth).coerceIn(0F, 1F)
        val barFillWidth = barWidth * healthPercent
        val barColor = when {
            lizzColorMode == "Health" -> when {
                healthPercent > 2F / 3F -> Color(76, 157, 240)
                healthPercent > 1F / 3F -> Color(56, 137, 220)
                else -> Color(36, 117, 200)
            }
            else -> when {
                healthPercent > 2F / 3F -> Color(lizzColorRed, lizzColorGreen, lizzColorBlue)
                healthPercent > 1F / 3F -> Color(max(lizzColorRed - 20, 0), max(lizzColorGreen - 20, 0), max(lizzColorBlue - 20, 0))
                else -> Color(max(lizzColorRed - 40, 0), max(lizzColorGreen - 40, 0), max(lizzColorBlue - 40, 0))
            }
        }
        RenderUtils.drawRect(rightAreaX, barY, rightAreaX + barFillWidth, barY + barHeight, barColor.rgb)
        val healthText = decimalFormat.format(easingHealth)
        val healthTextWidth = Fonts.fontRegular35.getStringWidth(healthText)
        Fonts.fontRegular35.drawString(healthText, rightAreaX + barWidth - healthTextWidth - 2F, barY + 2 + (barHeight - Fonts.fontRegular35.fontHeight) / 2F, Color.WHITE.rgb)
        GlStateManager.popMatrix()
    }

    private fun renderNovolineHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        val width = 120F; val height = 46F
        easingHealth = lerp(easingHealth, entity.health, animSpeed)

        drawHudBlur(x, y, width * slideIn, height * slideIn)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        RenderUtils.drawRoundedBorderRect(1f, 1f, width - 1f, height - 1f, 1f, Color(40, 40, 40, 200).rgb, getBackgroundColor(), 0F)

        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            Target().drawHead(it.locationSkin, 6, 6, 34, 34, Color.WHITE)
        }
        Fonts.minecraftFont.drawString(entity.name, 46, 8, Color.WHITE.rgb)
        val healthPercent = (easingHealth / entity.maxHealth).coerceIn(0F, 1F)
        val barColor = when (novolineColorMode) {
            "Custom" -> Color(novolineColorRed, novolineColorGreen, novolineColorBlue)
            "Rainbow" -> Color.getHSBColor(hue, 0.7f, 0.9f)
            else -> ColorUtils.getHealthColor(easingHealth, entity.maxHealth)
        }
        RenderUtils.drawRect(46F, 22F, width - 6F, 32F, Color(45, 45, 45).rgb)
        if (!novolineColorSpec) RenderUtils.drawRect(46F, 22F, 46F + (width - 52F) * healthPercent, 32F, barColor.rgb)
        else RenderUtils.drawGradientRect(46F, 22F, 46F + (width - 52F) * healthPercent, 32F, novolineLeftColor.rgb, novolineRightColor.rgb, 0f)
        Fonts.minecraftFont.drawString("${decimalFormat.format(healthPercent * 100)}%", 66, 24, Color.WHITE.rgb)
        GlStateManager.popMatrix()
    }

    private fun renderArcHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        val size = 50F
        easingHealth = lerp(easingHealth, entity.health, animSpeed)

        drawHudBlur(x, y, size + Fonts.fontSemibold40.getStringWidth(entity.name) + 5f, size)

        GlStateManager.pushMatrix()
        val scale = slideIn.pow(0.5f)
        GlStateManager.translate(x + size / 2, y + size / 2, 0F)
        GlStateManager.scale(scale, scale, scale)
        GlStateManager.translate(-(x + size / 2), -(y + size / 2), 0F)

        withClipping({
            drawCircle(x + size / 2, y + size / 2, size / 2 - 3, Color.WHITE.rgb)
        }) {
            mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
                Target().drawHead(it.locationSkin, x.toInt() + 3, y.toInt() + 3, (size - 6).toInt(), (size - 6).toInt(), Color.WHITE)
            }
        }

        val healthPercent = (easingHealth / entity.maxHealth).coerceIn(0F, 1F)
        val arcColor = if (arcRainbow) Color.getHSBColor(hue, 0.6f, 1f) else Color(arcColorRed, arcColorGreen, arcColorBlue)
        drawCircleArc(x + size / 2, y + size / 2, size / 2 - 1.5F, 3F, 0F, 360F, Color(40, 40, 40, (200 * slideIn).toInt()))
        if (healthPercent > 0) drawCircleArc(x + size / 2, y + size / 2, size / 2 - 1.5F, 3F, -90F, 360F * healthPercent, arcColor)

        val textX = x + size + 5
        Fonts.fontSemibold40.drawString(entity.name, textX, y + 8, Color(255, 255, 255, (255 * slideIn).toInt()).rgb)
        Fonts.fontSemibold35.drawString("HP: ${decimalFormat.format(easingHealth)}", textX, y + 24, Color(200, 200, 200, (255 * slideIn).toInt()).rgb)
        GlStateManager.popMatrix()
    }

    private fun renderCompactHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        val width = 120F; val height = 18F
        if (target != null) {
            easingHealth = lerp(easingHealth, entity.health, animSpeed * 1.5f)
            if (abs(entity.health - damageHealth) > 0.1f && easingHealth < damageHealth)
                damageHealth = lerp(damageHealth, easingHealth, animSpeed * 0.5f)
            else damageHealth = easingHealth
        } else {
            easingHealth = lerp(easingHealth, 0f, animSpeed)
            damageHealth = lerp(damageHealth, 0f, animSpeed)
        }
        if (target != null && target != lastTarget) damageHealth = entity.maxHealth

        val compactScale = slideIn.pow(2f)
        drawHudBlur(x, y + (height - height * compactScale) / 2f, width, height * compactScale)

        GlStateManager.pushMatrix()
        val scale = compactScale
        GlStateManager.translate(x + width / 2, y + height / 2, 0F)
        GlStateManager.scale(1f, scale, 1f)
        GlStateManager.translate(-(x + width / 2), -(y + height / 2), 0F)
        if (scale >= 0.05f) {
            RenderUtils.drawRect(x, y, x + width, y + height, getBackgroundColor())
            val healthPercent = (easingHealth / entity.maxHealth).coerceIn(0F, 1F)
            val damagePercent = (damageHealth / entity.maxHealth).coerceIn(0F, 1F)
            RenderUtils.drawRect(x, y, x + (width - 4) * damagePercent, y + height, Color(100, 100, 100, (150 * scale).toInt()).rgb)
            RenderUtils.drawRect(x, y, x + (width - 4) * healthPercent, y + height, Color(100, 100, 100, (150 * scale).toInt()).rgb)
            Fonts.fontSemibold40.drawCenteredString("${entity.name} - ${decimalFormat.format(easingHealth)} HP", x + width / 2, y + height / 2 - Fonts.fontSemibold35.fontHeight / 2 + 1, Color.WHITE.rgb, false)
        }
        GlStateManager.popMatrix()
    }

    private fun renderMoon4HUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        val currentHealth = target?.health ?: 0f
        moon4EasingHealth += ((currentHealth - moon4EasingHealth) / 2.0F.pow(10.0F - riseAnimSpeed)) * deltaTime
        val mainColor = Color(riseBarColorR, riseBarColorG, riseBarColorB)
        var boldName = "$BOLD${entity.name}"
        val healthInt = entity.health.toInt()
        val percentText = "${healthInt}.0"
        val healthWidth = Fonts.fontGoogleSans35.getStringWidth(percentText)
        if (mc.thePlayer.health < entity.health) boldName = "Losing: $boldName"
        else if (mc.thePlayer.health > entity.health) boldName = "Winning: $boldName"
        else boldName = "Saming: $boldName"
        val nameLength = (Fonts.fontGoogleSans40.getStringWidth(boldName)).coerceAtLeast(
            Fonts.fontSemibold35.getStringWidth(percentText)
        ).toFloat() + 20F
        val healthPercent = (entity.health / entity.maxHealth).coerceIn(0F, 1F)
        val barWidth = healthPercent * (nameLength - 2F)

        val totalWidth = nameLength + 45F + healthWidth + 2F
        val totalHeight = 36F

        drawHudBlur(x, y, totalWidth * slideIn, totalHeight * slideIn, 10f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        if (riseShadowCheck) ShowShadow(-1F, -1F, 2F + nameLength + 45F + healthWidth, 1F + 36F, 1F)
        drawRoundedRect(-1F, -1F, 2F + nameLength + 45F + healthWidth, 1F + 36F, getBackgroundColor(), 10f)

        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let { playerInfo ->
            Stencil.write(false)
            glDisable(GL_TEXTURE_2D)
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            drawRoundedRect(1f, 0.5f, 1f + 35f, 0.5f + 35f, Color.WHITE.rgb, 7F)
            glDisable(GL_BLEND)
            glEnable(GL_TEXTURE_2D)
            Stencil.erase(true)
            drawRoundedHead(playerInfo.locationSkin, 3, 2, 31, 31, Color.WHITE)
            Stencil.dispose()
        }
        Fonts.fontGoogleSans40.drawString(boldName, 2F + 36F, 7F, -1)
        AnimX = AnimationUtil.base(AnimX.toDouble(), barWidth.toDouble(), 0.2).toFloat()
        drawRoundedRect(38F, 24F, 38F + nameLength, 28f, Color(0, 0, 0, 100).rgb, 4f)
        if (!riseGradient) {
            drawRoundedRect(38F, 24F, 38F + barWidth, 28f, mainColor.rgb, 4f)
        } else {
            RenderUtils.drawRoundedGradientRectCorner(38F, 24F, 38F + barWidth, 28f, 4f, mainColor.rgb, Color(riseBGColor2.red, riseBGColor2.green, riseBGColor2.blue).rgb)
        }
        drawRoundedRect(38F, 24F, 38F + AnimX, 28f, Color(mainColor.red, mainColor.green, mainColor.blue, 50).rgb, 4f)
        Fonts.fontGoogleSans35.drawString(percentText, 2F + nameLength + 40F, 23F, -1)
        GlStateManager.popMatrix()
    }

    private fun renderSouthsideHUD(x: Float, y: Float) {
        val entity = target ?: lastTarget ?: return
        val health = entity.health; val maxHealth = entity.maxHealth
        val healthPercent = (health / maxHealth).coerceIn(0f, 1f)
        updateSouthsideEasingHealth(health, maxHealth)
        val easingHealthPercent = (southsideEasingHealth / maxHealth).coerceIn(0f, 1f)
        val name = entity.name
        val width = Fonts.fontSemibold40.getStringWidth(name) + 75f
        val presentWidth = easingHealthPercent * width

        val animOutput = slideIn
        val transX = (x + width / 2) * (1 - animOutput)
        val transY = (y + 20) * (1 - animOutput)
        drawHudBlur(transX + x * animOutput, transY + y * animOutput, width * animOutput, 40f * animOutput)

        GlStateManager.pushMatrix()
        GlStateManager.translate(transX.toDouble(), transY.toDouble(), 0.0)
        GlStateManager.scale(animOutput, animOutput, animOutput)

        RenderUtils.drawRect(x, y, x + width, y + 40f, getBackgroundColor())
        RenderUtils.drawRect(x, y, x + presentWidth, y + 40f, Color(230, 230, 230, 100).rgb)
        val healthColor = when {
            healthPercent > 0.5 -> Color(63, 157, 4, 150)
            healthPercent > 0.25 -> Color(255, 144, 2, 150)
            else -> Color(168, 1, 1, 150)
        }
        RenderUtils.drawRect(x, y + 12.5f, x + 3f, y + 27.5f, healthColor.rgb)
        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            Target().drawHead(it.locationSkin, x.toInt() + 7, y.toInt() + 7, 26, 26, Color.WHITE)
        } ?: RenderUtils.drawRect(x + 6f, y + 6f, x + 34f, y + 34f, Color.BLACK.rgb)
        Fonts.fontSemibold40.drawString(name, x + 40f, y + 7f, Color(200, 200, 200, 255).rgb)
        Fonts.fontSemibold40.drawString("${health.toInt()} HP", x + 40f, y + 22f, Color(200, 200, 200, 255).rgb)
        val itemStack = entity.heldItem
        val itemX = x + Fonts.fontSemibold40.getStringWidth(name) + 50f
        if (itemStack != null) {
            GlStateManager.pushMatrix()
            GlStateManager.translate(itemX, y + 12f, 0f)
            GlStateManager.scale(1.5f, 1.5f, 1.5f)
            RenderHelper.enableGUIStandardItemLighting()
            mc.renderItem.renderItemAndEffectIntoGUI(itemStack, 0, 0)
            RenderHelper.disableStandardItemLighting()
            GlStateManager.popMatrix()
        } else {
            Fonts.fontSemibold40.drawString("?", itemX + 5f, y + 11f, Color(200, 200, 200, 255).rgb)
        }
        GlStateManager.popMatrix()
    }

    private fun renderStylesHUD(sr: ScaledResolution) {
        val entity = target ?: return
        val x = sr.scaledWidth / 2F + posX; val y = sr.scaledHeight / 2F + posY
        val width = (38F + Fonts.fontRegular35.getStringWidth(entity.name)).coerceAtLeast(118F)
        stylesEasingHealth = lerp(stylesEasingHealth, entity.health, animSpeed * 1.4F)

        drawHudBlur(x.toFloat(), y.toFloat(), width + 14f, 44f)

        if (stylesShadow) ShowShadow(x.toFloat(), y.toFloat(), width + 14f, 44f, 0.3F)
        RenderUtils.drawRect(x.toFloat(), y.toFloat(), x + width + 14f, y + 44f, getBackgroundColor())

        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            Target().drawHead(it.locationSkin, (x + 3).toInt(), (y + 3).toInt(), 30, 30, Color.WHITE)
        }
        Fonts.fontRegular35.drawString(entity.name, x + 34.5f, y + 4f, Color.WHITE.rgb)
        Fonts.fontRegular35.drawString("Health: ${"%.1f".format(stylesEasingHealth)}", x + 34.5f, y + 14f, Color.WHITE.rgb)
        Fonts.fontRegular35.drawString("Distance: ${"%.1f".format(mc.thePlayer.getDistanceToEntity(entity))}m", x + 34.5f, y + 24f, Color.WHITE.rgb)
        RenderUtils.drawRect(x + 2.5f, y + 35.5f, x + width + 11.5f, y + 37.5f, Color(0, 0, 0, 200).rgb)
        RenderUtils.drawRect(x + 3f, y + 36f, x + 3f + (stylesEasingHealth / entity.maxHealth) * (width + 8f), y + 37f, Color(0, 255, 150).rgb)
        RenderUtils.drawRect(x + 2.5f, y + 39.5f, x + width + 11.5f, y + 41.5f, Color(0, 0, 0, 200).rgb)
        RenderUtils.drawRect(x + 3f, y + 40f, x + 3f + (entity.totalArmorValue / 20f) * (width + 8f), y + 41f, Color(77, 128, 255).rgb)
    }

    private fun renderNeonHUD(x: Float, y: Float) {
        val entity = target ?: return
        val width = 161F; val height = 47F
        easingHealth = lerp(easingHealth, entity.health, animSpeed * 10)

        drawHudBlur(x, y, width * slideIn, height * slideIn, 8F)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, 1F)
        ShowShadow(0F, 0F, width, height, 0.3F)
        drawRoundedRect(0F, 0F, width, height, getBackgroundColor(), 8F)

        if (neonGlow) {
            for (i in 1..3) {
                val glowSize = i * 2F
                drawRoundedRect(-glowSize, -glowSize, width + glowSize, height + glowSize, Color(neonColor.red, neonColor.green, neonColor.blue, 30 / i).rgb, 8F + glowSize)
            }
        }

        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            Target().drawHead(it.locationSkin, 8, 8, 33, 33, Color.WHITE)
        }

        Fonts.fontRegular40.drawString(entity.name, 51F, 12F, Color.WHITE.rgb)
        val winOrLose = if (mc.thePlayer.health >= entity.health) "W" else "L"
        val wlColor = if (winOrLose == "W") Color(0, 255, 0) else Color(255, 0, 0)
        Fonts.fontRegular40.drawString(winOrLose, 51F + Fonts.fontRegular40.getStringWidth(entity.name) + 3, 12F, wlColor.rgb)

        val healthPercent = easingHealth / entity.maxHealth
        val barY = 24F; val barHeight = 10F; val barX = 51F
        drawRoundedRect(barX, barY, width - 20F, barY + barHeight, Color(100, 100, 100).rgb, 2F)
        drawRoundedRect(barX, barY, barX + (width - 80F) * healthPercent, barY + barHeight, neonColor.brighter().rgb, 2F)

        val healthText = "${decimalFormat.format(easingHealth)} / ${entity.maxHealth}"
        Fonts.fontRegular40.drawString(healthText, width - Fonts.fontSemibold35.getStringWidth(healthText) - 50F, barY, Color.WHITE.rgb)
        Fonts.fontRegular35.drawString("${decimalFormat.format(mc.thePlayer.getDistanceToEntity(entity))}m", barX, barY + barHeight + 4F, Color(200, 200, 200, 180).rgb)

        GlStateManager.popMatrix()
    }

    // Opai 样式
    private fun renderOpaiHUD(sr: ScaledResolution) {
        val killAuraTarget = KillAura.target.takeIf { it is EntityPlayer }
        val shouldRender = KillAura.handleEvents() && killAuraTarget != null || mc.currentScreen is GuiChat
        val target = killAuraTarget ?: if (opaiDelayCounter >= opaiVanishDelay) mc.thePlayer else lastTarget ?: mc.thePlayer
        if (shouldRender) opaiDelayCounter = 0 else opaiDelayCounter++
        if (!shouldRender && opaiDelayCounter >= opaiVanishDelay) return

        val x = sr.scaledWidth / 2F + posX; val y = sr.scaledHeight / 2F + posY
        val targetName = target.name + "  "
        val targetNameWidth = Fonts.fontSemibold35.getStringWidth(targetName)
        val targetHealth = target.health.toInt()
        val targetHealthWidth = Fonts.fontSemibold35.getStringWidth(targetHealth.toString())
        val textsDrawBegin = 3.5f + 30f + 3.5f
        val allTextLen = targetNameWidth + targetHealthWidth
        val resultProgressWidth = max(135f, textsDrawBegin + allTextLen + 8f)
        val publicXY = Pair(3.5f * 2 + resultProgressWidth, 3.5f + 30f + 3.5f + 5f + 3.5f)

        drawHudBlur(x, y, publicXY.first * slideIn, publicXY.second * slideIn, 5f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        if (opaiShadowCheck) ShowShadow(0f, 0f, publicXY.first, publicXY.second, opaiShadowStrengh)
        drawRoundedRect(0f, 0f, publicXY.first - 3.5f, publicXY.second, getBackgroundColor(), 5f)

        if (target is EntityLivingBase) drawOpaiHead(target, 3.5f, 3.5f)

        val progressBarLength = resultProgressWidth / target.maxHealth * target.health
        opaiAnimX = AnimationUtil.base(opaiAnimX.toDouble(), progressBarLength.toDouble(), 0.2).toFloat()

        RenderUtils.drawRoundedBorderRect(3.5f, 3.5f + 30f + 3.5f, resultProgressWidth, 3.5f + 30f + 3.5f + 5f, 0.3f,
            Color(0, 0, 0, 200).rgb, Color(0, 0, 0, 200).rgb, 5f)
        RenderUtils.drawRoundedBorderRect(3.5f, 3.5f + 30f + 3.5f, opaiAnimX, 3.5f + 30f + 3.5f + 5f, 0.3f,
            Color(opaiThemeColor.red, opaiThemeColor.green, opaiThemeColor.blue, 150).rgb,
            Color(opaiThemeColor.red, opaiThemeColor.green, opaiThemeColor.blue, 150).rgb, 4F)
        RenderUtils.drawRoundedBorderRect(3.5f, 3.5f + 30f + 3.5f, progressBarLength, 3.5f + 30f + 3.5f + 5f, 0.3f,
            opaiThemeColor.rgb, opaiThemeColor.rgb, 4F)

        Fonts.fontSemibold35.drawString(targetName, textsDrawBegin + 3.5f, 3.5f * 2, Color.WHITE.rgb)
        Fonts.fontSemibold35.drawString(targetHealth.toString(), textsDrawBegin + targetNameWidth + 3.5f, 3.5f * 2 - 1F, opaiThemeColor.rgb)

        drawOpaiArmor(textsDrawBegin, 3.5f + 30f - 18, target)
        GlStateManager.popMatrix()
    }

    private fun drawOpaiHead(target: EntityLivingBase, x: Float, y: Float) {
        val texture = mc.renderManager.getEntityRenderObject<Entity>(target)?.getEntityTexture(target) ?: return
        withClipping(main = { drawRoundedRect(x, y, x + 30f, y + 30f, 0, 5f) }, toClip = {
            RenderUtils.drawHead(texture, x.toInt(), y.toInt(), 8f, 8f, 8, 8, 30, 30, 64f, 64f, Color.WHITE)
        })
    }

    private fun drawOpaiArmor(x: Float, y: Float, target: EntityLivingBase) {
        if (target !is EntityPlayer) return
        GlStateManager.pushMatrix()
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        enableGUIStandardItemLighting()
        var offsetX = x
        for (index in 3 downTo 0) {
            val stack = target.inventory.armorInventory[index] ?: continue
            mc.renderItem.renderItemIntoGUI(stack, offsetX.toInt(), y.toInt())
            mc.renderItem.renderItemOverlays(mc.fontRendererObj, stack, offsetX.toInt(), y.toInt())
            offsetX += 18f
        }
        disableStandardItemLighting()
        glDisable(GL_BLEND)
        GlStateManager.popMatrix()
    }

    // RavenB4 样式
    private fun renderRavenB4HUD(sr: ScaledResolution) {
        val entity = target ?: return
        val x = sr.scaledWidth / 2F + posX; val y = sr.scaledHeight / 2F + posY
        val font = Fonts.minecraftFont
        val hp = decimalFormat.format(entity.health)
        val hplength = font.getStringWidth(hp)
        val length = font.getStringWidth(entity.displayName.formattedText)
        val barColor = Color(barColorR, barColorG, barColorB)
        val totalWidth = length + hplength + 23F
        val totalHeight = 35F

        drawHudBlur(x, y, totalWidth, totalHeight, 4F)

        GlStateManager.pushMatrix()
        updateRavenB4Anim(entity.health)
        drawRoundedRect(x, y, x + totalWidth, y + totalHeight, getBackgroundColor(), 4F)
        GlStateManager.enableBlend()

        font.drawStringWithShadow(entity.displayName.formattedText, x + 6F, y + 8F, Color.WHITE.rgb)
        val winOrLose = if (entity.health < mc.thePlayer.health) "W" else "L"
        val wlColor = if (winOrLose == "W") Color(0, 255, 0).rgb else Color(139, 0, 0).rgb
        font.drawStringWithShadow(winOrLose, x + length + hplength + 11.6F, y + 8F, wlColor)
        font.drawStringWithShadow(hp, x + length + 8F, y + 8F, ColorUtils.reAlpha(ColorUtils.getHealthColor(entity.health, entity.maxHealth).rgb, 255F).rgb)

        GlStateManager.disableAlpha(); GlStateManager.disableBlend()
        drawRoundedRect(x + 5.0F, y + 29.55F, x + length + hplength + 18F, y + 25F, Color(0, 0, 0, 110).rgb, 2F)
        RenderUtils.drawRoundedGradientRectCorner(x + 5F, y + 25F, x + 5F + (easingHealth / entity.maxHealth) * (length + hplength + 13F), y + 29.5F, 4F, barColor.rgb, barColor.rgb)
        GlStateManager.popMatrix()
    }

    private fun updateRavenB4Anim(targetHealth: Float) {
        easingHealth += ((targetHealth - easingHealth) / 2.0F.pow(10.0F - animSpeedRB4)) * deltaTime
    }

    // Naven 样式
    private fun renderNavenHUD(sr: ScaledResolution) {
        val entity = target ?: return
        val x = sr.scaledWidth / 2f + posX; val y = sr.scaledHeight / 2f + posY
        val width = 130f; val height = 50f
        NavenEasingHealth = lerp(NavenEasingHealth, entity.health, animSpeed * 1.4F)

        drawHudBlur(x, y, width * slideIn, height * slideIn, 5f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0F)
        GlStateManager.scale(slideIn, slideIn, slideIn)
        ShowShadow(0F, 0F, width, height, 0.3F)
        drawRoundedRect(0F, 0F, width, height, getBackgroundColor(), 5f)

        mc.netHandler.getPlayerInfo(entity.uniqueID)?.locationSkin?.let {
            Target().drawHead(it, 7, 7, 30, 30, Color.WHITE)
        }

        val barX1 = 5f; val barY1 = height - 10f; val barX2 = width - 5f; val barY2 = barY1 + 3f
        drawRoundedRect(barX1, barY1, barX2, barY2, Color(0, 0, 0, 200).rgb, 2f)
        val healthPercent = NavenEasingHealth / entity.maxHealth
        val fillX2 = barX1 + (barX2 - barX1) * healthPercent
        drawRoundedRect(barX1, barY1, fillX2, barY2, Color(160, 42, 42).rgb, 2f)

        Fonts.fontRegular35.drawString(entity.name, 40f, 10f, Color.WHITE.rgb)
        Fonts.fontRegular35.drawString("Health: ${"%.2f".format(NavenEasingHealth)}", 40f, 22f, Color.WHITE.rgb)
        Fonts.fontRegular35.drawString("Distance: ${"%.2f".format(entity.getDistanceToEntity(mc.thePlayer))}", 40f, 30f, Color.WHITE.rgb)
        GlStateManager.popMatrix()
    }

    // Myau 样式
    private fun renderMyauHUD(sr: ScaledResolution) {
        val entity = target ?: return
        val x = sr.scaledWidth / 2F + posX; val y = sr.scaledHeight / 2F + posY
        val nameWidth = Fonts.fontRegular35.getStringWidth(entity.name)
        val hudWidth = maxOf(80f, nameWidth + 20f)
        val hudHeight = 25f; val avatarSize = hudHeight

        if (rainbow) { hue += 0.0005f; if (hue > 1f) hue = 0f }
        val borderColor = if (rainbow) getRainbowColor() else Color(borderRed, borderGreen, borderBlue)
        val totalWidth = if (showAvatar) hudWidth + avatarSize else hudWidth

        drawHudBlur(x, y, totalWidth, hudHeight)

        RenderUtils.drawRect(x - 1, y - 1, x + totalWidth + 1, y, borderColor.rgb)
        RenderUtils.drawRect(x - 1, y + hudHeight, x + totalWidth + 1, y + hudHeight + 1, borderColor.rgb)
        RenderUtils.drawRect(x - 1, y, x, y + hudHeight, borderColor.rgb)
        RenderUtils.drawRect(x + totalWidth, y, x + totalWidth + 1, y + hudHeight, borderColor.rgb)
        RenderUtils.drawRect(x, y, x + totalWidth, y + hudHeight, getBackgroundColor())

        if (showAvatar) {
            mc.netHandler.getPlayerInfo(entity.uniqueID)?.locationSkin?.let {
                Target().drawHead(it, x.toInt(), y.toInt(), avatarSize.toInt(), avatarSize.toInt(), Color.WHITE)
            }
        }

        val textX = if (showAvatar) x + avatarSize + 3 else x + 3
        Fonts.fontSemibold35.drawString(entity.name, textX, y + 1, Color.WHITE.rgb)
        val healthText = String.format("%.1f", entity.health)
        Fonts.fontSemibold35.drawString(healthText, textX, y + 11, borderColor.rgb)
        Fonts.fontSemibold35.drawString("\u2764", textX + Fonts.fontSemibold35.getStringWidth(healthText) + 2, y + 11, borderColor.rgb)

        val barY = y + 21; val barWidth = hudWidth - 5f
        RenderUtils.drawRect(textX, barY, textX + barWidth, barY + 3, Color(64, 64, 64).rgb)
        val targetFill = (entity.health / entity.maxHealth) * barWidth
        easingHealth = lerp(easingHealth, targetFill, 0.1f)
        RenderUtils.drawRect(textX, barY, textX + easingHealth, barY + 3, borderColor.rgb)

        val playerHealth = mc.thePlayer.health
        val (winLoss, wlColor) = when {
            playerHealth > entity.health -> "W" to Color(0, 255, 0)
            playerHealth < entity.health -> "L" to Color(255, 0, 0)
            else -> "D" to Color(255, 255, 0)
        }
        Fonts.fontSemibold35.drawString(winLoss, x + totalWidth - Fonts.fontSemibold35.getStringWidth(winLoss) - 1, y + 1, wlColor.rgb)

        val diff = playerHealth - entity.health
        val diffText = if (diff > 0) "+${"%.1f".format(diff)}" else String.format("%.1f", diff)
        val diffColor = when {
            diff > 0 -> Color(0, 255, 0)
            diff < 0 -> Color(255, 0, 0)
            else -> Color(255, 255, 0)
        }
        Fonts.fontSemibold35.drawString(diffText, maxOf(x + totalWidth - Fonts.fontSemibold35.getStringWidth(diffText) - 1, textX), y + 11, diffColor.rgb)
    }

    // 戶籍 样式
    private fun render0x01a4HUD(sr: ScaledResolution) {
        val entity = target ?: return
        val x = sr.scaledWidth / 2 + this.posX; val y = sr.scaledHeight / 2 + this.posY
        val width = 150F; val height = 90F

        drawHudBlur(x + 11F, y - 15F, width - 11F, height)

        RenderUtils.drawRect(x + 11F, y - 15F, x + width, y + height - 15F, getBackgroundColor())
        Fonts.fontSemibold35.drawString("PLC 全国人口档案查询系统", x + 15f, y - 5f, Color.WHITE.rgb)
        Fonts.fontSemibold35.drawString("姓名: ${entity.name}", x + 15f, y + 5f, Color.WHITE.rgb)
        Fonts.fontSemibold35.drawString("健康: ${entity.health.toInt()}/${entity.maxHealth.toInt()}", x + 15f, y + 25f, Color.WHITE.rgb)
        Fonts.fontSemibold35.drawString("资产: ${entity.totalArmorValue}", x + 15f, y + 45f, Color.WHITE.rgb)
        Fonts.fontSemibold35.drawString("身份证: ${entity.entityId}", x + 15f, y + 65f, Color.WHITE.rgb)
    }

    // 辅助绘图函数
    private fun drawCircleArc(x: Float, y: Float, radius: Float, lineWidth: Float, startAngle: Float, endAngle: Float, color: Color) {
        glPushMatrix()
        glEnable(GL_BLEND); glDisable(GL_TEXTURE_2D); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH); glLineWidth(lineWidth)
        glColor4f(color.red / 255F, color.green / 255F, color.blue / 255F, color.alpha / 255F)
        glBegin(GL_LINE_STRIP)
        for (i in (startAngle / 360 * 100).toInt()..(endAngle / 360 * 100).toInt()) {
            val angle = (i / 100.0 * 360.0 * (PI / 180)).toFloat()
            glVertex2f(x + sin(angle) * radius, y + cos(angle) * radius)
        }
        glEnd()
        glDisable(GL_LINE_SMOOTH); glEnable(GL_TEXTURE_2D); glDisable(GL_BLEND)
        glPopMatrix()
        glColor4f(1f, 1f, 1f, 1f)
    }

    private fun drawCircle(x: Float, y: Float, radius: Float, color: Int) {
        val side = (radius * 2).toInt()
        glEnable(GL_BLEND); glDisable(GL_TEXTURE_2D); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_POLYGON_SMOOTH)
        glBegin(GL_TRIANGLE_FAN)
        RenderUtils.glColor(Color(color))
        for (i in 0..side) {
            val angle = i * (Math.PI * 2) / side
            glVertex2d(x + sin(angle) * radius, y + cos(angle) * radius)
        }
        glEnd()
        glDisable(GL_POLYGON_SMOOTH); glEnable(GL_TEXTURE_2D); glDisable(GL_BLEND)
    }

    private fun drawRoundedHead(skinLocation: ResourceLocation, x: Int, y: Int, width: Int, height: Int, color: Color, radius: Float = 6f) {
        Stencil.write(false)
        glDisable(GL_TEXTURE_2D); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        drawRoundedRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), color.rgb, radius)
        glDisable(GL_BLEND); glEnable(GL_TEXTURE_2D)
        Stencil.erase(true)
        Target().drawHead(skinLocation, x, y, width, height, color)
        Stencil.dispose()
    }

    private fun ShowShadow(startX: Float, startY: Float, width: Float, height: Float, shadowStrengh: Float) {
        if (shadowStrengh <= 0f) return

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        GlStateManager.pushMatrix()

        GlStateManager.disableLighting()
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.disableAlpha()
        GlStateManager.enableTexture2D()
        GlStateManager.color(1f, 1f, 1f, 1f)

        GlowUtils.drawGlow(startX, startY, width, height, (shadowStrengh * 13F).toInt(), Color(0, 0, 0, 120))

        GlStateManager.popMatrix()
        glPopAttrib()
    }

    // ================= Liawb 渲染 =================
    private val liawbInventoryTexture = ResourceLocation("textures/gui/container/inventory.png")

    private fun renderLiawbHUD(x: Float, y: Float) {
        // 消失动画期间 target 已经为 null，用单独缓存的上一个目标继续画
        val entity = target ?: liawbLastEntity ?: return
        if (target != null) liawbLastEntity = target
        NavenEasingHealth = lerp(NavenEasingHealth, entity.health, animSpeed)
        val width = 160F
        val height = 70F
        val radius = liawbRadius

        val animOn = liawbAnimMode == "Bounce"
        if (animOn) {
            updateLiawbAnimation()
            // 关闭阶段：只画整张快照并统一淡出/缩小（物品、图标、模糊、阴影一起走）
            if (!liawbShown && liawbSnapshot && !liawbJustHidden) {
                drawLiawbSnapshot(x, y, liawbAlpha, liawbScale * liawbUiScale)
                return
            }
            if (liawbAlpha <= 0.01F) return
        }
        val animScale = if (animOn) liawbScale else slideIn
        // None 模式下也让透明度跟随 slideIn 淡出，避免"整体突然消失"
        val animAlpha = if (animOn) liawbAlpha else slideIn.coerceIn(0F, 1F)
        // HUD 大小可调（与动画缩放相乘）
        val totalScale = animScale * liawbUiScale

        // 背景模糊：跟随缩放后的真实矩形（居中缩放，位置/尺寸/圆角都要跟着变）
        // 阈值放很低，避免淡出尾声模糊突然消失造成"闪一下"
        if (animAlpha > 0.01F) {
            val scaledW = width * totalScale
            val scaledH = height * totalScale
            drawHudBlur(
                x + (width - scaledW) / 2F,
                y + (height - scaledH) / 2F,
                scaledW, scaledH,
                radius * totalScale
            )
        }

        GlStateManager.pushMatrix()
        // 以面板中心为缩放原点，避免从左上角"弹"出去
        GlStateManager.translate(x + width / 2F, y + height / 2F, 0F)
        GlStateManager.scale(totalScale, totalScale, totalScale)
        GlStateManager.translate(-width / 2F, -height / 2F, 0F)

        // Glow 阴影（可调颜色/强度 + mask）
        if (liawbShadowCheck && animAlpha > 0.01F) {
            GlowUtils.drawGlow(
                0F, 0F, width, height,
                (liawbShadowStrength * 13F).toInt(), liawbShadowColor.withAlphaMul(animAlpha),
                cornerRadius = radius, mask = liawbShadowMask
            )
        }

        drawRoundedRect(0F, 0F, width, height, liawbBackgroundColor.withAlphaMul(animAlpha).rgb, radius)

        val textColor = liawbTextColor.withAlphaMul(animAlpha).rgb

        // ---- 血量圆环（激活/非激活两段可自定义颜色）----
        val ringCX = 22F
        val ringCY = height / 2F
        val healthPercent = (NavenEasingHealth / entity.maxHealth).coerceIn(0F, 1F)
        drawRing(ringCX, ringCY, liawbRingRadius, liawbRingThickness, 0F, 360F, liawbRingBackColor.withAlphaMul(animAlpha))
        if (healthPercent > 0F)
            drawRing(ringCX, ringCY, liawbRingRadius, liawbRingThickness, -90F, -90F + 360F * healthPercent, liawbRingColor.withAlphaMul(animAlpha))

        // 圆环内显示血量百分比
        val hpText = "${(healthPercent * 100).toInt()}%"
        val hpW = liawbInfoFont.getStringWidth(hpText)
        liawbInfoFont.drawString(hpText, ringCX - hpW / 2F, ringCY - 5F, textColor, false)

        // ---- 头像（跟随整体透明度，否则消失时会突兀地保持不透明）----
        mc.netHandler.getPlayerInfo(entity.uniqueID)?.let {
            drawRoundedHead(it.locationSkin, 40, (ringCY - 13F).toInt(), 26, 26, Color.WHITE.withAlphaMul(animAlpha), 5f)
        }

        val textX = 70F

        // ---- 名字顶部底条（可选 + 可选颜色）----
        if (liawbNameBar) {
            val nameWidth = liawbNameFont.getStringWidth(entity.name)
            drawRoundedRect(
                textX - 2F, 4F,
                textX + nameWidth + 3F, 18F,
                liawbNameBarColor.withAlphaMul(animAlpha).rgb, 3F
            )
        }

        // ---- 名字 ----
        liawbNameFont.drawString(entity.name, textX, 6F, textColor, false)

        // ---- 主手物品（图标 + 数量 + 耐久）----
        if (liawbShowMainHand) {
            entity.heldItem?.let { stack ->
                drawItemStack(stack, textX.toInt(), 22, animAlpha)
                val durText: String
                val durPercent: Float
                if (stack.isItemStackDamageable && stack.maxDamage > 0) {
                    val remain = stack.maxDamage - stack.itemDamage
                    durText = "$remain/${stack.maxDamage}"
                    durPercent = remain.toFloat() / stack.maxDamage
                } else {
                    durText = "x${stack.stackSize}"
                    durPercent = 1F
                }
                val durColor = ColorUtils.interpolateColor(Color(255, 60, 60), Color(90, 255, 120), durPercent)
                    .withAlphaMul(animAlpha).rgb
                liawbInfoFont.drawString(durText, textX + 18F, 26F, durColor, false)
            }
        }

        // ---- 护甲（图标行 + 可选数字耐久 + 可选总耐久圆环）----
        if (liawbShowArmor && entity is EntityPlayer) {
            var totalDur = 0F
            var totalMax = 0F
            var armorX = textX
            val armorY = 42F

            glPushMatrix()
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            enableGUIStandardItemLighting()
            GlStateManager.color(1F, 1F, 1F, animAlpha)
            for (index in 3 downTo 0) {
                val stack = entity.inventory.armorInventory[index]
                if (stack != null) {
                    GlStateManager.color(1F, 1F, 1F, animAlpha)
                    mc.renderItem.renderItemIntoGUI(stack, armorX.toInt(), armorY.toInt())
                    GlStateManager.color(1F, 1F, 1F, animAlpha)
                    mc.renderItem.renderItemOverlays(mc.fontRendererObj, stack, armorX.toInt(), armorY.toInt())
                    if (stack.isItemStackDamageable && stack.maxDamage > 0) {
                        val remain = stack.maxDamage - stack.itemDamage
                        totalDur += remain
                        totalMax += stack.maxDamage
                        if (liawbArmorNumbers) {
                            val pct = remain.toFloat() / stack.maxDamage
                            val c = ColorUtils.interpolateColor(Color(255, 60, 60), Color(90, 255, 120), pct)
                                .withAlphaMul(animAlpha).rgb
                            liawbInfoFont.drawString("$remain", armorX + 1F, armorY + 16F, c, false)
                        }
                    }
                }
                armorX += 16F
            }
            disableStandardItemLighting()
            GlStateManager.color(1F, 1F, 1F, 1F)
            glDisable(GL_BLEND)
            glPopMatrix()

            // 总耐久圆环（右上角小环）
            if (liawbArmorRing && totalMax > 0F) {
                val pct = (totalDur / totalMax).coerceIn(0F, 1F)
                val acx = width - 10F
                val acy = 10F
                drawRing(acx, acy, 8F, 2.5F, 0F, 360F, liawbArmorRingBackColor.withAlphaMul(animAlpha))
                if (pct > 0F)
                    drawRing(acx, acy, 8F, 2.5F, -90F, -90F + 360F * pct, liawbArmorRingColor.withAlphaMul(animAlpha))
            }
        }

        // ---- 药水效果（仅图标，右侧竖排）----
        if (liawbShowPotions) {
            var potionY = 24F
            for (effect in entity.activePotionEffects) {
                if (potionY > height - 18F) break
                val potion = Potion.potionTypes[effect.potionID] ?: continue
                if (!potion.hasStatusIcon()) continue
                mc.textureManager.bindTexture(liawbInventoryTexture)
                GlStateManager.color(1F, 1F, 1F, animAlpha)
                val iconX = potion.statusIconIndex % 8 * 18
                val iconY = 198 + potion.statusIconIndex / 8 * 18
                RenderUtils.drawTexturedModalRect((width - 19F).toInt(), potionY.toInt(), iconX, iconY, 18, 18, 0F)
                potionY += 16F
            }
        }

        GlStateManager.popMatrix()

        // 关闭的第一帧：把刚画好的整个 HUD 区域抓成一张纹理，
        // 之后只画这张图整体缩小 + 淡出（物品图标/药水/模糊/阴影都在图里一起走）
        if (animOn && liawbJustHidden) {
            captureLiawb(x, y, width, height, totalScale)
            liawbJustHidden = false
        }
    }

    /** 把 HUD 当前占据的屏幕区域复制到纹理里 */
    private fun captureLiawb(x: Float, y: Float, w: Float, h: Float, scale: Float) {
        val sw = (w * scale).toInt()
        val sh = (h * scale).toInt()
        if (sw <= 0 || sh <= 0) return
        val sr = ScaledResolution(mc)
        val sf = sr.scaleFactor.toFloat()
        // 居中缩放后的实际矩形（GUI 坐标）
        val gx = x + (w - w * scale) / 2F
        val gy = y + (h - h * scale) / 2F
        // 转成帧缓冲像素坐标（GL 原点在左下角）
        val px = (gx * sf).toInt()
        val py = (mc.displayHeight - (gy + h * scale) * sf).toInt()

        if (liawbTexId == -1) liawbTexId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, liawbTexId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE)
        glTexImage2D(
            GL_TEXTURE_2D, 0, GL_RGBA, sw, sh, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?
        )
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, px, py, sw, sh)

        liawbCapX = gx; liawbCapY = gy
        liawbCapW = w * scale; liawbCapH = h * scale
        liawbSnapshot = true
    }

    /** 画快照：整体缩放 + 统一 alpha 淡出 */
    private fun drawLiawbSnapshot(x: Float, y: Float, alpha: Float, scale: Float) {
        if (liawbTexId == -1 || liawbCapW <= 0F) return
        val a = alpha.coerceIn(0F, 1F)
        if (a <= 0.01F) { liawbSnapshot = false; return }

        val w = liawbCapW * scale
        val h = liawbCapH * scale
        val left = x + (liawbCapW - w) / 2F
        val top = y + (liawbCapH - h) / 2F

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glEnable(GL_TEXTURE_2D)
        glBindTexture(GL_TEXTURE_2D, liawbTexId)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1F, 1F, 1F, a)
        glBegin(GL_QUADS)
        // 纹理 (0,0) 对应区域左下角，屏幕绘制 y 向下 → 上边用 v=1
        glTexCoord2f(0F, 1F); glVertex2f(left, top)
        glTexCoord2f(1F, 1F); glVertex2f(left + w, top)
        glTexCoord2f(1F, 0F); glVertex2f(left + w, top + h)
        glTexCoord2f(0F, 0F); glVertex2f(left, top + h)
        glEnd()
        glColor4f(1F, 1F, 1F, 1F)
        glPopAttrib()
    }
    private fun drawRing(cx: Float, cy: Float, radius: Float, thickness: Float, startDeg: Float, endDeg: Float, color: Color) {
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_LINE_SMOOTH)
        glBegin(GL_TRIANGLE_STRIP)
        val steps = 72
        var i = 0
        while (i <= steps) {
            val angle = Math.toRadians(startDeg + (endDeg - startDeg) * (i.toDouble() / steps))
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
            glVertex2f(cx + cosA * (radius - thickness / 2F), cy + sinA * (radius - thickness / 2F))
            glVertex2f(cx + cosA * (radius + thickness / 2F), cy + sinA * (radius + thickness / 2F))
            i++
        }
        glEnd()
        glDisable(GL_LINE_SMOOTH)
        glEnable(GL_TEXTURE_2D)
        glPopAttrib()
    }

    /** 物品图标（含数量/耐久 overlay） */
    private fun drawItemStack(stack: ItemStack, x: Int, y: Int, alpha: Float = 1f) {
        GlStateManager.pushMatrix()
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        enableGUIStandardItemLighting()
        GlStateManager.color(1F, 1F, 1F, alpha)
        mc.renderItem.renderItemIntoGUI(stack, x, y)
        GlStateManager.color(1F, 1F, 1F, alpha)
        mc.renderItem.renderItemOverlays(mc.fontRendererObj, stack, x, y)
        GlStateManager.color(1F, 1F, 1F, 1F)
        disableStandardItemLighting()
        glDisable(GL_BLEND)
        GlStateManager.popMatrix()
    }

    // ================= Liawb 弹性动画状态（弹簧模型，速度连续无顿挫） =================
    private var liawbLastEntity: EntityLivingBase? = null
    private var liawbShown = false
    private var liawbAlpha = 1F
    private var liawbScale = 1F
    private var liawbScaleVel = 0F
    private var liawbAlphaVel = 0F
    private var liawbLastHideAt = 0L

    // ---- 关闭时整张快照淡出（物品/图标等无法单独调 alpha 的元素也一起走）----
    private var liawbTexId = -1
    private var liawbSnapshot = false
    private var liawbJustHidden = false
    private var liawbCapX = 0F
    private var liawbCapY = 0F
    private var liawbCapW = 0F
    private var liawbCapH = 0F

    /**
     * 弹簧动画（帧率无关）：
     *  - 出现：scale 从 0.55 弹向 1.0，欠阻尼自然过冲后回落；alpha 平滑淡入
     *  - 消失：先给一个向上冲量（视觉上先膨胀一下），再缩向 0.60，alpha 同步淡出
     * 连续速度曲线，没有分段缓动那种"一顿一顿"的顿挫感。
     */
    private fun updateLiawbAnimation() {
        val has = target != null
        val dt = (deltaTime / 1000F).coerceIn(0.001F, 0.05F)

        if (has && !liawbShown) {
            liawbShown = true
            liawbSnapshot = false
            liawbJustHidden = false
            // 宽限期：刚消失没多久又重新出现（换目标/目标短暂丢失）→ 直接以正常大小显示，
            // 不重播"从 0.55 弹入"，否则收尾时会看到一次闪
            val quickReappear = System.currentTimeMillis() - liawbLastHideAt < 250L
            if (quickReappear) {
                liawbScale = 1F
                liawbScaleVel = 0F
                liawbAlpha = 1F
                liawbAlphaVel = 0F
            } else {
                liawbScale = 0.55F
                liawbScaleVel = 0F
                liawbAlpha = 0F
                liawbAlphaVel = 0F
            }
        } else if (!has && liawbShown) {
            liawbShown = false
            liawbLastHideAt = System.currentTimeMillis()
            // 这一帧不开始衰减：先按原样把完整面板画出来供抓图，下一帧起改用快照淡出
            liawbJustHidden = true
            liawbScale = 1F
            liawbScaleVel = 0F
            liawbAlpha = 1F
            liawbAlphaVel = 0F
            return
        }

        // 快照淡出：缩到最小 + 淡到全透明（模糊/阴影都烘进图里一起缩）
        val targetScale = if (has) 1F else 0.2F
        val targetAlpha = if (has) 1F else 0F

        // scale 弹簧（欠阻尼 → 出现时自然过冲再回落）
        val kS = if (has) 170F else 150F
        val cS = if (has) 13F else 14F
        liawbScaleVel += (targetScale - liawbScale) * kS * dt
        liawbScaleVel *= (1F - cS * dt).coerceIn(0F, 1F)
        liawbScale += liawbScaleVel * dt

        // alpha 平滑追随（放慢一点，淡出更柔和，不突兀）
        val kA = 90F
        val cA = 17F
        liawbAlphaVel += (targetAlpha - liawbAlpha) * kA * dt
        liawbAlphaVel *= (1F - cA * dt).coerceIn(0F, 1F)
        liawbAlpha = (liawbAlpha + liawbAlphaVel * dt).coerceIn(0F, 1F)

        // 静止复位，避免数值长期漂移
        if (has && abs(targetScale - liawbScale) < 0.001F && abs(liawbScaleVel) < 0.01F) {
            liawbScale = 1F
            liawbScaleVel = 0F
        }
        if (liawbAlpha > 0.999F) { liawbAlpha = 1F; liawbAlphaVel = 0F }
        if (liawbAlpha < 0.001F) { liawbAlpha = 0F; liawbAlphaVel = 0F }
    }

    private fun Color.withAlphaMul(mult: Float): Color =
        Color(red, green, blue, (alpha * mult).toInt().coerceIn(0, 255))

    override fun onDisable() {
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.disableBlend()
    }

    override val tag get() = hudStyle

    private fun lerp(start: Float, end: Float, speed: Float): Float = start + (end - start) * speed * (deltaTime / (1000F / 60F))
    private fun getRainbowColor(): Color = Color.getHSBColor(hue, 1f, 1f)
}