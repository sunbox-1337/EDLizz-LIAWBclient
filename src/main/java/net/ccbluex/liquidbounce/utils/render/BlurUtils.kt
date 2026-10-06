package net.ccbluex.liquidbounce.utils.render

import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_COMBINE
import org.lwjgl.opengl.GL13.GL_COMBINE_ALPHA
import org.lwjgl.opengl.GL13.GL_COMBINE_RGB
import org.lwjgl.opengl.GL13.GL_PRIMARY_COLOR
import org.lwjgl.opengl.GL13.GL_SOURCE0_ALPHA
import org.lwjgl.opengl.GL13.GL_SOURCE0_RGB
import org.lwjgl.opengl.GL13.GL_SOURCE1_RGB
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GLContext
import java.awt.Color
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

object BlurUtils {
    private val mc = Minecraft.getMinecraft()

    /** 单次模糊允许的最大采样数（实际 quad 数 = 1 + 方向数 × 层数）。 */
    private const val MAX_BLUR_TAPS = 128

    /**
     * 绘制模糊。
     * @param radius 圆角半径（屏幕像素）。> 0 时使用模板缓冲把模糊裁切成圆角矩形，<= 0 时保持原来的矩形裁切。
     * @param rects 可选：多个矩形（屏幕像素、原点左上）。给了就把模糊裁切成这些矩形的并集，
     *              用于"整块模糊但只保留实际背景区域"（例如 arraylist 每行长度不一的锯齿边缘）。
     */
    @JvmOverloads
    fun drawOffsetBlur(
        x: Float, y: Float, w: Float, h: Float,
        samples: Int, strength: Float, radius: Float = 0f,
        rects: List<FloatArray>? = null,
        liquidGlass: Boolean = true
    ) {
        maskRects = rects
        try {
            // 模糊：仅在模糊总开关开启时执行。
            if (BlurSettings.enabled) {
                when (BlurSettings.mode.lowercase()) {
                    "shader" -> drawShaderBlur(x, y, w, h, samples, strength, radius)
                    "gaussian" -> drawGaussianBlur(x, y, w, h, samples, strength, radius)
                    "kawase" -> drawKawaseBlur(x, y, w, h, radius)
                    "naven" -> drawNavenBlur(x, y, w, h, radius)
                    "radial" -> drawRadialBlur(x, y, w, h, radius)
                    else -> drawOffsetBlurInternal(x, y, w, h, samples, strength, radius)
                }
            }

            // 液态玻璃：独立开关，复用上面同一个 x/y/w/h/radius（定位与圆角都由组件经此处传入）。
            if (BlurSettings.liquidGlass && liquidGlass) {
                LiquidGlassUtils.draw(x, y, w, h, radius)
            }
        } finally {
            maskRects = null
        }
    }

    /** 本次模糊的多矩形遮罩；为空时退回单矩形圆角遮罩。 */
    private var maskRects: List<FloatArray>? = null

    // ========== Offset 模糊（环形方向采样 + 高斯权重） ==========
    private fun drawOffsetBlurInternal(x: Float, y: Float, w: Float, h: Float, samples: Int, strength: Float, radius: Float) {
        val spread = (if (BlurSettings.mode == "Offset") BlurSettings.offsetStrength else strength).coerceAtLeast(0.5f)
        drawDirectionalGaussianBlur(x, y, w, h, spread, BlurSettings.offsetDirections, BlurSettings.offsetLayers, radius)
    }

    /**
     * 环形方向高斯模糊：从中心出发，向 [directions] 个方向、每个方向 [layers] 层由内向外扩散。
     * 每层权重按到中心的距离取高斯值（中心权重高、四周低），
     * 再用「累积权重 over 合成」（中心优先、alpha = w_k / Σw）合成，得到真正的加权平均。
     * 方向越多、层步距越小，越不容易出现撕裂。
     */
    private fun drawDirectionalGaussianBlur(x: Float, y: Float, w: Float, h: Float, spread: Float, directions: Int, layers: Int, radius: Float, opacity: Float = 1f) {
        val px = x; val py = y
        val pw = w; val ph = h
        val scissorX = px.toInt().coerceAtLeast(0)
        val scissorY = (mc.displayHeight - py - ph).toInt().coerceAtLeast(0)
        val scissorW = pw.toInt().coerceAtMost(mc.displayWidth - scissorX)
        val scissorH = ph.toInt().coerceAtMost(mc.displayHeight - scissorY)

        val dirs = directions.coerceIn(4, 64)
        // 限制总采样数（实际是 1 + dirs*lyr 个 quad），避免极端设置（如 64×128）
        // 或方向数很大的配置把填充率吃光
        val lyr = layers.coerceIn(4, 128).coerceAtMost((MAX_BLUR_TAPS / dirs).coerceAtLeast(4))

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity()
        glDisable(GL_DEPTH_TEST); glDisable(GL_LIGHTING); glEnable(GL_TEXTURE_2D)

        glReadBuffer(GL_BACK)
        val tex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, px.toInt(), (mc.displayHeight - py - ph).toInt(), pw.toInt(), ph.toInt(), 0)

        glEnable(GL_SCISSOR_TEST)
        glScissor(scissorX, scissorY, scissorW, scissorH)
        setupStencilMask(px, py, pw, ph, radius)

