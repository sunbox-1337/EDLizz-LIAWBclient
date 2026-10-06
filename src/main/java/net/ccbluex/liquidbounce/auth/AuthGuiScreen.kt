package net.ccbluex.liquidbounce.auth

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import org.lwjgl.input.Keyboard
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets

class AuthGuiScreen : GuiScreen() {

    companion object {
        var isAuthenticated = false   // 全局认证状态，防止重复拦截主菜单
    }

    private lateinit var usernameField: GuiTextField
    private lateinit var passwordField: GuiTextField
    private lateinit var inviteField: GuiTextField
    private var statusMessage = ""
    private var isRegistering = false
    private var isProcessing = false

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        val centerX = width / 2
        val fontH = fontRendererObj.FONT_HEIGHT
        val startY = height / 4 + 50

        // 保留上次输入的内容，切换模式时不丢失
        val lastUsername = if (::usernameField.isInitialized) usernameField.text else ""
        val lastPassword = if (::passwordField.isInitialized) passwordField.text else ""

        usernameField = GuiTextField(0, fontRendererObj, centerX - 100, startY, 200, 20)
        passwordField = GuiTextField(1, fontRendererObj, centerX - 100, startY + 30, 200, 20)
        inviteField   = GuiTextField(2, fontRendererObj, centerX - 100, startY + 60, 200, 20)

        usernameField.text = lastUsername
        passwordField.text = lastPassword
        passwordField.setEnableBackgroundDrawing(true)
        usernameField.maxStringLength = 16
        passwordField.maxStringLength = 64
        inviteField.maxStringLength = 32

