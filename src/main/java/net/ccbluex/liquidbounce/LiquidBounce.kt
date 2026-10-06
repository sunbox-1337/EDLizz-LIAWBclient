package net.ccbluex.liquidbounce

import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.google.gson.Gson
import de.florianmichael.viamcp.ViaMCP
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.api.ClientUpdate
import net.ccbluex.liquidbounce.api.ClientUpdate.gitInfo
import net.ccbluex.liquidbounce.api.loadSettings
import net.ccbluex.liquidbounce.auth.AuthListener
import net.ccbluex.liquidbounce.cape.CapeService
import net.ccbluex.liquidbounce.event.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.StartupEvent
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.command.CommandManager.registerCommands
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.ModuleManager.registerModules
import net.ccbluex.liquidbounce.features.module.modules.misc.ClientDetector
import net.ccbluex.liquidbounce.features.special.BungeeCordSpoof
import net.ccbluex.liquidbounce.features.special.ClientFixes
import net.ccbluex.liquidbounce.features.special.ClientRichPresence
import net.ccbluex.liquidbounce.features.special.ClientRichPresence.showRPCValue
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.file.FileManager.loadAllConfigs
import net.ccbluex.liquidbounce.file.FileManager.saveAllConfigs
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.updateClientWindow
import net.ccbluex.liquidbounce.lang.LanguageManager.loadLanguages
import net.ccbluex.liquidbounce.script.ScriptManager
import net.ccbluex.liquidbounce.script.ScriptManager.enableScripts
import net.ccbluex.liquidbounce.script.ScriptManager.loadScripts
import net.ccbluex.liquidbounce.script.remapper.Remapper
import net.ccbluex.liquidbounce.script.remapper.Remapper.loadSrg
import net.ccbluex.liquidbounce.tabs.BlocksTab
import net.ccbluex.liquidbounce.tabs.ExploitsTab
import net.ccbluex.liquidbounce.tabs.HeadsTab
import net.ccbluex.liquidbounce.ui.client.altmanager.GuiAltManager.Companion.loadActiveGenerators
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.ccbluex.liquidbounce.ui.client.hud.HUD
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.ClassUtils.hasForge
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.client.ClientUtils.disableFastRender
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.io.MiscUtils.showErrorPopup
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.movement.BPSUtils
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.ccbluex.liquidbounce.utils.movement.TimerBalanceUtils
import net.ccbluex.liquidbounce.utils.render.MiniMapRegister
import net.ccbluex.liquidbounce.utils.render.shader.Background
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.timing.TickedActions
import net.ccbluex.liquidbounce.utils.timing.WaitTickUtils
import net.ccbluex.liquidbounce.web.ClickGuiWebInterface
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.UIManager
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge

object LiquidBounce {

    const val CLIENT_NAME = "L"+"izz"
    // User-facing brand name shown as the prefix of client chat messages, e.g. [LIAWB]
    const val CLIENT_DISPLAY_NAME = "LIAWB"
    const val CLIENT_AUTHOR = "CCBlueX"
    const val CLIENT_CLOUD = "https://cloud.liquidbounce.net/LiquidBounce"
    const val CLIENT_WEBSITE = "sunboxmc.fucku.top"
    const val CLIENT_GITHUB = "https://github.com/CCBlueX/LiquidBounce"
    const val MINECRAFT_VERSION = "1.8.9"

    val clientVersionText = "b9.4"
    val clientVersionNumber = clientVersionText.substring(1).toIntOrNull() ?: 0
    val clientCommit = gitInfo["git.commit.id.abbrev"]?.let { "git-$it" } ?: "unknown"
    val clientBranch = gitInfo["git.branch"]?.toString() ?: "unknown"
    const val IN_DEV = false
    val clientTitle = "LIAWB client 1.8.9  " + clientVersionText + " | 免费注册加群：815061613"
    val shouldShowSpecialTitle = kotlin.random.Random.nextDouble() <= 0.01
    var isStarting = true

    val moduleManager = ModuleManager
    val commandManager = CommandManager
    val eventManager = EventManager
    val fileManager = FileManager
    val scriptManager = ScriptManager
    val hud = HUD
    val clickGui = ClickGui
    var background: Background? = null
    val clientRichPresence = ClientRichPresence

    // ========== 后台网络服务 ==========
    private val gson = Gson()
    private val serverHost = "194.147.16.88"
    private val serverPort = 58755
    private val clientId = UUID.randomUUID().toString()

