package net.ccbluex.liquidbounce.chat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * IRC 客户端 —— 直接对话 irc服务器/AuthServer.py，和 LiquidChat 无关。
 *
 * 服务端是「一次性请求 / 响应」协议（recv 一次 → 回一行 JSON → 关连接），
 * 所以这里发送和接收各开一条连接，接收靠轮询（服务端 action = chat_poll）。
 *
 * 地址 / 端口沿用主界面 auth 那套（AuthGuiScreen.sendTcp / LiquidBounce.serverPort）。
 *
 * 线程模型：所有 socket I/O 都在后台线程，渲染线程只读 [messages]，
 * 否则 3 秒超时会直接把游戏卡住。
 */
object IrcClient {
    private const val HOST = "194.147.16.88"
    private const val PORT = 58755

    private const val TIMEOUT_MS = 3000
    private const val MAX_MESSAGES = 200

    /** 登录态（内存态，凭据沿用主界面 auth 的用户名/密码） */
    @Volatile
    var username: String? = null
    @Volatile
    var password: String? = null

    val isLoggedIn: Boolean
        get() = !username.isNullOrBlank() && !password.isNullOrBlank()

    /** 最近的消息（时间正序）。用 CopyOnWrite 是为了渲染线程边遍历、后台线程边追加不炸 */
    val messages = CopyOnWriteArrayList<ChatMessage>()

    /** 上次拉取到的服务端时间戳，作为下次 poll 的 since，实现增量拉取 */
    @Volatile
    private var lastTimestamp = 0.0
    @Volatile
    private var lastPollAt = 0L
    @Volatile
    private var polling = false

    /** 输入框草稿 / 焦点 / 包围盒 —— UI 状态放在这里，是为了让 ClickGui 和 style 都能拿到 */
    @Volatile
    var draft = ""

    @Volatile
    var inputFocused = false

    @Volatile
    var inputBounds: FloatArray? = null

    /** 消息列表的滚轮偏移：0 = 贴底（显示最新），越大越往上翻旧消息 */
    @Volatile
    var scroll = 0F

    /** 面板提示用的一句话状态 */
    @Volatile
    var lastError: String? = null
        private set

    data class ChatMessage(
        val user: String,
        val text: String,
        val target: String,
        val time: Double
    )

    /**
     * 发一行 JSON、读一行回复。和 AuthGuiScreen.sendTcp 同一套做法。
     * 必须只在后台线程调用。
     */
    private fun request(json: String): JsonObject? {
        return try {
            val socket = Socket(HOST, PORT)
            socket.soTimeout = TIMEOUT_MS
            try {
                OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8).apply {
                    write(json + "\n")
                    flush()
                }

                val line = BufferedReader(
                    InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                ).readLine()

                if (line.isNullOrBlank()) null else JsonParser().parse(line) as? JsonObject
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            lastError = e.message
            null
        }
    }

    /**
     * 用 IRC 面板里的账号密码登录（和主界面 auth 同一个 login 动作）。
     * 成功后凭据留在 [username] / [password]，面板直接切到已登录态。
     *
     * 注意：[onResult] 是在后台线程回调的，要改 UI 状态请自己切回主线程。
     */
    fun login(user: String, pass: String, onResult: ((Boolean) -> Unit)? = null) {
        if (user.isBlank() || pass.isBlank()) {
            lastError = "Empty username or password"
            onResult?.invoke(false)
            return
        }

        val payload = JsonObject().apply {
            addProperty("action", "login")
            addProperty("username", user)
            addProperty("password", pass)
        }.toString()

        Thread({
            val response = request(payload)
            val success = response?.get("success")?.asBoolean == true

            if (success) {
                username = user
                password = pass
                lastError = null
                // 换账号时清掉上一个账号的消息，避免串台
                messages.clear()
                lastTimestamp = 0.0
            } else {
                lastError = response?.get("message")?.asString ?: "Network error"
            }

            onResult?.invoke(success)
        }, "IRC-Login").apply { isDaemon = true }.start()
    }

