/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.clickgui

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.api.ClientApi
import net.ccbluex.liquidbounce.api.autoSettingsList
import net.ccbluex.liquidbounce.api.loadSettings
import net.ccbluex.liquidbounce.config.SettingsUtils
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.guiColor
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.scale
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.scrolls
import net.ccbluex.liquidbounce.file.FileManager.clickGuiConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.ButtonElement
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.ModuleElement
import net.ccbluex.liquidbounce.ui.client.clickgui.style.Style
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.BlackStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.LiquidBounceStyle
import net.ccbluex.liquidbounce.ui.client.clickgui.style.styles.SlowlyStyle
import net.ccbluex.liquidbounce.ui.client.hud.HUD
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.Targets
import net.ccbluex.liquidbounce.utils.client.ClientUtils
import net.ccbluex.liquidbounce.utils.client.asResourceLocation
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.playSound
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawImage
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.guiBackgroundColor
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager.disableLighting
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11.glScaled
import kotlin.math.roundToInt

object ClickGui : GuiScreen() {

    // Note: hash key = [Panel.name]
    val panels = linkedSetOf<Panel>()
    private val hudIcon = ResourceLocation("${CLIENT_NAME.lowercase()}/custom_hud_icon.png")

    /** IRC 面板开关 + 展开进度（纯前端 UI，不接后端） */
    private var ircOpen = false
    private var ircProgress = 0F
    var style: Style = LiquidBounceStyle
    private var mouseX = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }
    private var mouseY = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    private var autoScrollY: Int? = null

    // Used when closing ClickGui using its key bind, prevents it from getting closed instantly after getting opened.
    // Caused by keyTyped being called along with onKey that opens the ClickGui.
    private var ignoreClosing = false

    fun setDefault() {
        panels.clear()

        val width = 100
        val height = 18
        var yPos = 5

        for (category in Category.entries) {
            panels += Panel(
                category.displayName,
                x = 100,
                y = yPos,
                width,
                height,
                false,
                moduleManager[category].map(::ModuleElement)
            )

            yPos += 20
        }

        yPos += 20
        panels += setupTargetsPanel(100, yPos, width, height)

        // Settings Panel
        yPos += 20
        panels += setupSettingsPanel(100, yPos, width, height)
    }

    private fun setupTargetsPanel(xPos: Int = 100, yPos: Int, width: Int, height: Int) =
        Panel("Targets", xPos, yPos, width, height, false, listOf(
            ButtonElement("Players", { if (Targets.player) guiColor else Int.MAX_VALUE }) {
                Targets.player = !Targets.player
            },
            ButtonElement("Mobs", { if (Targets.mob) guiColor else Int.MAX_VALUE }) {
                Targets.mob = !Targets.mob
            },
            ButtonElement("Animals", { if (Targets.animal) guiColor else Int.MAX_VALUE }) {
                Targets.animal = !Targets.animal
            },
            ButtonElement("Invisible", { if (Targets.invisible) guiColor else Int.MAX_VALUE }) {
                Targets.invisible = !Targets.invisible
            },
            ButtonElement("Dead", { if (Targets.dead) guiColor else Int.MAX_VALUE }) {
                Targets.dead = !Targets.dead
            },
        ))

    private fun setupSettingsPanel(xPos: Int = 100, yPos: Int, width: Int, height: Int): Panel {
        val list = autoSettingsList?.map { setting ->
            ButtonElement(setting.name, { Integer.MAX_VALUE }) {
                SharedScopes.IO.launch {
                    try {
                        chat("Loading settings...")

                        // Load settings and apply them
                        val settings = ClientApi.getSettingsScript(settingId = setting.settingId)

                        chat("Applying settings...")
                        SettingsUtils.applyScript(settings)

                        chat("§6Settings applied successfully.")
                        HUD.addNotification(Notification.informative("ClickGUI", "Updated Settings"))
                        mc.playSound("random.anvil_use".asResourceLocation())
                    } catch (e: Exception) {
                        ClientUtils.LOGGER.error("Failed to load settings", e)
                        chat("Failed to load settings: ${e.message}")
                    }
                }
            }.apply {
                this.hoverText = buildString {
                    appendLine("§7Description: §e${setting.description.ifBlank { "No description available" }}")
                    appendLine("§7Type: §e${setting.type.displayName}")
                    appendLine("§7Contributors: §e${setting.contributors}")
                    appendLine("§7Last updated: §e${setting.date}")
                    append("§7Status: §e${setting.statusType.displayName} §a(${setting.statusDate})")
                }
            }
        } ?: run {
            // Try load settings
            loadSettings(useCached = true) {
                mc.addScheduledTask {
                    setupSettingsPanel(xPos, yPos, width, height)
                }
            }

            emptyList()
        }

        return Panel("Auto Settings", xPos, yPos, width, height, false, list)
    }

    override fun drawScreen(x: Int, y: Int, partialTicks: Float) {
        // Enable DisplayList optimization
        assumeNonVolatile {
            mouseX = (x / scale).roundToInt()
            mouseY = (y / scale).roundToInt()

            // 背景遮罩，颜色可自定义（原来是 drawDefaultBackground() 的半透明黑）
            drawRect(0, 0, width, height, guiBackgroundColor.rgb)
            drawImage(hudIcon, 9, height - 41, 32, 32)

            // IRC 开关动画：位移正比于剩余距离 —— 起步快、越接近目标越慢
            val ircRemaining = (if (ircOpen) 1F else 0F) - ircProgress
            ircProgress = if (ircRemaining > -0.005F && ircRemaining < 0.005F) {
                if (ircOpen) 1F else 0F
            } else {
                ircProgress + ircRemaining * 0.17F
            }

            // IRC 按钮：紧接 HUD 图标右边，同尺寸 32×32
            val ircHovered = x in 41..77 && y in height - 46..height - 9
            drawRect(
                45, height - 41, 77, height - 9,
                if (ircHovered || ircOpen) 0x50FFFFFF else 0x28FFFFFF
            )
            drawCenteredString(fontRendererObj, "IRC", 61, height - 27, -1)

            val scale = scale.toDouble()
            glScaled(scale, scale, scale)

            for (panel in panels) {
                panel.updateFade(deltaTime)
                // IRC 打开时模块列表同步收起（fade 同时驱动面板高度和内容高度）
                panel.fade = (panel.fade * (1F - ircProgress)).toInt()
                panel.drawScreenAndClick(mouseX, mouseY)
            }

            // IRC 面板：纯前端 UI，圆角/阴影/模糊沿用 ClickGUI 自己的参数
            if (ircProgress > 0.01F) {
                LiquidBounceStyle.drawIrcPanel(ircProgress, mouseX, mouseY)
            }

            descriptions@ for (panel in panels.reversed()) {
                // Don't draw hover text when hovering over a panel header.
                if (panel.isHovered(mouseX, mouseY)) break

                for (element in panel.elements) {
                    if (element is ButtonElement) {
                        if (element.isVisible && element.hoverText.isNotBlank() && element.isHovered(
                                mouseX, mouseY
                            ) && element.y <= panel.y + panel.fade
                        ) {
                            style.drawHoverText(mouseX, mouseY, element.hoverText)
                            // Don't draw hover text for any elements below.
                            break@descriptions
                        }
                    }
                }
            }

            if (Mouse.hasWheel()) {
                val wheel = autoScrollY?.let { it - y } ?: Mouse.getDWheel()

                if (wheel != 0) {
                    var handledScroll = false

                    // Handle foremost panel.
                    for (panel in panels.reversed()) {
                        if (panel.handleScroll(mouseX, mouseY, wheel)) {
                            handledScroll = true
                            break
                        }
                    }

                    if (!handledScroll) handleScroll(wheel)
                }
            }

            disableLighting()
            RenderHelper.disableStandardItemLighting()
            glScaled(1.0, 1.0, 1.0)
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun handleScroll(wheel: Int) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            // IRC 面板打开时，滚轮只滚聊天，不再缩放整个 ClickGUI
            if (ircOpen) {
                net.ccbluex.liquidbounce.chat.IrcClient.scroll += wheel
            } else {
                scale += wheel * 0.0001f
            }

            for (panel in panels) {
                panel.x = panel.parseX()
                panel.y = panel.parseY()
            }

        } else if (scrolls) {
            if (ircOpen) {
                net.ccbluex.liquidbounce.chat.IrcClient.scroll += wheel
            } else {
                for (panel in panels) panel.y = panel.parseY(panel.y + wheel / 10)
            }
        }
    }

    /**
     * IRC 输入：面板打开且输入框聚焦时，键盘归 IRC 用。
     * 别名 irc 指向 IrcClient，避免到处写全限定名。
     */
    private fun ircKeyTyped(typedChar: Char, keyCode: Int) {
        val irc = net.ccbluex.liquidbounce.chat.IrcClient

        if (ircOpen && irc.inputFocused) {
            // Ctrl+V 粘贴。1.8 的自绘 GUI 不接系统 IME，中文只能靠粘贴进来
            if (keyCode == org.lwjgl.input.Keyboard.KEY_V &&
                (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL) ||
                    org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL))
            ) {
                runCatching {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val text = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                    if (!text.isNullOrEmpty()) {
                        // 换行会破坏单行输入框，统一换成空格
                        irc.draft += text.replace('\n', ' ').replace('\r', ' ')
                    }
                }
                return
            }

            when (keyCode) {
                org.lwjgl.input.Keyboard.KEY_RETURN, org.lwjgl.input.Keyboard.KEY_NUMPADENTER -> {
                    if (irc.draft.isNotBlank()) {
                        irc.send(irc.draft)
                        irc.draft = ""
                    }
                    return
                }

                org.lwjgl.input.Keyboard.KEY_ESCAPE -> {
                    irc.inputFocused = false
                    return
                }

                org.lwjgl.input.Keyboard.KEY_BACK -> {
                    if (irc.draft.isNotEmpty()) {
                        irc.draft = irc.draft.dropLast(1)
                    }
                    return
                }
            }

            // 只收可见字符，控制字符（含退格/回车）已经在上面处理掉了
            if (!typedChar.isISOControl()) {
                irc.draft += typedChar
            }
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    public override fun mouseClicked(x: Int, y: Int, mouseButton: Int) {
        if (mouseButton == 0 && x in 8..42 && y in height - 42..height - 8) {
            mc.displayGuiScreen(GuiHudDesigner())
            return
        }

        // IRC 按钮
        if (mouseButton == 0 && x in 41..77 && y in height - 46..height - 9) {
            ircOpen = !ircOpen
            return
        }

        // IRC 面板的关闭按钮（包围盒是上一帧绘制时留下的）
        if (ircOpen && mouseButton == 0) {
            val ircLocalX = (x / scale).roundToInt()
            val ircLocalY = (y / scale).roundToInt()
            val bounds = LiquidBounceStyle.ircCloseBounds

            if (bounds != null && ircLocalX >= bounds[0] && ircLocalX <= bounds[2] &&
                ircLocalY >= bounds[1] && ircLocalY <= bounds[3]
            ) {
                ircOpen = false
                return
            }
        }

        // IRC 输入框：点中聚焦，点面板别处失焦
        if (ircOpen && mouseButton == 0) {
            val ircClient = net.ccbluex.liquidbounce.chat.IrcClient
            val ircLocalX = (x / scale).roundToInt()
            val ircLocalY = (y / scale).roundToInt()
            val inputBounds = ircClient.inputBounds

            // 发送按钮：紧贴文本区右边界（宽度 26，样式那边定的）
            if (inputBounds != null &&
                ircLocalX > inputBounds[2] && ircLocalX <= inputBounds[2] + 29F &&
                ircLocalY >= inputBounds[1] && ircLocalY <= inputBounds[3]
            ) {
                if (ircClient.draft.isNotBlank()) {
                    ircClient.send(ircClient.draft)
                    ircClient.draft = ""
                }
                return
            }

            if (inputBounds != null && ircLocalX >= inputBounds[0] && ircLocalX <= inputBounds[2] &&
                ircLocalY >= inputBounds[1] && ircLocalY <= inputBounds[3]
            ) {
                if (ircClient.isLoggedIn) {
                    ircClient.inputFocused = true
                } else {
                    // 未登录时这块是「登录」按钮 → 打开主界面 auth
                    mc.displayGuiScreen(net.ccbluex.liquidbounce.auth.AuthGuiScreen())
                }
                return
            }

            ircClient.inputFocused = false
        }

        // IRC 面板打开时，除它自己的控件外一律不响应 ——
        // 否则点击会穿透到下面的模块面板（误开关模块、误拖拽、中键滚动等）
        if (ircOpen) return

        if (mouseButton == 2) {
            autoScrollY = y
        }

        mouseX = (x / scale).roundToInt()
        mouseY = (y / scale).roundToInt()

        // Handle foremost panel.
        panels.reversed().forEachIndexed { index, panel ->
            if (panel.mouseClicked(mouseX, mouseY, mouseButton)) return

            panel.drag = false

            if (mouseButton == 0 && panel.isHovered(mouseX, mouseY)) {
                panel.x2 = panel.x - mouseX
                panel.y2 = panel.y - mouseY
                panel.drag = true

                // Move dragged panel to top.
                panels.remove(panel)
                panels += panel
                return
            }
        }
    }

    public override fun mouseReleased(x: Int, y: Int, button: Int) {
        mouseX = (x / scale).roundToInt()
        mouseY = (y / scale).roundToInt()

        if (button == 2) {
            autoScrollY = null
        }

        for (panel in panels) panel.mouseReleased(mouseX, mouseY, button)
    }

    override fun updateScreen() {
        if (style is SlowlyStyle || style is BlackStyle) {
            for (panel in panels) {
                for (element in panel.elements) {
                    if (element is ButtonElement) element.hoverTime += if (element.isHovered(mouseX, mouseY)) 1 else -1

                    if (element is ModuleElement) element.slowlyFade += if (element.module.state) 20 else -20
                }
            }
        }

        super.updateScreen()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // IRC 输入优先：面板打开且输入框聚焦时，键盘不再触发关闭 GUI 等原有逻辑
        val ircWasFocused = ircOpen && net.ccbluex.liquidbounce.chat.IrcClient.inputFocused
        ircKeyTyped(typedChar, keyCode)
        if (ircWasFocused) return

        // Close ClickGUI by using its key bind.
        if (keyCode in arrayOf(ClickGUI.keyBind, Keyboard.KEY_ESCAPE)) {
            if (style.chosenText != null) {
                style.chosenText = null
                return
            }

            if (keyCode != Keyboard.KEY_ESCAPE) {
                if (ignoreClosing) {
                    ignoreClosing = false
                } else {
                    mc.displayGuiScreen(null)
                }

                return
            }
        }

        style.chosenText?.processInput(typedChar, keyCode) { style.moveRGBAIndexBy(it) }

        super.keyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        autoScrollY = null
        saveConfig(clickGuiConfig)
        Keyboard.enableRepeatEvents(false)
        for (panel in panels) panel.fade = 0
    }

    override fun initGui() {
        ignoreClosing = true
    }

    fun Int.clamp(min: Int, max: Int): Int = this.coerceIn(min, max.coerceAtLeast(0))

    override fun doesGuiPauseGame() = false
}