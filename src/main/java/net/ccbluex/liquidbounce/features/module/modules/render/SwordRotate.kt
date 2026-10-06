package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.item.ItemSword
import org.lwjgl.opengl.GL11

object SwordRotate : Module("SwordRotate", Category.RENDER) {

    private val axis by choices("Axis", arrayOf("Horizontal", "Vertical"), "Horizontal")
    private val speed by float("Speed", 5f, 1f..20f)

    val onRender3D = handler<Render3DEvent> {
        val player = mc.thePlayer ?: return@handler
        val stack = player.heldItem ?: return@handler
        if (stack.item !is ItemSword) return@handler

        // 计算基于时间的旋转角度
        val angle = (System.currentTimeMillis() / 10f * speed) % 360f

        GL11.glPushMatrix()
        when (axis.lowercase()) {
            "horizontal" -> GL11.glRotatef(angle, 0f, 1f, 0f)
            "vertical" -> GL11.glRotatef(angle, 1f, 0f, 0f)
        }
        // 注意：只 push 不 pop，这样只影响手部渲染，后续 GUI 渲染会被自动重置
    }
}