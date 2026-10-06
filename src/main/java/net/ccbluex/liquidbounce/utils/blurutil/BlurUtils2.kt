package net.ccbluex.liquidbounce.utils.blurutil

import net.ccbluex.liquidbounce.utils.MinecraftInstance
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.shader.Framebuffer
import net.minecraft.client.shader.Shader
import net.minecraft.client.shader.ShaderGroup
import net.minecraft.client.shader.ShaderUniform
import net.minecraft.util.ResourceLocation

object BlurUtils2 : MinecraftInstance() {
    private var blurShader: ShaderGroup? = null
    private var buffer: Framebuffer? = null
    private var lastScale = 0
    private var lastScaleWidth = 0
    private var lastScaleHeight = 0
    private var initFailed = false
    private var cachedUniforms: Array<Array<ShaderUniform?>>? = null

    private fun getOrCreateShader(): ShaderGroup? {
        if (initFailed) return null
        if (blurShader == null) {
            try {
                blurShader = ShaderGroup(
                    mc.textureManager,
                    mc.resourceManager,
                    mc.framebuffer,
                    ResourceLocation("lizz/blur/post/BlurArea.json")
                )
            } catch (e: Exception) {
                initFailed = true
                return null
            }
        }
        return blurShader
    }

    private fun reinitShader(shader: ShaderGroup) {
        shader.createBindFramebuffers(mc.displayWidth, mc.displayHeight)
        buffer = Framebuffer(mc.displayWidth, mc.displayHeight, true)
        buffer?.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f)
        cachedUniforms = null
    }

    private fun getUniforms(shader: ShaderGroup): Array<Array<ShaderUniform?>> {
        val cached = cachedUniforms
        if (cached != null) return cached

        // 通过反射获取私有字段 listShaders，避免直接访问导致编译错误
        val listShadersField = ShaderGroup::class.java.getDeclaredField("listShaders")
        listShadersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val shaders = listShadersField.get(shader) as List<Shader>

        val arr = Array(shaders.size) { idx ->
            val manager = shaders[idx].shaderManager
            arrayOfNulls<ShaderUniform>(4).also {
                it[0] = manager.getShaderUniform("BlurXY")
                it[1] = manager.getShaderUniform("BlurCoord")
                it[2] = manager.getShaderUniform("Radius")
                it[3] = manager.getShaderUniform("ScaleFactor")
            }
        }
        cachedUniforms = arr
        return arr
    }

    @JvmOverloads
    fun draw(x: Float, y: Float, width: Float, height: Float, radius: Float = 10f) {
        try {
            drawInternal(x, y, width, height, radius)
        } catch (ignored: Exception) {
            try {
                mc.framebuffer.bindFramebuffer(true)
            } catch (ignored2: Exception) {
            }
        }
    }

    private fun drawInternal(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        if (width <= 0f || height <= 0f) return
        if (radius <= 0f) return

        val shader = getOrCreateShader() ?: return

        val sr = ScaledResolution(mc)
        val factor = sr.scaleFactor
        val factor2 = sr.scaledWidth
        val factor3 = sr.scaledHeight

        if (x + width < 0 || y + height < 0 || x > factor2 || y > factor3) return

        if (lastScale != factor || lastScaleWidth != factor2 || lastScaleHeight != factor3) {
            reinitShader(shader)
        }
        lastScale = factor
        lastScaleWidth = factor2
        lastScaleHeight = factor3

        val clampedRadius = radius.coerceIn(1f, 50f)

        for (uniforms in getUniforms(shader)) {
            uniforms[0]?.set(x, factor3 - y - height)
            uniforms[1]?.set(width, height)
            uniforms[2]?.set(clampedRadius)
            uniforms[3]?.set(factor.toFloat())
        }

        shader.loadShaderGroup(mc.timer.renderPartialTicks)
        mc.framebuffer.bindFramebuffer(true)
    }

    fun release() {
        try {
            blurShader?.deleteShaderGroup()
            buffer?.deleteFramebuffer()
        } catch (ignored: Exception) {
        }
        blurShader = null
        buffer = null
        cachedUniforms = null
        initFailed = false
        lastScale = 0
        lastScaleWidth = 0
        lastScaleHeight = 0
    }
}