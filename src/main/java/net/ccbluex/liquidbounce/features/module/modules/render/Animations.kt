/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.MinecraftInstance.Companion
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.item.ItemSword
import net.minecraft.util.MathHelper
import org.lwjgl.opengl.GL11.glTranslated
import org.lwjgl.opengl.GL11.glTranslatef

object Animations : Module("Animations", Category.RENDER, gameDetecting = false) {

    // Default animation
    val defaultAnimation = OneSevenAnimation()

    private val animations = arrayOf(
        OneSevenAnimation(),
        OldPushdownAnimation(),
        NewPushdownAnimation(),
        OldAnimation(),
        HeliumAnimation(),
        ArgonAnimation(),
        CesiumAnimation(),
        SulfurAnimation(),
        ExhibitionAnimation(),
        SwingAnimation(),
        RotateAnimation()
    )

    private val animationMode by choices("Mode", animations.map { it.name }.toTypedArray(), "NewPushdown")
    val oddSwing by boolean("OddSwing", false)
    val swingSpeed by int("SwingSpeed", 15, 0..20)

    val handItemScale by float("ItemScale", 0f, -5f..5f)
    val handX by float("X", 0f, -5f..5f)
    val handY by float("Y", 0f, -5f..5f)
    val handPosX by float("PositionRotationX", 0f, -50f..50f)
    val handPosY by float("PositionRotationY", 0f, -50f..50f)
    val handPosZ by float("PositionRotationZ", 0f, -50f..50f)

    // 旋转动画专属设置（移除 private，使其可在同文件类中访问）
    val rotateAxis by choices("RotateAxis", arrayOf("Horizontal", "Vertical"), "Horizontal") {
        animationMode == "Rotate"
    }
    val rotateSpeed by float("RotateSpeed", 5f, 1f..20f) {
        animationMode == "Rotate"
    }

    fun getAnimation() = animations.firstOrNull { it.name == animationMode }
}

abstract class Animation(val name: String) : net.ccbluex.liquidbounce.utils.client.MinecraftInstance {
    abstract fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer)

    protected fun doBlockTransformations() {
        translate(-0.5f, 0.2f, 0f)
        rotate(30f, 0f, 1f, 0f)
        rotate(-80f, 1f, 0f, 0f)
        rotate(60f, 0f, 1f, 0f)
    }

    protected fun transformFirstPersonItem(equipProgress: Float, swingProgress: Float) {
        translate(0.56f, -0.52f, -0.71999997f)
        translate(0f, equipProgress * -0.6f, 0f)
        rotate(45f, 0f, 1f, 0f)
        val f = MathHelper.sin(swingProgress * swingProgress * 3.1415927f)
        val f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * 3.1415927f)
        rotate(f * -20f, 0f, 1f, 0f)
        rotate(f1 * -20f, 0f, 0f, 1f)
        rotate(f1 * -80f, 1f, 0f, 0f)
        scale(0.4f, 0.4f, 0.4f)
    }
}

// 原有动画类（保持不变）
class OneSevenAnimation : Animation("OneSeven") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
        translate(-0.5f, 0.2f, 0f)
    }
}

class OldAnimation : Animation("Old") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
    }
}

class OldPushdownAnimation : Animation("OldPushdown") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        translate(0.56, -0.52, -0.5)
        translate(0.0, -f.toDouble() * 0.3, 0.0)
        rotate(45.5f, 0f, 1f, 0f)
        val var3 = MathHelper.sin(0f)
        val var4 = MathHelper.sin(0f)
        rotate((var3 * -20f), 0f, 1f, 0f)
        rotate((var4 * -20f), 0f, 0f, 1f)
        rotate((var4 * -80f), 1f, 0f, 0f)
        scale(0.32, 0.32, 0.32)
        val var15 = MathHelper.sin((MathHelper.sqrt_float(f1) * 3.1415927f))
        rotate((-var15 * 125 / 1.75f), 3.95f, 0.35f, 8f)
        rotate(-var15 * 35, 0f, (var15 / 100f), -10f)
        translate(-1.0, 0.6, -0.0)
        rotate(30f, 0f, 1f, 0f)
        rotate(-80f, 1f, 0f, 0f)
        rotate(60f, 0f, 1f, 0f)
        glTranslated(1.05, 0.35, 0.4)
        glTranslatef(-1f, 0f, 0f)
    }
}

