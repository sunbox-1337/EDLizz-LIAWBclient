/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.minecraft.client.Minecraft
import java.awt.Color

object Playtime : Module("Playtime", Category.RENDER) {
    private val xOffset by int("XOffset", 0, -1000..1000)
    private val yOffset by int("YOffset", 0, -1000..1000)

    private var playTimeMillis = 0L
    private var lastUpdateTime = 0L
    private var isCounting = false

    override fun onEnable() {
        playTimeMillis = 0L
        lastUpdateTime = System.currentTimeMillis()
        isCounting = false
    }

    val onRender2D = handler<Render2DEvent> { event ->
        val mc = Minecraft.getMinecraft()

        // 检查是否在服务器中（使用类似Watermark的判定方法）
        val isInServer = mc.theWorld != null && mc.theWorld.isRemote

        // 更新计时状态
        if (isInServer && !isCounting) {
            // 进入服务器，开始计时
            isCounting = true
            lastUpdateTime = System.currentTimeMillis()
            playTimeMillis = 0L
        } else if (!isInServer && isCounting) {
            // 退出服务器，暂停计时
            isCounting = false
        }

        // 更新游玩时间
        if (isCounting) {
            val currentTime = System.currentTimeMillis()
            playTimeMillis += currentTime - lastUpdateTime
            lastUpdateTime = currentTime
        }

        // 计算小时、分钟、秒
        val totalSeconds = playTimeMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        // 格式化文本
        val text = "${hours}h ${minutes}m ${seconds}s"

        // 使用可自定义的 X 和 Y 坐标
        val x = xOffset.toFloat()
        val y = yOffset.toFloat()

        // 绘制文本（无背景）
        Fonts.fontGoogleSans40.drawString(
            text,
            x,
            y,
            Color.WHITE.rgb,
            false // 无阴影
        )
    }
}