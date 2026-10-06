package net.ccbluex.liquidbounce.file.configs.models

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.render.IconUtils
import net.minecraft.client.gui.FontRenderer
import org.lwjgl.opengl.Display

object ClientConfiguration : Configurable("ClientConfiguration"), MinecraftInstance {
    var clientTitle by boolean("ClientTitle", true)
    var customBackground by boolean("CustomBackground", true)
    var particles by boolean("Particles", false)
    var stylisedAlts by boolean("StylisedAlts", true)
    var unformattedAlts by boolean("CleanAlts", true)
    var altsLength by int("AltsLength", 16, 4..20)
    var altsPrefix by text("AltsPrefix", "")
    // The game language can be overridden by the user. empty=default
    var overrideLanguage by text("OverrideLanguage","")

    // 原版 UI 字体：开启后用自定义字体替换 Minecraft 原版 UI 的字体渲染。
    // 这里只保存字体的标识（名称/字号）而不是 FontRenderer：
    // ClientConfiguration 在 Minecraft.startGame() 早期就会被初始化（setWindowIcon 回调），
    // 那时 mc.fontRendererObj 还没创建，直接持有 FontRenderer 会 NPE（Fonts.minecraftFont 是 lazy）。
    var customGameFont by boolean("CustomGameFont", false)
    var gameFontShadow by boolean("GameFontShadow", true)
    var gameFontName by text("GameFont", "Minecraft Font")
    var gameFontSize by int("GameFontSize", -1, -1..256)

    private var cachedGameFont: FontRenderer? = null
    private var cachedGameFontName: String? = null
    private var cachedGameFontSize: Int = Int.MIN_VALUE

    /** 解析当前选择的字体；注册表中找不到时回退到原版字体。结果会缓存，避免每帧线性查找。 */
    fun resolvedGameFont(): FontRenderer {
        if (cachedGameFont == null || cachedGameFontName != gameFontName || cachedGameFontSize != gameFontSize) {
            cachedGameFontName = gameFontName
            cachedGameFontSize = gameFontSize
            cachedGameFont = Fonts.getFontRenderer(gameFontName, gameFontSize)
        }
        return cachedGameFont!!
    }

    /** 当前字体在按钮上显示的名字。 */
    fun gameFontLabel(): String {
        val font = resolvedGameFont()
        if (font === Fonts.minecraftFont) return "Minecraft"
        val info = Fonts.getFontDetails(font)
        return if (info == null || info.size == -1) info?.name ?: "Unknown" else "${info.name} - ${info.size}"
    }

    fun updateClientWindow() {
        if (clientTitle) {
            // Set LiquidBounce title
            Display.setTitle(LiquidBounce.clientTitle)
            // Update favicon
            IconUtils.favicon?.let { icons ->
                Display.setIcon(icons)
            }
        } else {
            // Set original title
            Display.setTitle("Minecraft 1.8.9")
            // Update favicon
            mc.setWindowIcon()
        }
    }

}