class NewPushdownAnimation : Animation("NewPushdown") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val x = Animations.handPosX - 0.08
        val y = Animations.handPosY + 0.12
        val z = Animations.handPosZ.toDouble()
        translate(x, y, z)
        val var9 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        translate(0.0, 0.0, 0.0)
        transformFirstPersonItem(f / 1.4f, 0.0f)
        rotate(-var9 * 65.0f / 2.0f, var9 / 2.0f, 1.0f, 4.0f)
        rotate(-var9 * 60.0f, 1.0f, var9 / 3.0f, -0.0f)
        doBlockTransformations()
        scale(1.0, 1.0, 1.0)
    }
}

class HeliumAnimation : Animation("Helium") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, 0.0f)
        val c0 = MathHelper.sin(f1 * f * 3.1415927f)
        val c1 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        rotate(-c1 * 55.0f, 30.0f, c0 / 5.0f, 0.0f)
        doBlockTransformations()
    }
}

class ArgonAnimation : Animation("Argon") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 2.5f, f1)
        val c2 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        val c3 = MathHelper.cos(MathHelper.sqrt_float(f) * 3.1415927f)
        rotate(c3 * 50.0f / 10.0f, -c2, -0.0f, 100.0f)
        rotate(c2 * 50.0f, 200.0f, -c2 / 2.0f, -0.0f)
        translate(0.0, 0.3, 0.0)
        doBlockTransformations()
    }
}

class CesiumAnimation : Animation("Cesium") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val c4 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        rotate(-c4 * 10.0f / 20.0f, c4 / 2.0f, 0.0f, 4.0f)
        rotate(-c4 * 30.0f, 0.0f, c4 / 3.0f, 0.0f)
        rotate(-c4 * 10.0f, 1.0f, c4 / 10.0f, 0.0f)
        translate(0.0, 0.2, 0.0)
        doBlockTransformations()
    }
}

class SulfurAnimation : Animation("Sulfur") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val c5 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        val c6 = MathHelper.cos(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        rotate(-c5 * 30.0f, c5 / 10.0f, c6 / 10.0f, 0.0f)
        translate(c5 / 1.5, 0.2, 0.0)
        doBlockTransformations()
    }
}

class ExhibitionAnimation : Animation("Exhibition") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var151 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        glTranslated(-0.03, (var151 * 0.062f).toDouble(), 0.0)
        glTranslated(0.025, 0.09615, 0.0)
        transformFirstPersonItem(f / 3f, 0.0f)
        rotate(-var151 * 9f, -var151 / 20f, -var151 / 20f, 1f)
        rotate(-var151 * 55f, 1.2f, var151 / 4f, 0.36f)
        if (mc.thePlayer.isSneaking) {
            translate(-0.05, -0.05, 0.0)
        }
        doBlockTransformations()
    }
}

class SwingAnimation : Animation("Swing") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 2.0f, f1)
        translate(-0.5f, 0.4f, 0.0f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-80.0f, 1.0f, 0.0f, 0.0f)
        rotate(60.0f, 0.0f, 1.0f, 0.0f)
    }
}

// 新增旋转动画（已修正访问权限）
class RotateAnimation : Animation("Rotate") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        // 基础变换（与 OneSeven 相同）
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
        translate(-0.5f, 0.2f, 0f)

        val player = mc.thePlayer ?: return
        val stack = player.heldItem
        if (stack != null && stack.item is ItemSword) {
            val angle = (System.currentTimeMillis() / 10f * Animations.rotateSpeed) % 360f
            when (Animations.rotateAxis) {
                "Horizontal" -> rotate(angle, 0f, 1f, 0f)   // 水平绕Y轴旋转
                "Vertical" -> rotate(angle, 1f, 0f, 0f)     // 竖直绕X轴旋转
            }
        }
    }
}