        val sigma = spread / 2f
        val step = spread / lyr
        val offsets = ArrayList<Pair<Float, Float>>()
        val weights = ArrayList<Float>()
        offsets.add(0f to 0f); weights.add(1f)
        for (j in 1..lyr) {
            val rr = j * step
            val wt = exp(-(rr * rr) / (2.0 * sigma * sigma)).toFloat()
            for (i in 0 until dirs) {
                val th = 2.0 * Math.PI * i / dirs
                offsets.add((cos(th) * rr).toFloat() to (sin(th) * rr).toFloat())
                weights.add(wt)
            }
        }
        drawWeightedTaps(px, py, pw, ph, offsets, weights, 1f)

        // 不透明度：把原画面按 (1 - opacity) 叠回去，得到 opacity * 模糊 + (1 - opacity) * 原图
        if (opacity < 1f) {
            glBindTexture(GL_TEXTURE_2D, tex)
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glColor4f(1f, 1f, 1f, (1f - opacity).coerceIn(0f, 1f))
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 1f); glVertex2f(px, py)
            glTexCoord2f(0f, 0f); glVertex2f(px, py + ph)
            glTexCoord2f(1f, 0f); glVertex2f(px + pw, py + ph)
            glTexCoord2f(1f, 1f); glVertex2f(px + pw, py)
            glEnd()
            glColor4f(1f, 1f, 1f, 1f)
        }

        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_STENCIL_TEST)
        glDeleteTextures(tex)
        glMatrixMode(GL_PROJECTION); glPopMatrix()
        glMatrixMode(GL_MODELVIEW); glPopMatrix()
        glPopAttrib()
    }

    private var sepFbo: Framebuffer? = null

    private fun ensureSepFbo(): Framebuffer {
        val w = mc.displayWidth; val h = mc.displayHeight
        val cur = sepFbo
        if (cur != null && cur.framebufferWidth == w && cur.framebufferHeight == h) return cur
        cur?.deleteFramebuffer()
        val fb = Framebuffer(w, h, false)
        sepFbo = fb
        return fb
    }

    /**
     * 可分离高斯模糊：先做一次水平方向的一维高斯加权平均（渲染进 FBO），
     * 再做一次垂直方向的一维高斯加权平均（合成到屏幕）。
     * 这样得到的模糊是各向同性的圆形高斯（不会出现采样网格造成的方块/向右拖影），
     * 且采样数只有 2N 而不是 N²。
     * 每次加权平均都用「累积权重 over 合成」：第 k 笔 alpha = w_k / (w_1+…+w_k)，中心优先以 alpha=1 打底。
     */
    private fun drawSeparableGaussianBlur(x: Float, y: Float, w: Float, h: Float, sigmaPx: Float, radius: Float) {
        val px = x.toInt(); val py = y.toInt()
        val pw = w.toInt(); val ph = h.toInt()
        if (pw <= 0 || ph <= 0) return

        val scissorX = px.coerceAtLeast(0)
        val scissorY = (mc.displayHeight - py - ph).coerceAtLeast(0)
        val scissorW = pw.coerceAtMost(mc.displayWidth - scissorX)
        val scissorH = ph.coerceAtMost(mc.displayHeight - scissorY)

        val s = sigmaPx.coerceAtLeast(0.6f)
        val r = (s * 2.5f).toInt().coerceIn(1, 64)

        // 一维高斯权重 + 绘制顺序（中心优先，再向两侧交替展开）
        val weights = FloatArray(2 * r + 1) { i -> val d = (i - r).toFloat(); exp(-(d * d) / (2.0 * s * s)).toFloat() }
        val order = IntArray(2 * r + 1)
        order[0] = r
        var oi = 1
        for (d in 1..r) {
            order[oi++] = r + d
            order[oi++] = r - d
        }

        val fbo = ensureSepFbo()
        val uMax = pw.toFloat() / mc.displayWidth
        val vMax = ph.toFloat() / mc.displayHeight

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glDisable(GL_DEPTH_TEST); glDisable(GL_LIGHTING); glEnable(GL_TEXTURE_2D)

        // 捕获区域
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity()
        glReadBuffer(GL_BACK)
        val tex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, px, mc.displayHeight - py - ph, pw, ph, 0)

        // ---- 水平 pass -> FBO ----
        glMatrixMode(GL_PROJECTION); glLoadIdentity()
        glOrtho(0.0, pw.toDouble(), ph.toDouble(), 0.0, -1.0, 1.0)
        glViewport(0, 0, pw, ph)
        glDisable(GL_SCISSOR_TEST)
        fbo.bindFramebuffer(false)
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glBindTexture(GL_TEXTURE_2D, tex)
        var cum = 0f
        for (idx in order) {
            cum += weights[idx]
            val a = (weights[idx] / cum).coerceIn(0f, 1f)
            val dx = (idx - r).toFloat()
            glColor4f(1f, 1f, 1f, a)
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 1f); glVertex2f(dx, 0f)
            glTexCoord2f(0f, 0f); glVertex2f(dx, ph.toFloat())
            glTexCoord2f(1f, 0f); glVertex2f(dx + pw, ph.toFloat())
            glTexCoord2f(1f, 1f); glVertex2f(dx + pw, 0f)
            glEnd()
        }

        // ---- 垂直 pass -> 屏幕 ----
        mc.framebuffer.bindFramebuffer(false)
        glMatrixMode(GL_PROJECTION); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glViewport(0, 0, mc.displayWidth, mc.displayHeight)
        glEnable(GL_SCISSOR_TEST); glScissor(scissorX, scissorY, scissorW, scissorH)
        setupStencilMask(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), radius)
        glBindTexture(GL_TEXTURE_2D, fbo.framebufferTexture)
        cum = 0f
        for (idx in order) {
            cum += weights[idx]
            val a = (weights[idx] / cum).coerceIn(0f, 1f)
            val dy = (idx - r).toFloat()
            glColor4f(1f, 1f, 1f, a)
            glBegin(GL_QUADS)
            glTexCoord2f(0f, vMax); glVertex2f(px.toFloat(), py + dy)
            glTexCoord2f(0f, 0f); glVertex2f(px.toFloat(), py + ph + dy)
            glTexCoord2f(uMax, 0f); glVertex2f(px + pw.toFloat(), py + ph + dy)
            glTexCoord2f(uMax, vMax); glVertex2f(px + pw.toFloat(), py + dy)
            glEnd()
        }
        glColor4f(1f, 1f, 1f, 1f)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_STENCIL_TEST)
        glDeleteTextures(tex)
        glMatrixMode(GL_PROJECTION); glPopMatrix()
        glMatrixMode(GL_MODELVIEW); glPopMatrix()
        glPopAttrib()
    }

    // ========== Radial 模糊（高斯权重） ==========
    private fun drawRadialBlur(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        val strength = BlurSettings.radialStrength
        val samples = BlurSettings.radialSamples
        drawRadialBlurInternal(x, y, w, h, samples, strength, radius)
    }

    private fun drawRadialBlurInternal(x: Float, y: Float, w: Float, h: Float, samples: Int, strength: Float, radius: Float) {
        val px = x; val py = y
        val pw = w; val ph = h

        val scissorX = px.toInt().coerceAtLeast(0)
        val scissorY = (mc.displayHeight - py - ph).toInt().coerceAtLeast(0)
        val scissorW = pw.toInt().coerceAtMost(mc.displayWidth - scissorX)
        val scissorH = ph.toInt().coerceAtMost(mc.displayHeight - scissorY)

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity()
        glDisable(GL_DEPTH_TEST); glDisable(GL_LIGHTING); glEnable(GL_TEXTURE_2D)

        glReadBuffer(GL_BACK)
        val tex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, px.toInt(), (mc.displayHeight - py - ph).toInt(), pw.toInt(), ph.toInt(), 0)

        glEnable(GL_SCISSOR_TEST)
        glScissor(scissorX, scissorY, scissorW, scissorH)
        setupStencilMask(px, py, pw, ph, radius)

        val sigma = strength / 2.0f
        val radialSteps = (strength / 2f).toInt().coerceAtLeast(1)
        val offsets = mutableListOf<Pair<Float, Float>>()
        val weights = mutableListOf<Float>()

        offsets.add(0f to 0f)
        weights.add(1.0f)

        for (r in 1..radialSteps) {
            val radius2 = (strength * r) / radialSteps
            val radiusWeight = exp(-(radius2 * radius2) / (2.0 * sigma * sigma)).toFloat()
            val perSampleWeight = radiusWeight / samples
            for (i in 0 until samples) {
                val angle = 2.0 * Math.PI * i / samples
                val ox = (cos(angle) * radius2).toFloat()
                val oy = (sin(angle) * radius2).toFloat()
                offsets.add(ox to oy)
                weights.add(perSampleWeight)
            }
        }

        drawWeightedTaps(px, py, pw, ph, offsets, weights, 1f)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_STENCIL_TEST)
        glDeleteTextures(tex)
        glMatrixMode(GL_PROJECTION); glPopMatrix()
        glMatrixMode(GL_MODELVIEW); glPopMatrix()
        glPopAttrib()
    }

    // ========== Kawase 模式（着色器高斯模糊，已修正 FBO 渲染） ==========
    private var kawaseGaussianProgram = -1
    private var kawaseGaussianTexelSizeUniform = -1
    private var kawaseFboA: Framebuffer? = null
    private var kawaseFboB: Framebuffer? = null

    private fun initKawaseGaussianShader() {
        if (!GLContext.getCapabilities().OpenGL20) return
        if (kawaseGaussianProgram != -1) return

        val vert = compileShader(GL_VERTEX_SHADER, """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                gl_TexCoord[0] = gl_MultiTexCoord0;
            }
        """.trimIndent())

        val frag = compileShader(GL_FRAGMENT_SHADER, """
            #version 120
            uniform sampler2D texture;
            uniform vec2 texelSize;
            void main() {
                vec2 tc = gl_TexCoord[0].st;
                vec4 sum = vec4(0.0);
                float totalWeight = 0.0;

                // 7x7 高斯核，sigma 通过 texelSize 的缩放间接控制
                for (int i = -7; i <= 7; i++) {
                    for (int j = -7; j <= 7; j++) {
                        float weight = exp(-float(i*i + j*j) / (2.0 * texelSize.x * texelSize.x));
                        sum += texture2D(texture, tc + vec2(float(i) * texelSize.x, float(j) * texelSize.y)) * weight;
                        totalWeight += weight;
                    }
                }
                gl_FragColor = sum / totalWeight;
            }
        """.trimIndent())

        if (vert == 0 || frag == 0) return

        kawaseGaussianProgram = glCreateProgram()
        glAttachShader(kawaseGaussianProgram, vert)
        glAttachShader(kawaseGaussianProgram, frag)
        glLinkProgram(kawaseGaussianProgram)

        if (glGetProgrami(kawaseGaussianProgram, GL_LINK_STATUS) == GL_FALSE) {
            kawaseGaussianProgram = -1
        } else {
            kawaseGaussianTexelSizeUniform = glGetUniformLocation(kawaseGaussianProgram, "texelSize")
        }
        glDeleteShader(vert)
        glDeleteShader(frag)
    }

    private fun ensureKawaseFramebuffers(width: Int, height: Int) {
        if (kawaseFboA == null || kawaseFboA!!.framebufferWidth != width || kawaseFboA!!.framebufferHeight != height) {
            kawaseFboA?.deleteFramebuffer()
            kawaseFboB?.deleteFramebuffer()
            kawaseFboA = Framebuffer(width, height, false)
            kawaseFboB = Framebuffer(width, height, false)
        }
    }

    private fun drawKawaseBlur(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        if (kawaseGaussianProgram == -1) initKawaseGaussianShader()
        if (kawaseGaussianProgram == -1) return

        val iterations = BlurSettings.kawaseIterations.coerceIn(0, 20)
        if (iterations <= 0) return

        val sigma = BlurSettings.kawaseOffset.toFloat()
        val px = x.toInt(); val py = y.toInt()
        val pw = w.toInt(); val ph = h.toInt()

        val scissorX = px.coerceAtLeast(0)
        val scissorY = (mc.displayHeight - py - ph).coerceAtLeast(0)
        val scissorW = pw.coerceAtMost(mc.displayWidth - scissorX)
        val scissorH = ph.coerceAtMost(mc.displayHeight - scissorY)

        ensureKawaseFramebuffers(pw, ph)
        val fboA = kawaseFboA!!
        val fboB = kawaseFboB!!

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity()
        glDisable(GL_DEPTH_TEST); glDisable(GL_LIGHTING); glEnable(GL_TEXTURE_2D)

        glReadBuffer(GL_BACK)
        val capturedTex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, capturedTex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, px, mc.displayHeight - py - ph, pw, ph, 0)

        // FBO 投影矩阵和视口
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, pw.toDouble(), ph.toDouble(), 0.0, -1.0, 1.0)
        glViewport(0, 0, pw, ph)

        fboA.bindFramebuffer(true)
        glClear(GL_COLOR_BUFFER_BIT)
        glBindTexture(GL_TEXTURE_2D, capturedTex)
        glDisable(GL_BLEND)
        glBegin(GL_QUADS)
        glTexCoord2f(0f, 1f); glVertex2f(0f, 0f)
        glTexCoord2f(0f, 0f); glVertex2f(0f, ph.toFloat())
        glTexCoord2f(1f, 0f); glVertex2f(pw.toFloat(), ph.toFloat())
        glTexCoord2f(1f, 1f); glVertex2f(pw.toFloat(), 0f)
        glEnd()
        fboA.unbindFramebuffer()

        val stepX = sigma / pw
        val stepY = sigma / ph
        glUseProgram(kawaseGaussianProgram)
        glUniform2f(kawaseGaussianTexelSizeUniform, stepX, stepY)

        var src = fboA
        var dst = fboB
        for (i in 1..iterations) {
            dst.bindFramebuffer(true)
            glClear(GL_COLOR_BUFFER_BIT)
            glBindTexture(GL_TEXTURE_2D, src.framebufferTexture)
            glDisable(GL_BLEND)
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 1f); glVertex2f(0f, 0f)
            glTexCoord2f(0f, 0f); glVertex2f(0f, ph.toFloat())
            glTexCoord2f(1f, 0f); glVertex2f(pw.toFloat(), ph.toFloat())
            glTexCoord2f(1f, 1f); glVertex2f(pw.toFloat(), 0f)
            glEnd()
            dst.unbindFramebuffer()
            val tmp = src
            src = dst
            dst = tmp
        }

        // 恢复屏幕投影和视口
        mc.framebuffer.bindFramebuffer(true)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glViewport(0, 0, mc.displayWidth, mc.displayHeight)

        glEnable(GL_SCISSOR_TEST)
        glScissor(scissorX, scissorY, scissorW, scissorH)
        setupStencilMask(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), radius)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1f, 1f, 1f, 1f)
        glBindTexture(GL_TEXTURE_2D, src.framebufferTexture)
        glBegin(GL_QUADS)
        glTexCoord2f(0f, 1f); glVertex2f(px.toFloat(), py.toFloat())
        glTexCoord2f(0f, 0f); glVertex2f(px.toFloat(), (py + ph).toFloat())
        glTexCoord2f(1f, 0f); glVertex2f((px + pw).toFloat(), (py + ph).toFloat())
        glTexCoord2f(1f, 1f); glVertex2f((px + pw).toFloat(), py.toFloat())
        glEnd()

        glUseProgram(0)
        glColor4f(1f, 1f, 1f, 1f)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_STENCIL_TEST)
        glDeleteTextures(capturedTex)
        glMatrixMode(GL_PROJECTION); glPopMatrix()
        glMatrixMode(GL_MODELVIEW); glPopMatrix()
        glPopAttrib()
    }

    // ========== Shader 模式（同样修正 FBO 渲染） ==========
    private var shaderProgram = -1
    private var texelSizeUniform = -1
    private var fboA: Framebuffer? = null
    private var fboB: Framebuffer? = null

    private fun initShader() {
        if (!GLContext.getCapabilities().OpenGL20) return
        if (shaderProgram != -1) return
        val vert = compileShader(GL_VERTEX_SHADER, """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                gl_TexCoord[0] = gl_MultiTexCoord0;
            }
        """.trimIndent())
        val frag = compileShader(GL_FRAGMENT_SHADER, """
            #version 120
            uniform sampler2D texture;
            uniform vec2 texelSize;
            void main() {
                vec2 tc = gl_TexCoord[0].st;
                vec4 sum = vec4(0.0);
                sum += texture2D(texture, tc + vec2(-1.0, -1.0) * texelSize) * 0.0625;
                sum += texture2D(texture, tc + vec2( 0.0, -1.0) * texelSize) * 0.125;
                sum += texture2D(texture, tc + vec2( 1.0, -1.0) * texelSize) * 0.0625;
                sum += texture2D(texture, tc + vec2(-1.0,  0.0) * texelSize) * 0.125;
                sum += texture2D(texture, tc + vec2( 0.0,  0.0) * texelSize) * 0.25;
                sum += texture2D(texture, tc + vec2( 1.0,  0.0) * texelSize) * 0.125;
                sum += texture2D(texture, tc + vec2(-1.0,  1.0) * texelSize) * 0.0625;
                sum += texture2D(texture, tc + vec2( 0.0,  1.0) * texelSize) * 0.125;
                sum += texture2D(texture, tc + vec2( 1.0,  1.0) * texelSize) * 0.0625;
                gl_FragColor = sum;
            }
        """.trimIndent())
        if (vert == 0 || frag == 0) return
        shaderProgram = glCreateProgram()
        glAttachShader(shaderProgram, vert)
        glAttachShader(shaderProgram, frag)
        glLinkProgram(shaderProgram)
        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) shaderProgram = -1
        else texelSizeUniform = glGetUniformLocation(shaderProgram, "texelSize")
        glDeleteShader(vert); glDeleteShader(frag)
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

    private fun ensureFramebuffers(width: Int, height: Int) {
        if (fboA == null || fboA!!.framebufferWidth != width || fboA!!.framebufferHeight != height) {
            fboA?.deleteFramebuffer()
            fboB?.deleteFramebuffer()
            fboA = Framebuffer(width, height, false)
            fboB = Framebuffer(width, height, false)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun drawShaderBlur(x: Float, y: Float, w: Float, h: Float, samples: Int, strength: Float, radius: Float) {
        if (shaderProgram == -1) initShader()
        if (shaderProgram == -1) return
        val actualStrength = BlurSettings.shaderStrength
        val iterations = BlurSettings.shaderIterations.coerceIn(0, 20)
        if (iterations <= 0) return
        val px = x.toInt(); val py = y.toInt()
        val pw = w.toInt(); val ph = h.toInt()
        val scissorX = px.coerceAtLeast(0)
        val scissorY = (mc.displayHeight - py - ph).coerceAtLeast(0)
        val scissorW = pw.coerceAtMost(mc.displayWidth - scissorX)
        val scissorH = ph.coerceAtMost(mc.displayHeight - scissorY)
        ensureFramebuffers(pw, ph)
        val fboA = this.fboA!!
        val fboB = this.fboB!!

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity()
        glDisable(GL_DEPTH_TEST); glDisable(GL_LIGHTING); glEnable(GL_TEXTURE_2D)

        glReadBuffer(GL_BACK)
        val capturedTex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, capturedTex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, px, mc.displayHeight - py - ph, pw, ph, 0)

        // FBO 投影矩阵和视口
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, pw.toDouble(), ph.toDouble(), 0.0, -1.0, 1.0)
        glViewport(0, 0, pw, ph)

        fboA.bindFramebuffer(true)
        glClear(GL_COLOR_BUFFER_BIT)
        glBindTexture(GL_TEXTURE_2D, capturedTex)
        glDisable(GL_BLEND)
        glBegin(GL_QUADS)
        glTexCoord2f(0f, 1f); glVertex2f(0f, 0f)
        glTexCoord2f(0f, 0f); glVertex2f(0f, ph.toFloat())
        glTexCoord2f(1f, 0f); glVertex2f(pw.toFloat(), ph.toFloat())
        glTexCoord2f(1f, 1f); glVertex2f(pw.toFloat(), 0f)
        glEnd()
        fboA.unbindFramebuffer()

        val qualityFactor = 1f + BlurSettings.quality * 0.1f
        val stepX = actualStrength * qualityFactor / pw
        val stepY = actualStrength * qualityFactor / ph
        glUseProgram(shaderProgram)
        glUniform2f(texelSizeUniform, stepX, stepY)

        var src = fboA; var dst = fboB
        for (i in 1..iterations) {
            dst.bindFramebuffer(true)
            glClear(GL_COLOR_BUFFER_BIT)
            glBindTexture(GL_TEXTURE_2D, src.framebufferTexture)
            glDisable(GL_BLEND)
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 1f); glVertex2f(0f, 0f)
            glTexCoord2f(0f, 0f); glVertex2f(0f, ph.toFloat())
            glTexCoord2f(1f, 0f); glVertex2f(pw.toFloat(), ph.toFloat())
            glTexCoord2f(1f, 1f); glVertex2f(pw.toFloat(), 0f)
            glEnd()
            dst.unbindFramebuffer()
            val tmp = src; src = dst; dst = tmp
        }

        // 恢复屏幕投影和视口
        mc.framebuffer.bindFramebuffer(true)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glViewport(0, 0, mc.displayWidth, mc.displayHeight)

        glEnable(GL_SCISSOR_TEST)
        glScissor(scissorX, scissorY, scissorW, scissorH)
        setupStencilMask(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), radius)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1f, 1f, 1f, 1f)
        glBindTexture(GL_TEXTURE_2D, src.framebufferTexture)
        glBegin(GL_QUADS)
        glTexCoord2f(0f, 1f); glVertex2f(px.toFloat(), py.toFloat())
        glTexCoord2f(0f, 0f); glVertex2f(px.toFloat(), (py + ph).toFloat())
        glTexCoord2f(1f, 0f); glVertex2f((px + pw).toFloat(), (py + ph).toFloat())
        glTexCoord2f(1f, 1f); glVertex2f((px + pw).toFloat(), py.toFloat())
        glEnd()

        glUseProgram(0)
        glColor4f(1f, 1f, 1f, 1f)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_STENCIL_TEST)
        glDeleteTextures(capturedTex)
        glMatrixMode(GL_PROJECTION); glPopMatrix()
        glMatrixMode(GL_MODELVIEW); glPopMatrix()
        glPopAttrib()
    }

    // ========== Gaussian 模式（CPU 实现，真正的高斯模糊） ==========
    @Suppress("UNUSED_PARAMETER")
    private fun drawGaussianBlur(x: Float, y: Float, w: Float, h: Float, samples: Int, strength: Float, radius: Float) {
        val spread = (BlurSettings.gaussianStrength * (1f + BlurSettings.gaussianQuality * 0.1f)).coerceAtLeast(0.75f)
        val opacity = (BlurSettings.gaussianOpacity / 100f).coerceIn(0f, 1f)
        drawDirectionalGaussianBlur(x, y, w, h, spread, BlurSettings.gaussianDirections, BlurSettings.gaussianLayers, radius, opacity)
    }

    /**
     * 用累积权重（over 混合）把多次采样合成为真正的加权平均，从而得到真正的高斯模糊。
     * 第 k 次绘制的 alpha = w_k / (w_1 + ... + w_k)，n 次叠加后结果恰为 Σ(w_i * sample_i) / Σ(w_i)。
     */
    private fun drawWeightedTaps(
        px: Float, py: Float, pw: Float, ph: Float,
        offsets: List<Pair<Float, Float>>, weights: List<Float>, offsetScale: Float
    ) {
        if (offsets.isEmpty()) return
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        // 输出 alpha 只取采样权重（glColor 的 alpha），忽略截屏自身的 alpha；
        // 否则玻璃/树叶/荷叶等半透明处 alpha<1 会稀释模糊甚至让它消失。RGB 仍按纹理 * 颜色调制。
        if (GLContext.getCapabilities().OpenGL13) {
            glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_COMBINE)
            glTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_RGB, GL_MODULATE)
            glTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_RGB, GL_TEXTURE)
            glTexEnvi(GL_TEXTURE_ENV, GL_SOURCE1_RGB, GL_PRIMARY_COLOR)
            glTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_ALPHA, GL_REPLACE)
            glTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_ALPHA, GL_PRIMARY_COLOR)
        }
        var cumulative = 0f
        for (i in offsets.indices) {
            cumulative += weights[i]
            if (cumulative <= 0f) continue
            val alpha = (weights[i] / cumulative).coerceIn(0f, 1f)
            val ox = offsets[i].first * offsetScale
            val oy = offsets[i].second * offsetScale
            glColor4f(1f, 1f, 1f, alpha)
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 1f); glVertex2f(px + ox, py + oy)
            glTexCoord2f(0f, 0f); glVertex2f(px + ox, py + ph + oy)
            glTexCoord2f(1f, 0f); glVertex2f(px + pw + ox, py + ph + oy)
            glTexCoord2f(1f, 1f); glVertex2f(px + pw + ox, py + oy)
            glEnd()
        }
        glColor4f(1f, 1f, 1f, 1f)
    }

    // ========== 圆角模板遮罩：把模糊裁切成圆角矩形 ==========
    /**
     * 统一的模板遮罩入口：给了 [maskRects] 就用这些矩形的并集，否则用原来的单矩形圆角遮罩。
     */
    private fun setupStencilMask(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        val rects = maskRects
        if (!rects.isNullOrEmpty()) {
            setupRectsStencilMask(rects)
        } else if (radius > 0f) {
            setupRoundedStencilMask(x, y, w, h, radius)
        }
    }

    /**
     * 与 [setupRoundedStencilMask] 相同的模板写入流程，但遮罩是多个矩形（直角）的并集，
     * 这样整块模糊时只保留真正有背景的区域，不会把旁边的空白也糊上。
     */
    private fun setupRectsStencilMask(rects: List<FloatArray>) {
        Stencil.checkSetupFBO()

        glEnable(GL_STENCIL_TEST)
        glClearStencil(0)
        glClear(GL_STENCIL_BUFFER_BIT)
        glStencilMask(0xFF)
        glStencilFunc(GL_ALWAYS, 1, 0xFF)
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE)

        glColorMask(false, false, false, false)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_CULL_FACE)

        glBegin(GL_QUADS)
        for (r in rects) {
            glVertex2f(r[0], r[1])
            glVertex2f(r[0], r[3])
            glVertex2f(r[2], r[3])
            glVertex2f(r[2], r[1])
        }
        glEnd()

        glColorMask(true, true, true, true)
        glEnable(GL_TEXTURE_2D)

        // 之后只绘制遮罩内部的内容
        glStencilFunc(GL_EQUAL, 1, 0xFF)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
    }

    /**
     * 在当前（默认）帧缓冲的模板缓冲里写入一个圆角矩形遮罩，并启用模板测试。
     * 之后绘制的任何内容只会在圆角矩形内部可见。
     * 坐标与调用方使用的正交投影一致（屏幕像素，原点左上）。
     */
    private fun setupRoundedStencilMask(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        if (w <= 0f || h <= 0f) return
        Stencil.checkSetupFBO()

        glEnable(GL_STENCIL_TEST)
        glClearStencil(0)
        glClear(GL_STENCIL_BUFFER_BIT)
        glStencilMask(0xFF)
        glStencilFunc(GL_ALWAYS, 1, 0xFF)
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE)

        glColorMask(false, false, false, false)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_CULL_FACE)

        val r = min(radius, min(w, h) / 2f).coerceAtLeast(0f)
        val x1 = x; val y1 = y; val x2 = x + w; val y2 = y + h
        val steps = (r * 1.5f).toInt().coerceIn(4, 24)

        glBegin(GL_TRIANGLE_FAN)
        glVertex2f((x1 + x2) / 2f, (y1 + y2) / 2f)
        glVertex2f(x1 + r, y1)
        glVertex2f(x2 - r, y1)
        traceArc(x2 - r, y1 + r, r, -90.0, 0.0, steps)
        glVertex2f(x2, y2 - r)
        traceArc(x2 - r, y2 - r, r, 0.0, 90.0, steps)
        glVertex2f(x1 + r, y2)
        traceArc(x1 + r, y2 - r, r, 90.0, 180.0, steps)
        glVertex2f(x1, y1 + r)
        traceArc(x1 + r, y1 + r, r, 180.0, 270.0, steps)
        glEnd()

        glColorMask(true, true, true, true)
        glEnable(GL_TEXTURE_2D)

        // 之后只绘制遮罩内部的内容
        glStencilFunc(GL_EQUAL, 1, 0xFF)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
    }

    private fun traceArc(cx: Float, cy: Float, radius: Float, fromDeg: Double, toDeg: Double, steps: Int) {
        for (i in 0..steps) {
            val a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps)
            glVertex2f(cx + (radius * cos(a)).toFloat(), cy + (radius * sin(a)).toFloat())
        }
    }

    // ========== Naven 模式（单 pass shader 模糊，移植自 Naven-Modern RadialBlurUtils / blur_radial.frag） ==========
    // 一次片段着色器里做 16 个方向 × 若干层的高斯散开采样，只用一个 pass，不做 down/up 多级叠加。
    private var navenProgram = -1
    private var navenTexLoc = -1
    private var navenOneTexelLoc = -1
    private var navenOpacityLoc = -1
    private var navenRadiusLoc = -1
    private var navenLayersLoc = -1
    private var navenDirsLoc = -1

    private fun initNavenShader() {
        if (!GLContext.getCapabilities().OpenGL20) return
        if (navenProgram != -1) return

        val vert = """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                gl_TexCoord[0] = gl_MultiTexCoord0;
            }
        """.trimIndent()

        val frag = """
            #version 120
            uniform sampler2D texture;
            uniform vec2 oneTexel;
            uniform float uRadius;
            uniform int uLayers;
            uniform int uDirs;
            uniform float uOpacity;
            void main() {
                vec2 uv = gl_TexCoord[0].st;
                vec4 sum = texture2D(texture, uv);
                float wsum = 1.0;
                if (uRadius > 0.0 && uLayers > 0 && uDirs > 0) {
                    float angStep = 6.2831853 / float(uDirs);
                    for (int d = 0; d < 32; ++d) {
                        if (d >= uDirs) break;
                        float ang = float(d) * angStep;
                        vec2 dir = vec2(cos(ang), sin(ang));
                        for (int l = 1; l <= 24; ++l) {
                            if (l > uLayers) break;
                            float t = float(l) / float(uLayers);
                            float w = exp(-t * t * 3.0);
                            vec2 off = dir * oneTexel * uRadius * t;
                            sum += texture2D(texture, uv + off) * w;
                            wsum += w;
                        }
                    }
                }
                // 忽略截屏 alpha：玻璃/树叶/荷叶等半透明处 alpha<1 会让模糊消失，改用 uOpacity 控制
                gl_FragColor = vec4((sum / wsum).rgb, uOpacity);
            }
        """.trimIndent()

        navenProgram = linkProgram(vert, frag)
        if (navenProgram != -1) {
            navenTexLoc = glGetUniformLocation(navenProgram, "texture")
            navenOneTexelLoc = glGetUniformLocation(navenProgram, "oneTexel")
            navenOpacityLoc = glGetUniformLocation(navenProgram, "uOpacity")
            navenRadiusLoc = glGetUniformLocation(navenProgram, "uRadius")
            navenLayersLoc = glGetUniformLocation(navenProgram, "uLayers")
            navenDirsLoc = glGetUniformLocation(navenProgram, "uDirs")
        }
    }

    private fun linkProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GL_FRAGMENT_SHADER, fragSrc)
        if (vert == 0 || frag == 0) return -1
        val program = glCreateProgram()
        glAttachShader(program, vert)
        glAttachShader(program, frag)
        glLinkProgram(program)
        val linked = glGetProgrami(program, GL_LINK_STATUS) != GL_FALSE
        glDeleteShader(vert)
        glDeleteShader(frag)
        return if (linked) program else -1
    }

    private fun drawNavenBlur(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        if (navenProgram == -1) initNavenShader()
        if (navenProgram == -1) return

        val px = x.toInt(); val py = y.toInt()
        val pw = w.toInt(); val ph = h.toInt()
        if (pw <= 0 || ph <= 0) return

        val radialRadius = BlurSettings.navenRadius
        val layers = BlurSettings.navenLayers.coerceIn(1, 24)
        val dirs = BlurSettings.navenDirections.coerceIn(8, 32)

        // 向外多采样一圈（半径 + 2px），让组件边缘也能吃到周围的背景，最后仍只写回原矩形
        val margin = (radialRadius.toInt() + 2).coerceAtLeast(1)
        val capX = (px - margin).coerceAtLeast(0)
        val capY = (py - margin).coerceAtLeast(0)
        val capW = (pw + 2 * margin).coerceAtMost(mc.displayWidth - capX)
        val capH = (ph + 2 * margin).coerceAtMost(mc.displayHeight - capY)
        if (capW <= 0 || capH <= 0) return

        // 写回范围：只裁回原矩形
        val scissorX = px.coerceAtLeast(0)
        val scissorY = (mc.displayHeight - py - ph).coerceAtLeast(0)
        val scissorW = pw.coerceAtMost(mc.displayWidth - scissorX)
        val scissorH = ph.coerceAtMost(mc.displayHeight - scissorY)

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity()
        glDisable(GL_DEPTH_TEST); glDisable(GL_LIGHTING); glEnable(GL_TEXTURE_2D)
        glDisable(GL_ALPHA_TEST)

        glReadBuffer(GL_BACK)
        val capturedTex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, capturedTex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, capX, mc.displayHeight - capY - capH, capW, capH, 0)

        glEnable(GL_SCISSOR_TEST)
        glScissor(scissorX, scissorY, scissorW, scissorH)
        setupStencilMask(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), radius)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1f, 1f, 1f, 1f)
        glUseProgram(navenProgram)
        glUniform1i(navenTexLoc, 0)
        glUniform2f(navenOneTexelLoc, 1f / capW, 1f / capH)
        glUniform1f(navenOpacityLoc, (BlurSettings.navenOpacity / 100f).coerceIn(0f, 1f))
        glUniform1f(navenRadiusLoc, radialRadius)
        glUniform1i(navenLayersLoc, layers)
        glUniform1i(navenDirsLoc, dirs)
        glBindTexture(GL_TEXTURE_2D, capturedTex)
        drawTexturedQuad(capX.toFloat(), capY.toFloat(), capW.toFloat(), capH.toFloat())
        glUseProgram(0)

        glColor4f(1f, 1f, 1f, 1f)
        glDisable(GL_SCISSOR_TEST)
        glDisable(GL_STENCIL_TEST)
        glDeleteTextures(capturedTex)
        glMatrixMode(GL_PROJECTION); glPopMatrix()
        glMatrixMode(GL_MODELVIEW); glPopMatrix()
        glPopAttrib()
    }

    private fun drawTexturedQuad(x: Float, y: Float, w: Float, h: Float) {
        glBegin(GL_QUADS)
        glTexCoord2f(0f, 1f); glVertex2f(x, y)
        glTexCoord2f(0f, 0f); glVertex2f(x, y + h)
        glTexCoord2f(1f, 0f); glVertex2f(x + w, y + h)
        glTexCoord2f(1f, 1f); glVertex2f(x + w, y)
        glEnd()
    }

    // ========== 边框模糊功能 ==========
    @JvmOverloads
    fun drawBorderBlur(x: Float, y: Float, w: Float, h: Float, radius: Float = 0f) {
        if (!BlurSettings.borderEnabled) return
        val borderWidth = BlurSettings.borderWidth
        val blurStrength = BlurSettings.borderBlurStrength
        val borderColor = BlurSettings.borderColor
        val borderOpacity = BlurSettings.borderOpacity / 255f
        val borderOnly = BlurSettings.borderOnly

        val bx = x - borderWidth
        val by = y - borderWidth
        val bw = w + 2 * borderWidth
        val bh = h + 2 * borderWidth

        drawOffsetBlur(bx, by, bw, bh, BlurSettings.passes, blurStrength, radius + borderWidth)

        if (borderOnly) {
            drawRect(x, y, x + w, y + h, Color.BLACK.rgb)
        }

        val colorAlpha = Color(
            borderColor.red,
            borderColor.green,
            borderColor.blue,
            (borderColor.alpha * borderOpacity).toInt()
        )
        drawRect(bx, by, bx + bw, by + borderWidth, colorAlpha.rgb)
        drawRect(bx, by + bh - borderWidth, bx + bw, by + bh, colorAlpha.rgb)
        drawRect(bx, by + borderWidth, bx + borderWidth, by + bh - borderWidth, colorAlpha.rgb)
        drawRect(bx + bw - borderWidth, by + borderWidth, bx + bw, by + bh - borderWidth, colorAlpha.rgb)
    }

    private fun drawRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        net.minecraft.client.renderer.GlStateManager.enableBlend()
        net.minecraft.client.renderer.GlStateManager.disableTexture2D()
        net.minecraft.client.renderer.GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        val tessellator = net.minecraft.client.renderer.Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        worldRenderer.begin(GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR)
        worldRenderer.pos(left.toDouble(), bottom.toDouble(), 0.0).color(r, g, b, a).endVertex()
        worldRenderer.pos(right.toDouble(), bottom.toDouble(), 0.0).color(r, g, b, a).endVertex()
        worldRenderer.pos(right.toDouble(), top.toDouble(), 0.0).color(r, g, b, a).endVertex()
        worldRenderer.pos(left.toDouble(), top.toDouble(), 0.0).color(r, g, b, a).endVertex()
        tessellator.draw()

        net.minecraft.client.renderer.GlStateManager.enableTexture2D()
        net.minecraft.client.renderer.GlStateManager.disableBlend()
    }
}
