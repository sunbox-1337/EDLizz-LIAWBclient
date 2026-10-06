/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.gui.web

import net.ccbluex.liquidbounce.utils.web.McefBrowser
import net.montoyo.mcef.api.IBrowser
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

/**
 * 真正的“内嵌”网页：把 Chromium 离屏渲染的纹理直接画进这个 GuiScreen，
 * 鼠标 / 键盘 / 滚轮全部转发给浏览器，不打开任何系统浏览器窗口。
 */
class WebBrowserScreen(private val url: String) : GuiScreen() {
    private var browser: IBrowser? = null

    override fun initGui() {
        browser?.close()
        browser = McefBrowser.createBrowser(url, true)
        browser?.resize(width, height)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawRect(0, 0, width, height, 0xFF0E0F13.toInt())
        browser?.draw(0.0, 0.0, width.toDouble(), height.toDouble())
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        browser?.injectMouseButton(mouseX, mouseY, mouseButton + 1, 1, false, 1)
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        browser?.injectMouseButton(mouseX, mouseY, state + 1, 0, false, 1)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel != 0) {
            val x = Mouse.getEventX() * width / mc.displayWidth
            val y = height - Mouse.getEventY() * height / mc.displayHeight - 1
            browser?.injectMouseWheel(x, y, 0, wheel, 0)
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null)
            return
        }
        browser?.injectKeyPressedByKeyCode(keyCode, typedChar, 0)
        if (typedChar != '\u0000') browser?.injectKeyTyped(typedChar, 0)
    }

    override fun onGuiClosed() {
        browser?.close()
        browser = null
        super.onGuiClosed()
    }

    companion object {
        /** 兜底：用系统浏览器打开（默认不走这条路，Embedded 才是内嵌）。 */
        fun openInSystemBrowser(url: String) {
            runCatching {
                val desktop = java.awt.Desktop.getDesktop()
                if (java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(java.net.URI(url))
                }
            }
        }
    }
}
