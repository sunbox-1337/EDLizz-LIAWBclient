package net.ccbluex.liquidbounce.utils.render.shader

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.GL11
import java.util.ArrayList

object KawaseBlur {
    private val mc = Minecraft.getMinecraft()

    private val vert = """
        #version 120
        void main() {
            gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            gl_TexCoord[0] = gl_MultiTexCoord0;
        }
    """.trimIndent()

    private val fragDown = """
        #version 120
        uniform sampler2D inTexture;
        uniform vec2 halfpixel;
        uniform vec2 offset;
        void main() {
            vec2 tc = gl_TexCoord[0].st;
            vec4 sum = texture2D(inTexture, tc) * 4.0;
            sum += texture2D(inTexture, tc - halfpixel * offset);
            sum += texture2D(inTexture, tc + halfpixel * offset);
            sum += texture2D(inTexture, tc + vec2(-halfpixel.x, halfpixel.y) * offset);
            sum += texture2D(inTexture, tc - vec2(-halfpixel.x, halfpixel.y) * offset);
            gl_FragColor = sum / 8.0;
        }
    """.trimIndent()

    private val fragUp = """
        #version 120
        uniform sampler2D inTexture;
        uniform vec2 halfpixel;
        uniform vec2 offset;
        void main() {
            vec2 tc = gl_TexCoord[0].st;
            vec4 sum = texture2D(inTexture, tc + vec2(-1.0, -1.0) * halfpixel * offset) * 0.0625;
            sum += texture2D(inTexture, tc + vec2(-1.0, 1.0) * halfpixel * offset) * 0.125;
            sum += texture2D(inTexture, tc + vec2( 1.0, -1.0) * halfpixel * offset) * 0.125;
            sum += texture2D(inTexture, tc + vec2( 1.0, 1.0) * halfpixel * offset) * 0.0625;
            sum += texture2D(inTexture, tc + vec2(-1.0, 0.0) * halfpixel * offset) * 0.125;
            sum += texture2D(inTexture, tc + vec2( 0.0, -1.0) * halfpixel * offset) * 0.125;
            sum += texture2D(inTexture, tc + vec2( 1.0, 0.0) * halfpixel * offset) * 0.125;
            sum += texture2D(inTexture, tc + vec2( 0.0, 1.0) * halfpixel * offset) * 0.125;
            sum += texture2D(inTexture, tc) * 0.125;
            gl_FragColor = sum;
        }
    """.trimIndent()

    private val kawaseDown = ShaderUtil(vert, fragDown)
    private val kawaseUp = ShaderUtil(vert, fragUp)

    private val framebuffer = Framebuffer(1, 1, false)
    private val framebufferList = ArrayList<Framebuffer>()
    private var currentIterations = 0

    fun setupUniforms(offset: Float) {
        kawaseDown.setUniformf("offset", offset, offset)
        kawaseUp.setUniformf("offset", offset, offset)
    }

    private fun initFramebuffers(iterations: Float) {
        for (fb in framebufferList) {
            fb.deleteFramebuffer()
        }
        framebufferList.clear()
        framebufferList.add(framebuffer)
        var i = 1
        while (i <= iterations) {
            framebufferList.add(Framebuffer(mc.displayWidth, mc.displayHeight, false))
            i++
        }
    }

    fun renderBlur(iterations: Int, offset: Float) {
        if (currentIterations != iterations) {
            initFramebuffers(iterations.toFloat())
            currentIterations = iterations
        }

        // 保存当前模板测试状态
        val stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        val stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC)
        val stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF)
        val stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK)
        val stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL)
        val stencilPassDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL)
        val stencilPassDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS)

        // 第一步：downsample，这些临时FBO没有模板缓冲区，必须禁用模板测试
        GL11.glDisable(GL11.GL_STENCIL_TEST)
        renderFBO(framebufferList[1], mc.framebuffer.framebufferTexture, kawaseDown, offset)

        for (i in 1 until iterations) {
            renderFBO(framebufferList[i + 1], framebufferList[i].framebufferTexture, kawaseDown, offset)
        }

        // Upsample 也是在无模板FBO上进行
        for (i in iterations downTo 2) {
            renderFBO(framebufferList[i - 1], framebufferList[i].framebufferTexture, kawaseUp, offset)
        }

        // 最后一步：渲染回主FBO（主FBO有模板缓冲区，恢复先前的模板状态）
        if (stencilEnabled) {
            GL11.glEnable(GL11.GL_STENCIL_TEST)
            GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask)
            GL11.glStencilOp(stencilFail, stencilPassDepthFail, stencilPassDepthPass)
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST)
        }

        mc.framebuffer.bindFramebuffer(true)
        GlStateManager.bindTexture(framebufferList[1].framebufferTexture)
        kawaseUp.init()
        kawaseUp.setUniformf("offset", offset, offset)
        kawaseUp.setUniformf("halfpixel", 0.5f / mc.displayWidth, 0.5f / mc.displayHeight)
        kawaseUp.setUniformi("inTexture", 0)
        ShaderUtil.drawQuads()
        kawaseUp.unload()
    }

    /**
     * 区域模糊版本，通过裁剪实现只更新指定矩形区域。
     * 实际全屏模糊，但只有目标区域被写回主帧缓冲。
     */
    fun renderBlurRect(x: Float, y: Float, w: Float, h: Float, iterations: Int, offset: Float) {
        // 设置裁剪区域
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            x.toInt().coerceAtLeast(0),
            (mc.displayHeight - y - h).toInt().coerceAtLeast(0),
            w.toInt().coerceAtMost(mc.displayWidth),
            h.toInt().coerceAtMost(mc.displayHeight)
        )
        renderBlur(iterations, offset)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
    }

    private fun renderFBO(framebuffer: Framebuffer, framebufferTexture: Int, shader: ShaderUtil, offset: Float) {
        framebuffer.framebufferClear()
        framebuffer.bindFramebuffer(true)
        shader.init()
        GlStateManager.bindTexture(framebufferTexture)
        shader.setUniformf("offset", offset, offset)
        shader.setUniformi("inTexture", 0)
        shader.setUniformf("halfpixel", 0.5f / mc.displayWidth, 0.5f / mc.displayHeight)
        ShaderUtil.drawQuads()
        shader.unload()
        framebuffer.unbindFramebuffer()
    }
}