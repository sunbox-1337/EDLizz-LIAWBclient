package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.KeyEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.render.music.Mp3AudioPlayer
import net.ccbluex.liquidbounce.utils.render.music.MusicLibrary
import net.ccbluex.liquidbounce.utils.render.music.MusicTrack
import org.lwjgl.input.Keyboard
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.File
import kotlin.concurrent.thread

/**
 * 音乐播放器。
 *
 * 逻辑都放在这个模块里；实际画面由 HUD 元素 MusicPlayer 绘制。
 * 音乐来源：外部文件夹（[MusicFolder]，默认 <游戏目录>/lizz/music） + 可选的 classpath 内置资源目录。
 */
object MusicPlayer : Module("MusicPlayer", Category.RENDER) {

    private val folderPath by text("MusicFolder", "")
    private val useResources by boolean("UseResources", true)
    private val volume by float("Volume", 1f, 0f..1f)

    // 快捷键（存 LWJGL 键值，0 = 未绑定）
    private val nextKey by int("NextKey", Keyboard.KEY_RIGHT, 0..255)
    private val prevKey by int("PrevKey", Keyboard.KEY_LEFT, 0..255)
    private val pauseKey by int("PauseKey", Keyboard.KEY_P, 0..255)

    // 打开外部音乐文件夹（设为 true 会弹出资源管理器窗口，然后自动复位）
    private val openFolder by boolean("OpenFolder", false)
    // 重新扫描音乐列表
    private val reloadList by boolean("Reload", false)

    @Volatile var tracks: List<MusicTrack> = emptyList()
        private set

    @Volatile var currentIndex: Int = -1
        private set

    @Volatile var currentCover: BufferedImage? = null
        private set

    val currentTrack: MusicTrack? get() = tracks.getOrNull(currentIndex)
    val isPlaying: Boolean get() = Mp3AudioPlayer.isPlaying
    val isPaused: Boolean get() = Mp3AudioPlayer.isPaused
    val position: Float get() = Mp3AudioPlayer.position
    val duration: Float get() = Mp3AudioPlayer.duration

    private fun resolvedFolder(): File {
        val configured = folderPath.trim()
        return if (configured.isNotEmpty()) File(configured) else MusicLibrary.defaultFolder(mc.mcDataDir)
    }

    /** 重新扫描音乐列表。 */
    fun reload(autoplay: Boolean = true) {
        val list = mutableListOf<MusicTrack>()
        list += MusicLibrary.scanFolder(resolvedFolder())
        if (useResources) list += MusicLibrary.scanResources()

        tracks = list

        if (list.isEmpty()) {
            currentIndex = -1
            currentCover = null
            Mp3AudioPlayer.stop()
            chat("§c音乐列表为空，请把音频文件放到: §7${resolvedFolder().absolutePath}")
            return
        }

        if (currentIndex !in list.indices) currentIndex = 0
        if (autoplay) playCurrent() else loadCover()
    }

    fun playCurrent() {
        val track = currentTrack ?: return
        chat("§7正在播放 §a${track.title} §7(${currentIndex + 1}/${tracks.size})")
        Mp3AudioPlayer.setVolume(volume)
        Mp3AudioPlayer.play { track.openStream() }
        loadCover()
    }

    private fun loadCover() {
        val track = currentTrack ?: run {
            currentCover = null
            return
        }
        currentCover = null
        thread(start = true, isDaemon = true, name = "MusicPlayer-Cover") {
            val image = try {
                track.readCover()
            } catch (_: Exception) {
                null
            }
            if (currentTrack === track) currentCover = image
        }
    }

    fun next() {
        if (tracks.isEmpty()) return
        currentIndex = (currentIndex + 1) % tracks.size
        playCurrent()
    }

    fun previous() {
        if (tracks.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) tracks.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun togglePause() {
        if (Mp3AudioPlayer.isActive) {
            Mp3AudioPlayer.togglePause()
        } else {
            playCurrent()
        }
    }

    fun openMusicFolder() {
        val folder = resolvedFolder()
        if (!folder.exists()) folder.mkdirs()
        try {
            Desktop.getDesktop().open(folder)
        } catch (_: Exception) {
            chat("§c无法打开文件夹: §7${folder.absolutePath}")
        }
    }

    override fun onEnable() {
        reload(autoplay = true)
    }

    override fun onDisable() {
        Mp3AudioPlayer.stop()
    }

    val keyHandler = handler<KeyEvent> { event ->
        if (!state) return@handler

        val key = event.key
        when {
            nextKey != Keyboard.KEY_NONE && key == nextKey -> next()
            prevKey != Keyboard.KEY_NONE && key == prevKey -> previous()
            pauseKey != Keyboard.KEY_NONE && key == pauseKey -> togglePause()
        }
    }

    val updateHandler = handler<UpdateEvent> {
        if (!state) return@handler

        Mp3AudioPlayer.setVolume(volume)

        if (openFolder) {
            openFolder = false
            openMusicFolder()
        }
        if (reloadList) {
            reloadList = false
            reload(autoplay = Mp3AudioPlayer.isActive.not())
        }
    }
}
