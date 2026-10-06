package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce.clickGui
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.BlackStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.LiquidBounceStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.NullStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.SlowlyStyle
import net.minecraft.network.play.server.S2EPacketCloseWindow
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClickGUI : Module("ClickGUI", Category.RENDER, Keyboard.KEY_RSHIFT, canBeEnabled = false) {
    private val style by choices(
        "Style",
        arrayOf("LiquidBounce", "Null", "Slowly", "Black"),
        "LiquidBounce"
    ).onChanged {
        updateStyle()
    }
    var scale by float("Scale", 0.8f, 0.5f..1.5f)
    val maxElements by int("MaxElements", 15, 1..30)
    val fadeSpeed by float("FadeSpeed", 1f, 0.5f..4f)
    val scrolls by boolean("Scrolls", true)
    val spacedModules by boolean("SpacedModules", false)
    val panelsForcedInBoundaries by boolean("PanelsForcedInBoundaries", false)
    val hideServerListButtons by boolean("HideServerListButtons", false)

    private val color by color("Color", Color(0, 160, 255)) { style !in arrayOf("Slowly", "Black") }

    // ===== LiquidBounce 风格的面板外观（只在 Style = LiquidBounce 时生效）=====
    val lbPanelColor by color("LB-PanelColor", Color(0, 0, 0, 140)) { style == "LiquidBounce" }
    val lbSubPanelColor by color("LB-SubPanelColor", Color(0, 0, 0, 170)) { style == "LiquidBounce" }
    val lbPanelBorderColor by color("LB-PanelBorderColor", Color(128, 128, 128)) { style == "LiquidBounce" }
    val lbPanelRadius by float("LB-PanelRadius", 4F, 0F..10F) { style == "LiquidBounce" }

    val lbPanelBlur by boolean("LB-PanelBlur", false) { style == "LiquidBounce" && BlurSettings.active }
    val lbSubPanelBlur by boolean("LB-SubPanelBlur", false) {
        style == "LiquidBounce" && BlurSettings.active && lbPanelBlur
    }

    val lbPanelShadow by boolean("LB-PanelShadow", false) { style == "LiquidBounce" }
    val lbShadowColor by color("LB-ShadowColor", Color(0, 0, 0, 120)) { style == "LiquidBounce" && lbPanelShadow }

    // ===== 模块开启后的整行配色 / 模糊 =====
    val lbModuleColor by color("LB-ModuleEnabledColor", Color(0, 160, 255, 110)) { style == "LiquidBounce" }
    val lbModuleBlur by boolean("LB-ModuleEnabledBlur", true) {
        style == "LiquidBounce" && lbModuleColor.alpha > 0
    }

    // ===== ClickGUI 背景遮罩（原来的半透明黑底）=====
    val guiBackgroundColor by color("GuiBackgroundColor", Color(16, 16, 16, 200))

    /** ClickGUI 字体；样式里同名的成员会遮蔽 import，所以整个文件跟着它走 */
    val lbFont by font(
        "LB-Font",
        net.ccbluex.liquidbounce.ui.font.Fonts.fontSemibold35
    ) { style == "LiquidBounce" }
    val lbShadowStrength by float("LB-ShadowStrength", 1F, 0.5F..2F) { style == "LiquidBounce" && lbPanelShadow }
    val lbShadowMask by boolean("LB-ShadowMask", true) { style == "LiquidBounce" && lbPanelShadow }

    val guiColor
        get() = color.rgb

    override fun onEnable() {
        updateStyle()
        mc.displayGuiScreen(clickGui)
        Keyboard.enableRepeatEvents(true)
    }

    private fun updateStyle() {
        clickGui.style = when (style) {
            "LiquidBounce" -> LiquidBounceStyle
            "Null" -> NullStyle
            "Slowly" -> SlowlyStyle
            "Black" -> BlackStyle
            else -> return
        }
    }

    val onPacket = handler<PacketEvent>(always = true) { event ->
        if (event.packet is S2EPacketCloseWindow && mc.currentScreen is ClickGui) {
            event.cancelEvent()
        }
    }
}