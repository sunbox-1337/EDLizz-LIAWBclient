package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.JLayerUtils

object ModuleSounds : Module("ModuleSounds", Category.MISC) {

    // 是否播放开关提示音
    private val playToggleSound by boolean("PlayToggleSound", true)

    // 开启音效选择（文件名，不含扩展名）
    private val enableSound by choices(
        "EnableSound",
        arrayOf(
            "KDE5",
            "LiquidBounce+",
            "Loratadine",
            "Mac",
            "MagicUI",
            "MIUI",
            "Sigma5",
            "SoundsHarmony2",
            "Win11"
        ),
        "Sigma5"
    )

    // 关闭音效选择
    private val disableSound by choices(
        "DisableSound",
        arrayOf(
            "KDE5",
            "LiquidBounce+",
            "Loratadine",
            "Mac",
            "MagicUI",
            "MIUI",
            "Sigma5",
            "SoundsHarmony2",
            "Win11"
        ),
        "Sigma5"
    )

    /**
     * 播放开启模块时的提示音
     * 由 Module 基类调用
     */
    fun playEnableSound() {
        if (!playToggleSound) return
        val soundFile = "/assets/minecraft/${CLIENT_NAME.lowercase()}/sounds/Enable/$enableSound.mp3"
        try {
            JLayerUtils.playMP3(soundFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 播放关闭模块时的提示音
     * 由 Module 基类调用
     */
    fun playDisableSound() {
        if (!playToggleSound) return
        val soundFile = "/assets/minecraft/${CLIENT_NAME.lowercase()}/sounds/Disable/$disableSound.mp3"
        try {
            JLayerUtils.playMP3(soundFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}