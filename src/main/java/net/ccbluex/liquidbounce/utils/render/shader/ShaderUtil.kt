package net.ccbluex.liquidbounce.utils.render.shader

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GLContext

class ShaderUtil(private val vertexSource: String, private val fragmentSource: String) {
    private var program = -1
    private val uniforms = mutableMapOf<String, Int>()

    init {
        initShader()
    }

    private fun initShader() {
        if (!GLContext.getCapabilities().OpenGL20) return
        val vert = compileShader(GL_VERTEX_SHADER, vertexSource)
        val frag = compileShader(GL_FRAGMENT_SHADER, fragmentSource)
        if (vert == 0 || frag == 0) return
        program = glCreateProgram()
        glAttachShader(program, vert)
        glAttachShader(program, frag)
        glLinkProgram(program)
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            program = -1
        }
        glDeleteShader(vert)
        glDeleteShader(frag)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun init() {
        if (program != -1) glUseProgram(program)
    }

    fun unload() {
        glUseProgram(0)
    }

    fun setUniformf(name: String, vararg values: Float) {
        if (program == -1) return
        val loc = uniforms.getOrPut(name) { glGetUniformLocation(program, name) }
        when (values.size) {
            1 -> glUniform1f(loc, values[0])
            2 -> glUniform2f(loc, values[0], values[1])
            3 -> glUniform3f(loc, values[0], values[1], values[2])
            4 -> glUniform4f(loc, values[0], values[1], values[2], values[3])
        }
    }

    fun setUniformi(name: String, value: Int) {
        if (program == -1) return
        val loc = uniforms.getOrPut(name) { glGetUniformLocation(program, name) }
        glUniform1i(loc, value)
    }

    companion object {
        fun drawQuads() {
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 1f); glVertex2f(-1f, -1f)
            glTexCoord2f(0f, 0f); glVertex2f(-1f, 1f)
            glTexCoord2f(1f, 0f); glVertex2f(1f, 1f)
            glTexCoord2f(1f, 1f); glVertex2f(1f, -1f)
            glEnd()
        }
    }
}