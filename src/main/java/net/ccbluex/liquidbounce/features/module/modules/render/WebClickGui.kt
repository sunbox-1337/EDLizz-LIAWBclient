/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.gui.web.WebBrowserScreen

/**
 * WebClickGui —— 内嵌的网页版 ClickGUI。
 *
 * 后端就是客户端**已有**的 [net.ccbluex.liquidbounce.web.ClickGuiWebInterface]（端口 8081，
 * 随客户端启动自动运行；前端调用 `/api/modules`、`/api/update`、`/api/check-updates`）。
 * 这个模块只负责把那个网页画进游戏窗口里（MCEF/Chromium 离屏渲染成一张纹理，见 [WebBrowserScreen]），
 * 不打开任何系统浏览器。
 *
 * [mode] 里的 Browser 只是兜底（MCEF 不可用 / 想外部开时用），默认走 Embedded。
 */
object WebClickGui : Module("WebClickGui", Category.RENDER) {
    private const val WEB_URL = "http://127.0.0.1:8081/"

    private val mode by choices("Mode", arrayOf("Embedded", "Browser"), "Embedded")

    override fun onEnable() {
        if (mode == "Embedded") {
            mc.displayGuiScreen(WebBrowserScreen(WEB_URL))
        } else {
            WebBrowserScreen.openInSystemBrowser(WEB_URL)
        }
    }
}
