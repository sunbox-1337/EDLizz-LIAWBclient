/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.script.remapper

import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_CLOUD
import net.ccbluex.liquidbounce.file.FileManager.dir
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.io.Downloader
import net.ccbluex.liquidbounce.utils.io.isEmpty
import net.ccbluex.liquidbounce.utils.io.sha256
import java.io.File

/**
 * A srg remapper
 *
 * @author CCBlueX
 */
object Remapper {

    private const val SRG_NAME = "stable_22"
    private val srgFile = File(dir, "mcp-$SRG_NAME.srg")

    var mappingsLoaded = false
        private set

    private val fields = hashMapOf<String, HashMap<String, String>>()
    private val methods = hashMapOf<String, HashMap<String, String>>()

    /**
     * Load srg
     *
     * 完全离线：绝不联网下载（网络一卡会拖死整个启动加载）。
     * 只用本地已有的 srg 文件；没有就直接跳过（脚本 remap 不可用，但游戏照常跑）。
     */
    fun loadSrg() {
        if (mappingsLoaded) return

        synchronized(this) {
            if (mappingsLoaded) return

            mappingsLoaded = false

            if (!srgFile.exists() || srgFile.isEmpty) {
                LOGGER.error("[Remapper] Local $SRG_NAME missing, skipping download entirely (offline mode). Scripts may not work.")
                return
            }

            // Load srg
            parseSrg()

            mappingsLoaded = true

            LOGGER.info("[Remapper] Successfully loaded SRG mappings from local file.")
        }
    }

    private fun hashMatches(srgFile: File, sha256File: File): Boolean {
        if (!sha256File.exists()) {
            LOGGER.warn("[Remapper] No sha256 file found.")
            return false
        }

        // Generate SHA-256 hash of file content
        val hash = srgFile.sha256()

        // sha265sum mcp-stable_22.srg
        // -> a8486671a5e85153773eaac313f8babd1913b41524b45e92d42e6cf019e658eb  mcp-stable_22.srg
        val sha256 = sha256File.readText().substringBefore(' ')

        return sha256 == hash
    }

    private fun parseSrg() {
        srgFile.forEachLine {
            val args = it.split(' ')

            when {
                it.startsWith("FD:") -> {
                    val name = args[1]
                    val srg = args[2]

                    val className = name.substringBeforeLast('/').replace('/', '.')
                    val fieldName = name.substringAfterLast('/')
                    val fieldSrg = srg.substringAfterLast('/')

                    fields.getOrPut(className, ::HashMap)[fieldSrg] = fieldName
                }

                it.startsWith("MD:") -> {
                    val name = args[1]
                    val desc = args[2]
                    val srg = args[3]

                    val className = name.substringBeforeLast('/').replace('/', '.')
                    val methodName = name.substringAfterLast('/')
                    val methodSrg = srg.substringAfterLast('/')

                    methods.getOrPut(className, ::HashMap)[methodSrg + desc] = methodName
                }
            }
        }
    }

    /**
     * Remap field
     */
    fun remapField(clazz : Class<*>, name : String) =
        fields[clazz.name]?.get(name) ?: name

    /**
     * Remap method
     */
    fun remapMethod(clazz : Class<*>, name : String, desc : String) =
        methods[clazz.name]?.get(name + desc) ?: name
}