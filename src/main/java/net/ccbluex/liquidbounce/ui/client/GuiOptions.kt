package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.ui.client.fontmanager.GuiFontManager
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiOptions as MinecraftGuiOptions
import net.minecraft.client.gui.GuiScreen

class GuiOptions(private val prevGui: GuiScreen) : AbstractScreen() {

    override fun initGui() {
        buttonList.clear()

        // 设置按钮的基本位置和尺寸
        val baseCol1 = width / 2 - 100
        val defaultHeight = height / 4 + 48

        // 添加GuiFontManager按钮
        +GuiButton(109, baseCol1, defaultHeight, 200, 20, "FontManager")

        // 添加GuiClientConfiguration按钮
        +GuiButton(102, baseCol1, defaultHeight + 24, 200, 20, "ClientConfiguration")

        // 添加原版Minecraft的GuiOptions按钮
        +GuiButton(0, baseCol1, defaultHeight + 48, 200, 20, "Minecraft Options")

        // 添加返回主页面的按钮
        +GuiButton(10, baseCol1, defaultHeight + 72, 200, 20, "Back")
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
        Fonts.fontSemibold40.drawCenteredString("Set selection", width / 2f, height / 4f, 0xffffff)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            109 -> mc.displayGuiScreen(GuiFontManager(this))
            102 -> mc.displayGuiScreen(GuiClientConfiguration(this))
            0 -> mc.displayGuiScreen(MinecraftGuiOptions(this, mc.gameSettings))
            10 -> mc.displayGuiScreen(GuiMainMenu())
        }
    }
}