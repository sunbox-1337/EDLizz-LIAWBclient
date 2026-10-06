package net.ccbluex.liquidbounce.utils.render

import net.minecraft.client.Minecraft
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.EXTFramebufferObject
import org.lwjgl.opengl.GL11.*

object StencilUtil {
    private val mc = Minecraft.getMinecraft()

    fun initStencilToWrite() {
        mc.framebuffer.bindFramebuffer(false)
        checkSetupFBO(mc.framebuffer)
        glClear(GL_STENCIL_BUFFER_BIT)
        glEnable(GL_STENCIL_TEST)
        glStencilFunc(GL_ALWAYS, 1, 1)
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE)
        glColorMask(false, false, false, false)
    }

    fun readStencilBuffer(ref: Int) {
        glColorMask(true, true, true, true)
        glStencilFunc(GL_EQUAL, ref, 1)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
    }

    fun uninitStencilBuffer() {
        glDisable(GL_STENCIL_TEST)
    }

    private fun checkSetupFBO(framebuffer: Framebuffer) {
        if (framebuffer.depthBuffer > -1) {
            setupFBO(framebuffer)
            framebuffer.depthBuffer = -1
        }
    }

    private fun setupFBO(fbo: Framebuffer) {
        EXTFramebufferObject.glDeleteRenderbuffersEXT(fbo.depthBuffer)
        val stencilDepthBufferID = EXTFramebufferObject.glGenRenderbuffersEXT()
        EXTFramebufferObject.glBindRenderbufferEXT(36161, stencilDepthBufferID)
        EXTFramebufferObject.glRenderbufferStorageEXT(
            36161,
            34041,
            mc.displayWidth,
            mc.displayHeight
        )
        EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36128, 36161, stencilDepthBufferID)
        EXTFramebufferObject.glFramebufferRenderbufferEXT(36160, 36096, 36161, stencilDepthBufferID)
    }
}