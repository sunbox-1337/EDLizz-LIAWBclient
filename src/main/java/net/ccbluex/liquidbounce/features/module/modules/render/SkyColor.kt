package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import java.awt.Color

object SkyColor : Module("SkyColor", Category.RENDER) {

    private val skyColor by color("Sky", Color(135, 206, 235))
    private val cloudColor by color("Clouds", Color.WHITE)
    private val fogColor by color("Fog", Color(135, 206, 235))

    override fun onDisable() {
        // 关闭后世界自动恢复原生颜色，无需手动重置
    }

    val onRender = handler<Render3DEvent> {
        val world = mc.theWorld ?: return@handler

        try {
            // 尝试 MCP 名称
            var skyField = world.javaClass.getDeclaredField("field_72985_G")
            skyField.isAccessible = true
            skyField.setInt(world, skyColor.rgb)

            var cloudField = world.javaClass.getDeclaredField("field_72987_H")
            cloudField.isAccessible = true
            cloudField.setInt(world, cloudColor.rgb)

            var fogField = world.javaClass.getDeclaredField("field_72986_F")
            fogField.isAccessible = true
            fogField.setInt(world, fogColor.rgb)

        } catch (e: NoSuchFieldException) {
            // SRG 名称备用
            try {
                world.javaClass.getDeclaredField("field_72985_G").apply {
                    isAccessible = true
                    setInt(world, skyColor.rgb)
                }
                world.javaClass.getDeclaredField("field_72987_H").apply {
                    isAccessible = true
                    setInt(world, cloudColor.rgb)
                }
                world.javaClass.getDeclaredField("field_72986_F").apply {
                    isAccessible = true
                    setInt(world, fogColor.rgb)
                }
            } catch (_: Exception) {}
        }
    }
}