/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.api.ClientUpdate
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.client.altmanager.GuiAltManager
import net.ccbluex.liquidbounce.ui.client.fontmanager.GuiFontManager
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.JavaVersion
import net.ccbluex.liquidbounce.utils.client.javaVersion
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiOptions
import net.minecraft.client.gui.GuiSelectWorld
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.resources.I18n
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Mouse
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.awt.Color

class GuiMainMenu : AbstractScreen() {

    private var popup: PopupScreen? = null

    companion object {
        private var popupOnce = false
        var lastWarningTime: Long? = null
        private val warningInterval = TimeUnit.DAYS.toMillis(7)

        fun shouldShowWarning() = lastWarningTime == null || Instant.now().toEpochMilli() - lastWarningTime!! > warningInterval
    }

    // 根据你的实际路径 resources/assets/minecraft/lizz/background.png
    private val customBackground = ResourceLocation("minecraft", "lizz/background.png")

    init {
        if (!popupOnce) {
            javaVersion?.let {
                when {
                    it.major == 1 && it.minor == 8 && it.update < 100 -> showOutdatedJava8Warning()
                    it.major > 8 -> showJava11Warning()
                }
            }
            when {
                FileManager.firstStart -> showWelcomePopup()
                //hasUpdate() -> showUpdatePopup()
                //shouldShowWarning() -> showDiscontinuedWarning()
            }
            popupOnce = true
        }
    }

    override fun initGui() {
        val defaultHeight = height / 4 + 24*2
        val baseCol1 = width / 2 - 100
        val baseCol2 = width / 2 + 2

        +GuiButton(100, baseCol2, defaultHeight + 26 * 2, 94, 23, "Alts")
        +GuiButton(1, baseCol1, defaultHeight, 98*2, 23, I18n.format("menu.singleplayer"))
        +GuiButton(2, baseCol1, defaultHeight + 26, 98*2, 23, I18n.format("menu.multiplayer"))
        +GuiButton(110, baseCol1, defaultHeight + 26 * 2, 98, 23, "Options")
    }

    private fun showWelcomePopup() {
        popup = PopupScreen {
            title("§a§lWelcome!")
            message("""
                §eThank you for downloading and installing §bLIAWB§e!
        
                §6Here is some information you might find useful:§r
                §a- §fClickGUI:§r Press §7[RightShift]§f to open ClickGUI.
                §a- §fRight-click modules with a '+' to edit.
                §a- §fHover over a module to see its description.
        
                §6Important Commands:§r
                §a- §f.bind <module> <key> / .bind <module> none
                §a- §f.config load <name> / .config list
            """.trimIndent())
            button("§aOK")
            onClose { popup = null }
        }
    }

    private fun showUpdatePopup() {
        val newestVersion = ClientUpdate.newestVersion ?: return

        val isReleaseBuild = newestVersion.release
        val updateType = if (isReleaseBuild) "version" else "development build"

        val dateFormatter = SimpleDateFormat("EEEE, MMMM dd, yyyy, h a z", Locale.ENGLISH)
        val newestVersionDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(newestVersion.date)
        val formattedNewestDate = dateFormatter.format(newestVersionDate)

        popup = PopupScreen {
            title("§bNew Update Available!")
            message("""
                §eA new $updateType of LIAWB is available!
        
                - ${if (isReleaseBuild) "§aVersion" else "§aBuild ID"}:§r ${if (isReleaseBuild) newestVersion.lbVersion else newestVersion.buildId}
                - §aMinecraft Version:§r ${newestVersion.mcVersion}
                - §aBranch:§r ${newestVersion.branch}
                - §aDate:§r $formattedNewestDate
        
                §6Changes:§r
                ${newestVersion.message}
        
                §bUpgrade now to enjoy the latest features and improvements!§r
            """.trimIndent())
            button("§aDownload") { MiscUtils.showURL(newestVersion.url) }
            onClose { popup = null }
        }
    }

    private fun showOutdatedJava8Warning() {
        popup = PopupScreen {
            title("§c§lOutdated Java Runtime Environment")
            message("""
                §6§lYou are using an outdated version of Java 8 (${javaVersion!!.raw}).§r
                
                §fThis might cause unexpected §c§lBUGS§f.
                Please update it to 8u101+, or get a new one from the Internet.
            """.trimIndent())
            button("§aDownload Java") { MiscUtils.showURL(JavaVersion.DOWNLOAD_PAGE) }
            button("§eI realized")
            onClose { popup = null }
        }
    }

    private fun showJava11Warning() {
        popup = PopupScreen {
            title("§c§lInappropriate Java Runtime Environment")
            message("""
                §6§lThis version of LIAWB is designed for Java 8 environment.§r
                
                §fHigher versions of Java might cause bug or crash.
                You can get JRE 8 from the Internet.
            """.trimIndent())
            button("§aDownload Java") { MiscUtils.showURL(JavaVersion.DOWNLOAD_PAGE) }
            button("§eI realized")
            onClose { popup = null }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // 使用与其他原版界面一致的背景处理（走 MixinGuiScreen.drawBackground → lizz 背景 shader/图片）
        drawBackground(0)

        // 绘制标题
        if (LiquidBounce.shouldShowSpecialTitle){
            Fonts.fontRegular180.drawCenteredString("zzzzzzzz", (width-5) / 2F, height / 8F, Color.WHITE.rgb, true)
        }else{
            Fonts.fontRegular180.drawCenteredString("LIAWB", (width-5) / 2F, height / 8F, Color.WHITE.rgb, true)
        }
        Fonts.fontRegular45.drawString("WebUI: http://localhost:8080/", 0f, height-10f, Color.WHITE.rgb)

        super.drawScreen(mouseX, mouseY, partialTicks)

        if (popup != null) {
            popup!!.drawScreen(width, height, mouseX, mouseY)
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (popup != null) {
            popup!!.mouseClicked(mouseX, mouseY, mouseButton)
            return
        }
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun actionPerformed(button: GuiButton) {
        if (popup != null) return

        when (button.id) {
            1 -> mc.displayGuiScreen(GuiSelectWorld(this))
            2 -> mc.displayGuiScreen(GuiMultiplayer(this))
            4 -> mc.shutdown()
            100 -> mc.displayGuiScreen(GuiAltManager(this))
            110 -> mc.displayGuiScreen(GuiOptions(this))
        }
    }

    override fun handleMouseInput() {
        if (popup != null) {
            val eventDWheel = Mouse.getEventDWheel()
            if (eventDWheel != 0) {
                popup!!.handleMouseWheel(eventDWheel)
            }
        }
        super.handleMouseInput()
    }
}