        buttonList.clear()
        if (isRegistering) {
            // 注册模式：只有确认和返回按钮
            buttonList.add(GuiButton(1, centerX - 100, startY + 85, 95, 20, "Confirm"))
            buttonList.add(GuiButton(2, centerX + 5, startY + 85, 95, 20, "Back"))
        } else {
            // 普通模式：登录、注册、离线
            buttonList.add(GuiButton(0, centerX - 100, startY + 85, 60, 20, "Login"))
            buttonList.add(GuiButton(1, centerX - 35, startY + 85, 60, 20, "Register"))
            buttonList.add(GuiButton(3, centerX + 30, startY + 85, 70, 20, "Offline"))

            // 清空按钮放在第二行
            buttonList.add(GuiButton(2, centerX - 50, startY + 110, 100, 20, "Clear"))
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        val centerX = width / 2
        val fontH = fontRendererObj.FONT_HEIGHT
        val startY = height / 4 + 50
        val gap = 1  // 标签与输入框之间的垂直间距（像素）

        drawCenteredString(fontRendererObj, "Server Authentication", centerX, height / 4, 0xFFFFFF)

        // 标签绘制在输入框上方 gap 处，确保不重叠
        drawString(fontRendererObj, "Username:", centerX - 100, startY - fontH - gap, 0xA0A0A0)
        drawString(fontRendererObj, "Password:", centerX - 100, startY + 30 - fontH - gap, 0xA0A0A0)
        if (isRegistering) {
            drawString(fontRendererObj, "Invite Code:", centerX - 100, startY + 60 - fontH - gap, 0xA0A0A0)
            inviteField.drawTextBox()
        }

        usernameField.drawTextBox()
        passwordField.drawTextBox()

        if (statusMessage.isNotEmpty()) {
            drawCenteredString(fontRendererObj, statusMessage, centerX, startY + 135, 0xFF5555)
        }
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (isProcessing) return
        usernameField.textboxKeyTyped(typedChar, keyCode)
        passwordField.textboxKeyTyped(typedChar, keyCode)
        if (isRegistering) inviteField.textboxKeyTyped(typedChar, keyCode)
        if (keyCode == Keyboard.KEY_RETURN) {
            // 按下回车时模拟点击登录/确认按钮（非离线）
            actionPerformed(buttonList[if (isRegistering) 0 else 0])
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (isProcessing) return
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton)
        } catch (e: java.io.IOException) {
            e.printStackTrace()
        }
        usernameField.mouseClicked(mouseX, mouseY, mouseButton)
        passwordField.mouseClicked(mouseX, mouseY, mouseButton)
        if (isRegistering) inviteField.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun actionPerformed(button: GuiButton?) {
        if (button == null || isProcessing) return
        when (button.id) {
            0 -> { // 登录
                val username = usernameField.text.trim()
                val password = passwordField.text.trim()
                if (username.isEmpty() || password.isEmpty()) {
                    statusMessage = "Please enter username and password"
                    return
                }
                isProcessing = true
                Thread {
                    val json = "{\"action\":\"login\",\"username\":\"$username\",\"password\":\"$password\"}"
                    val response = sendTcp(json)
                    Minecraft.getMinecraft().addScheduledTask {
                        if (response.contains("\"success\": true")) {
                            isAuthenticated = true
                            Minecraft.getMinecraft().displayGuiScreen(GuiMainMenu())
                        } else {
                            statusMessage = "Login failed! Check credentials."
                            isProcessing = false
                        }
                    }
                }.start()
            }
            1 -> { // 注册 / 确认
                if (!isRegistering) {
                    // 切换到注册模式
                    isRegistering = true
                    statusMessage = ""
                    initGui()
                } else {
                    val username = usernameField.text.trim()
                    val password = passwordField.text.trim()
                    val invite = inviteField.text.trim()
                    if (username.isEmpty() || password.isEmpty() || invite.isEmpty()) {
                        statusMessage = "All fields are required"
                        return
                    }
                    isProcessing = true
                    Thread {
                        val json = "{\"action\":\"register\",\"username\":\"$username\",\"password\":\"$password\",\"inviteCode\":\"$invite\"}"
                        val response = sendTcp(json)
                        Minecraft.getMinecraft().addScheduledTask {
                            if (response.contains("\"success\": true")) {
                                statusMessage = "Registered! Logging in..."
                                isProcessing = false
                                actionPerformed(GuiButton(0, 0, 0, "")) // 自动登录
                            } else {
                                statusMessage = "Registration failed! Check invite code."
                                isProcessing = false
                            }
                        }
                    }.start()
                }
            }
            2 -> { // 返回 / 清空
                if (isRegistering) {
                    isRegistering = false
                    statusMessage = ""
                    inviteField.text = ""
                    initGui()
                } else {
                    usernameField.text = ""
                    passwordField.text = ""
                    statusMessage = ""
                    initGui()
                }
            }
            3 -> { // 离线游玩
                isAuthenticated = true
                Minecraft.getMinecraft().displayGuiScreen(GuiMainMenu())
            }
        }
    }

    private fun sendTcp(json: String): String {
        return try {
            val socket = Socket("194.147.16.88", 58755)
            socket.soTimeout = 3000
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            writer.write(json + "\n")
            writer.flush()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val result = reader.readLine() ?: ""

            // 登录 / 注册成功后把凭据同步给 IRC 客户端，IRC 面板打开即可用。
            // 放这里是因为这个函数同时拿到「发出的请求」和「收到的响应」，
            // 不用去翻登录成功分支在哪。
            runCatching {
                val response = com.google.gson.JsonParser().parse(result).asJsonObject
                val request = com.google.gson.JsonParser().parse(json).asJsonObject

                if (response.get("success")?.asBoolean == true &&
                    request.get("action")?.asString in listOf("login", "register")
                ) {
                    net.ccbluex.liquidbounce.chat.IrcClient.username = request.get("username")?.asString
                    net.ccbluex.liquidbounce.chat.IrcClient.password = request.get("password")?.asString
                }
            }
            socket.close()
            result
        } catch (e: Exception) {
            Minecraft.getMinecraft().addScheduledTask {
                statusMessage = "Network error: ${e.message}"
                isProcessing = false
            }
            ""
        }
    }
}