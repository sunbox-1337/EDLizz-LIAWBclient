/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.utils.web

import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.montoyo.mcef.MCEF
import net.montoyo.mcef.api.API
import net.montoyo.mcef.api.IBrowser
import net.montoyo.mcef.api.MCEFApi

/**
 * MCEF（Chromium 内嵌浏览器）薄封装。
 *
 * 注意：被打进 libs 的 `mcef-1.11-patched.jar` 的 mcmod.info 标的是 1.12.2，Foarge 不会把它当 mod 加载，
 * 所以它需要“手动初始化”（见 FDP 的 NextGenBrowserRuntime 做法）。在初始化完成前 [available] 为 false，
 * 这里所有调用都安全降级（返回 null / 记日志），绝不抛异常把游戏搞崩。
 *
 * 渲染内核（CEF/OSR）本身跟 MC 版本无关——它产出的是一张 GL 纹理，唯一耦合 MC 的只有 MCEF 自带的 demo，
 * 而 demo 由 `MixinMcefExampleMod` 关掉了。
 */
object McefBrowser {
    /** MCEF 是否已加载可用。 */
    val available: Boolean
        get() = runCatching { MCEFApi.isMCEFLoaded() }.getOrDefault(false)

    fun api(): API? = runCatching { MCEFApi.getAPI() }.getOrNull()

    fun createBrowser(url: String, transparent: Boolean = true): IBrowser? {
        if (!available) {
            LOGGER.warn("[WebClickGui] MCEF 未就绪，内嵌浏览器不可用（需要 mcef jar + 手动初始化）")
            return null
        }
        return runCatching { api()?.createBrowser(url, transparent) }
            .onFailure { LOGGER.error("[WebClickGui] createBrowser 失败", it) }
            .getOrNull()
    }

    fun shutdown() {
        runCatching { MCEF.onMinecraftShutdown() }
    }
}