    fun preload(): Future<*> {
        System.setProperty("forge.disableVersionCheck", "true")
        net.ccbluex.liquidbounce.utils.client.javaVersion
        UIManager.setLookAndFeel(FlatMacLightLaf())
        val future = CompletableFuture<Unit>()
        SharedScopes.IO.launch {
            try {
                LOGGER.info("Starting preload tasks of $CLIENT_NAME")
                loadLanguages()
                loadActiveGenerators()
                loadSrg()
                LOGGER.info("Preload tasks of $CLIENT_NAME are completed!")
                future.complete(Unit)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    fun startClient() {
        isStarting = true
        LOGGER.info("Starting $CLIENT_NAME $clientVersionText, by $CLIENT_AUTHOR")
        try {
            Fonts.loadFonts()
            RotationUtils
            ClientFixes
            BungeeCordSpoof
            CapeService
            InventoryUtils
            InventoryManager
            MiniMapRegister
            TickedActions
            MovementUtils
            PacketUtils
            TimerBalanceUtils
            BPSUtils
            WaitTickUtils
            SilentHotbar
            BlinkUtils
            ViaMCP.create()
            ViaMCP.INSTANCE.initAsyncSlider()
            ClickGuiWebInterface.start()
            loadSettings(false) {
                LOGGER.info("Successfully loaded ${it.size} settings.")
            }
            registerCommands()
            registerModules()
            runCatching {
                loadSrg()
                if (!Remapper.mappingsLoaded) error("Failed to load SRG mappings.")
                loadScripts()
                enableScripts()
            }.onFailure {
                LOGGER.error("Failed to load scripts.", it)
            }
            loadAllConfigs()
            updateClientWindow()
            if (hasForge()) {
                BlocksTab()
                ExploitsTab()
                HeadsTab()
            }
            disableFastRender()
            if (showRPCValue) {
                SharedScopes.IO.launch {
                    try { clientRichPresence.setup() }
                    catch (throwable: Throwable) { LOGGER.error("Failed to setup Discord RPC.", throwable) }
                }
            }
            if (CapeService.knownToken.isNotBlank()) {
                SharedScopes.IO.launch {
                    runCatching {
                        CapeService.login(CapeService.knownToken)
                    }.onFailure {
                        LOGGER.error("Failed to login into known cape token.", it)
                    }.onSuccess {
                        LOGGER.info("Successfully logged in into known cape token.")
                    }
                }
            }
            CapeService.refreshCapeCarriers {
                LOGGER.info("Successfully loaded ${it.size} cape carriers.")
            }
            FileManager.loadBackground()

            // 延迟注册登录验证监听器
            Minecraft.getMinecraft().addScheduledTask {
                MinecraftForge.EVENT_BUS.register(AuthListener())
            }

            // ========== 启动后台在线循环 ==========
            ClientDetector.clientId = clientId
            startOnlineLoop()

        } catch (e: Exception) {
            LOGGER.error("Failed to start client: ${e.message}")
            e.showErrorPopup()
        } finally {
            isStarting = false
            if (!FileManager.firstStart && FileManager.backedup) {
                SharedScopes.IO.launch {
                    MiscUtils.showMessageDialog("Warning: backup triggered", "Client update detected! Please check the config folder.")
                }
            }
            EventManager.call(StartupEvent)
            LOGGER.info("Successfully started client")
        }
    }

    private fun startOnlineLoop() {
        SharedScopes.IO.launch {
            while (true) {
                try {
                    val player = Minecraft.getMinecraft().thePlayer
                    if (player != null) {
                        val playerName = player.name
                        if (playerName.isNotEmpty()) {
                            sendHeartbeat(playerName)
                            val names = fetchOnlineNames()
                            synchronized(ClientDetector.onlineClientNames) {
                                ClientDetector.onlineClientNames.clear()
                                ClientDetector.onlineClientNames.addAll(names)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // 忽略网络错误
                }
                delay(10_000)
            }
        }
    }

    private fun sendHeartbeat(playerName: String) {
        val request = mapOf(
            "action" to "report_id",
            "client_id" to clientId,
            "player_name" to playerName
        )
        sendTcp(gson.toJson(request))
    }

    private fun fetchOnlineNames(): List<String> {
        val request = mapOf("action" to "get_online_ids")
        val response = sendTcp(gson.toJson(request))
        if (response.isNotBlank()) {
            val map = gson.fromJson(response, Map::class.java)
            val clients = map["online_clients"] as? List<*>
            if (clients != null) {
                val names = mutableListOf<String>()
                for (c in clients) {
                    if (c is Map<*, *>) {
                        val name = c["player_name"] as? String
                        if (name != null) names.add(name)
                    }
                }
                return names
            }
        }
        return emptyList()
    }

    private fun sendTcp(json: String): String {
        var socket: Socket? = null
        return try {
            socket = Socket(serverHost, serverPort)
            socket.soTimeout = 5000
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(json)
            writer.flush()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            reader.readLine() ?: ""
        } catch (_: Exception) {
            ""
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun stopClient() {
        EventManager.call(ClientShutdownEvent)
        SharedScopes.stop()
        saveAllConfigs()
        ClickGuiWebInterface.stop()
    }
}