    /** 发送一条消息（target 为空 = 全局）。不阻塞渲染线程。 */
    fun send(text: String, target: String = "") {
        val user = username
        val pass = password
        if (user.isNullOrBlank() || pass.isNullOrBlank() || text.isBlank()) return

        val payload = JsonObject().apply {
            addProperty("action", "chat_send")
            addProperty("username", user)
            addProperty("password", pass)
            addProperty("text", text)
            addProperty("target", target)
        }.toString()

        Thread({
            val response = request(payload)
            if (response == null || response.get("success")?.asBoolean != true) {
                lastError = response?.get("message")?.asString ?: lastError
            } else {
                lastError = null
            }
        }, "IRC-Send").apply { isDaemon = true }.start()
    }

    /**
     * 拉取新消息。由渲染循环每帧调用，内部自己限频（默认 2 秒一次），
     * 免得每帧都开一条 TCP 穿 frp。
     */
    fun poll(intervalMs: Long = 2000L) {
        if (!isLoggedIn) return

        val now = System.currentTimeMillis()
        if (now - lastPollAt < intervalMs || polling) return
        lastPollAt = now
        polling = true

        val user = username ?: return
        val pass = password ?: return

        val payload = JsonObject().apply {
            addProperty("action", "chat_poll")
            addProperty("username", user)
            addProperty("password", pass)
            addProperty("since", lastTimestamp)
        }.toString()

        Thread({
            try {
                val response = request(payload)

                if (response?.get("success")?.asBoolean == true) {
                    (response.get("messages") as? JsonArray)?.forEach { element ->
                        val obj = element.asJsonObject
                        messages += ChatMessage(
                            user = obj.get("user")?.asString ?: "",
                            text = obj.get("text")?.asString ?: "",
                            target = obj.get("target")?.asString ?: "",
                            time = obj.get("time")?.asDouble ?: 0.0
                        )
                    }

                    while (messages.size > MAX_MESSAGES) {
                        messages.removeAt(0)
                    }

                    response.get("now")?.asDouble?.let { lastTimestamp = it }
                    lastError = null
                } else if (response != null) {
                    lastError = response.get("message")?.asString
                }
            } finally {
                polling = false
            }
        }, "IRC-Poll").apply { isDaemon = true }.start()
    }

    /** 在线玩家（服务端 get_online_ids 返回）。渲染线程只读它 */
    val onlinePlayers = CopyOnWriteArrayList<OnlinePlayer>()

    data class OnlinePlayer(
        val clientId: String,
        val name: String,
        val uuid: String
    )

    @Volatile
    private var lastOnlineAt = 0L

    /**
     * 拉在线玩家列表。和消息一样由渲染循环驱动、内部限频。
     * 服务端这个 action 不需要鉴权，所以未登录时也能看到在线列表。
     */
    fun pollOnline(intervalMs: Long = 5000L) {
        val now = System.currentTimeMillis()
        if (now - lastOnlineAt < intervalMs) return
        lastOnlineAt = now

        val payload = JsonObject().apply {
            addProperty("action", "get_online_ids")
        }.toString()

        Thread({
            val response = request(payload) ?: return@Thread
            if (response.get("success")?.asBoolean != true) return@Thread

            val list = mutableListOf<OnlinePlayer>()
            (response.get("online_clients") as? JsonArray)?.forEach { element ->
                val obj = element.asJsonObject
                list += OnlinePlayer(
                    clientId = obj.get("client_id")?.asString ?: "",
                    name = obj.get("player_name")?.asString ?: "",
                    uuid = obj.get("uuid")?.asString ?: ""
                )
            }

            onlinePlayers.clear()
            onlinePlayers.addAll(list)
        }, "IRC-Online").apply { isDaemon = true }.start()
    }

    /** 登出 / 切换账号时清干净，避免上一个账号的消息串台 */
    fun reset() {
        username = null
        password = null
        lastTimestamp = 0.0
        lastPollAt = 0L
        lastError = null
        messages.clear()
    }
}
