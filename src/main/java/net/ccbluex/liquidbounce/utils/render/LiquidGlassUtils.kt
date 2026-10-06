package net.ccbluex.liquidbounce.utils.render

import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.utils.render.shader.ShaderUtil
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture

/**
 * Liquid Glass —— 单趟"液态玻璃"后处理（移植自 LiquidGlassShader-main 的 V2 片源，按本客户端
 * 的即时模式 + 内联 GLSL 1.20 改写）。
 *
 * 设计要点：
 * - 与 [BlurUtils] 解耦：本类自己编译着色器、自己抓屏，不依赖 BlurUtils 的任何内部逻辑。
 * - 但**矩形位置与圆角半径仍由调用方经 BlurUtils 传入**：组件照常把 `x/y/w/h/radius` 交给
 *   `BlurUtils.drawOffsetBlur(...)`，由它转交到这里，从而复用所有组件已有的定位/圆角。
 *
 * 效果：把面板背后的画面抓成纹理 → 按到边缘的距离做折射位移（透镜）→ 叠加磨砂模糊、屏幕噪点、
 * 方向性边缘高光；Tinted 模式再加 Fresnel、色散（R/B 反向偏移）、自适应染色与亮度整形。
 */
object LiquidGlassUtils {
    private val mc = Minecraft.getMinecraft()

    /** 通用于两个片源的顶点着色器（固定管线：位置矩阵 + 把材质坐标写进 gl_TexCoord[0]）。 */
    private const val VERTEX = """
#version 120
void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    gl_TexCoord[0] = gl_MultiTexCoord0;
}
"""

    /** Clear —— 清晰玻璃：折射 + 磨砂 + 噪点 + 方向性边缘高光。 */
    private const val CLEAR_FRAG = """
#version 120
uniform sampler2D uBlurTex;
uniform vec2 uQuadSize;
uniform float uRadius;
uniform float uBlurRadius;
uniform float uAspectCorrect;
uniform float uRefractionPower;
uniform float uRefractionEdge;
uniform float uNoise;
uniform float uGlobalAlpha;
uniform float uGlowWeight;
uniform float uGlowBias;
uniform float uGlowEdge0;
uniform float uGlowEdge1;

const float M_E = 2.718281828459045;

float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }

// 折射随"进入玻璃的深度"衰减：边缘最狠，向内衰减到 0 位移。
float falloff(float x) { return 1.0 - 2.3 * pow(5.2 * M_E, -6.9 * x - 0.7); }

// 圆角矩形有符号距离（像素单位），r 为圆角半径。
float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// 9 抽样磨砂，偏移按"每个轴各自的 UV"给（r 为 vec2），这样长宽不等时模糊半径在屏幕上仍是圆形。
vec4 blurSample(vec2 uv, vec2 r) {
    vec4 sum = texture2D(uBlurTex, uv) * 0.25;
    sum += texture2D(uBlurTex, uv + vec2(r.x, 0.0)) * 0.125;
    sum += texture2D(uBlurTex, uv - vec2(r.x, 0.0)) * 0.125;
    sum += texture2D(uBlurTex, uv + vec2(0.0, r.y)) * 0.125;
    sum += texture2D(uBlurTex, uv - vec2(0.0, r.y)) * 0.125;
    sum += texture2D(uBlurTex, uv + vec2(r.x, r.y) * 0.7071) * 0.0625;
    sum += texture2D(uBlurTex, uv + vec2(-r.x, r.y) * 0.7071) * 0.0625;
    sum += texture2D(uBlurTex, uv + vec2(r.x, -r.y) * 0.7071) * 0.0625;
    sum += texture2D(uBlurTex, uv + vec2(-r.x, -r.y) * 0.7071) * 0.0625;
    return sum;
}

void main() {
    vec2 localUV = gl_TexCoord[0].xy;
    vec2 halfSize = uQuadSize * 0.5;
    float minSide = min(uQuadSize.x, uQuadSize.y);

    float d = sdRoundBox((localUV - 0.5) * uQuadSize, halfSize, uRadius);
    float edge = 1.0 - smoothstep(-1.0, 1.0, d);
    if (edge <= 0.0) discard;

    float distPx = max(-d, 0.0);
    float distN = clamp(distPx / max(uRefractionEdge, 0.001), 0.0, 1.0);
    float refraction = pow(falloff(distN), uRefractionPower);

    vec2 sampleUV = 0.5 + (localUV - 0.5) * refraction;
    if (max(sampleUV.x, sampleUV.y) > 1.0 || min(sampleUV.x, sampleUV.y) < 0.0) discard;

    // 模糊半径：uAspectCorrect=1 时按组件长宽比换算（各轴 UV 不同），保证屏幕像素上是圆形的模糊。
    vec2 blurR = mix(vec2(uBlurRadius / max(minSide, 1.0)), vec2(uBlurRadius) / max(uQuadSize, vec2(1.0)), uAspectCorrect);
    vec3 color = blurSample(sampleUV, blurR).rgb;

    float noise = (rand(gl_FragCoord.xy * 1e-3) - 0.5) * uNoise;
    color += vec3(noise);

    // 边缘高光：distN 越小(越靠边) mask 越强，形成一圈标志性的玻璃边缘高光。
    vec2 g = localUV * 2.0 - 1.0;
    float glow = sin(atan(g.y, g.x) - 0.5);
    float glowMask = 1.0 - smoothstep(uGlowEdge0, uGlowEdge1, distN);
    color *= glow * uGlowWeight * glowMask + 1.0 + uGlowBias * glowMask;

    gl_FragColor = vec4(color, edge * uGlobalAlpha);
}
"""

    /** Tinted —— 有色玻璃：折射 + Fresnel + 色散 + 自适应染色 + 亮度整形。 */
    private const val TINTED_FRAG = """
#version 120
uniform sampler2D uBlurTex;
uniform vec2 uQuadSize;
uniform float uRadius;
uniform float uBlurRadius;
uniform float uAspectCorrect;
uniform float uRefractionPower;
uniform float uRefractionEdge;
uniform float uNoise;
uniform float uGlobalAlpha;
uniform vec3 uTintColor;
uniform float uTintStrength;
uniform float uChromaStrength;
uniform float uDarkness;

const float M_E = 2.718281828459045;

float falloff(float x) { return 1.0 - 2.3 * pow(5.2 * M_E, -6.9 * x - 0.7); }

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

vec4 blurSample(vec2 uv, vec2 r) {
    vec4 sum = texture2D(uBlurTex, uv) * 0.25;
    sum += texture2D(uBlurTex, uv + vec2(r.x, 0.0)) * 0.125;
    sum += texture2D(uBlurTex, uv - vec2(r.x, 0.0)) * 0.125;
    sum += texture2D(uBlurTex, uv + vec2(0.0, r.y)) * 0.125;
    sum += texture2D(uBlurTex, uv - vec2(0.0, r.y)) * 0.125;
    sum += texture2D(uBlurTex, uv + vec2(r.x, r.y) * 0.7071) * 0.0625;
    sum += texture2D(uBlurTex, uv + vec2(-r.x, r.y) * 0.7071) * 0.0625;
    sum += texture2D(uBlurTex, uv + vec2(r.x, -r.y) * 0.7071) * 0.0625;
    sum += texture2D(uBlurTex, uv + vec2(-r.x, -r.y) * 0.7071) * 0.0625;
    return sum;
}

void main() {
    vec2 localUV = gl_TexCoord[0].xy;
    vec2 halfSize = uQuadSize * 0.5;
    float minSide = min(uQuadSize.x, uQuadSize.y);

    float d = sdRoundBox((localUV - 0.5) * uQuadSize, halfSize, uRadius);
    float edge = 1.0 - smoothstep(-1.0, 1.0, d);
    if (edge <= 0.0) discard;

    float distPx = max(-d, 0.0);
    float distN = clamp(distPx / max(uRefractionEdge, 0.001), 0.0, 1.0);
    float fresnel = pow(1.0 - distN, 3.0);
    float refraction = pow(falloff(distN), uRefractionPower);

    vec2 sampleUV = 0.5 + (localUV - 0.5) * refraction;

    // 色散：R / B 沿径向反向偏移。
    vec2 chromaDir = normalize((localUV - 0.5) + vec2(0.00001));
    vec2 chromaOffset = chromaDir * fresnel * uChromaStrength;

    vec2 blurR = mix(vec2(uBlurRadius / max(minSide, 1.0)), vec2(uBlurRadius) / max(uQuadSize, vec2(1.0)), uAspectCorrect);
    vec4 base = blurSample(sampleUV, blurR);
    float r = texture2D(uBlurTex, clamp(sampleUV + chromaOffset, 0.0, 1.0)).r;
    float b = texture2D(uBlurTex, clamp(sampleUV - chromaOffset, 0.0, 1.0)).b;
    vec3 color = vec3(r, base.g, b);

    // 低频材质扩散 + 稳定颗粒。
    float micro = sin(gl_FragCoord.x * 0.015 + gl_FragCoord.y * 0.008) * sin(gl_FragCoord.y * 0.012 - gl_FragCoord.x * 0.006);
    color += micro * uNoise * 0.015;
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color += (micro - 0.5) * uNoise * 0.22;

    vec3 grayscale = vec3(luma);
    float saturation = mix(0.45, 0.75, luma);
    color = mix(grayscale, color, saturation);

    vec3 adaptiveTintColor = mix(uTintColor, vec3(0.08, 0.09, 0.11), uDarkness);
    float adaptiveTint = uTintStrength * (1.0 - luma * 0.5);
    color = mix(color, adaptiveTintColor, adaptiveTint);

    float adaptiveFresnel = mix(0.12, 0.06, luma);
    color += uTintColor * fresnel * adaptiveFresnel;
    color *= 0.96 + smoothstep(0.0, 1.0, distN) * 0.04;
    color *= mix(1.08, 0.98, luma) * mix(1.0, 0.82, uDarkness);

    float opticalEdge = pow(edge, 1.35);
    gl_FragColor = vec4(color, opticalEdge * uGlobalAlpha);
}
"""

    private val clearShader = ShaderUtil(VERTEX, CLEAR_FRAG)
    private val tintedShader = ShaderUtil(VERTEX, TINTED_FRAG)

    /** 当前是否应该绘制液态玻璃（BlurSettings 里的独立开关）。 */
    fun isEnabled() = BlurSettings.liquidGlass

    /**
     * 在屏幕矩形 (x, y, w, h)、圆角 [radius]（屏幕像素）处绘制一块液态玻璃。
     * 参数全部从 [BlurSettings] 的 LiquidGlass 设置组读取。
     */
    fun draw(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        if (!BlurSettings.liquidGlass) return

        val px = x.toInt()
        val py = y.toInt()
        val pw = w.toInt()
        val ph = h.toInt()
        if (pw <= 0 || ph <= 0) return

        val tinted = BlurSettings.liquidGlassMode.equals("Tinted", ignoreCase = true)
        val shader = if (tinted) tintedShader else clearShader

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity()
        glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity()
        glDisable(GL_DEPTH_TEST); glDisable(GL_LIGHTING); glEnable(GL_TEXTURE_2D)

        // 抓取该矩形背后的画面（与 BlurUtils 同样的抓屏方式，y 需要翻转）。
        glReadBuffer(GL_BACK)
        val tex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, px, mc.displayHeight - py - ph, pw, ph, 0)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1f, 1f, 1f, 1f)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, tex)

        val minSide = minOf(pw, ph).toFloat().coerceAtLeast(1f)

        shader.init()
        shader.setUniformi("uBlurTex", 0)
        shader.setUniformf("uQuadSize", pw.toFloat(), ph.toFloat())
        shader.setUniformf("uRadius", radius.coerceIn(0f, minSide * 0.5f))
        // 模糊半径以像素传入，着色器内按组件比例换算成各轴 UV。
        shader.setUniformf("uBlurRadius", BlurSettings.lgBlurRadius)
        shader.setUniformf("uAspectCorrect", if (BlurSettings.lgAspectCorrect) 1f else 0f)
        shader.setUniformf("uRefractionPower", BlurSettings.lgRefractionPower)
        shader.setUniformf("uRefractionEdge", BlurSettings.lgRefractionEdge)
        shader.setUniformf("uNoise", BlurSettings.lgNoise)
        shader.setUniformf("uGlobalAlpha", BlurSettings.lgAlpha)
        if (tinted) {
            shader.setUniformf("uTintColor", BlurSettings.lgTintR, BlurSettings.lgTintG, BlurSettings.lgTintB)
            shader.setUniformf("uTintStrength", BlurSettings.lgTintStrength)
            shader.setUniformf("uChromaStrength", BlurSettings.lgChromaStrength)
            shader.setUniformf("uDarkness", BlurSettings.lgDarkness)
        } else {
            shader.setUniformf("uGlowWeight", BlurSettings.lgGlowWeight)
            shader.setUniformf("uGlowBias", BlurSettings.lgGlowBias)
            shader.setUniformf("uGlowEdge0", BlurSettings.lgGlowEdge0)
            shader.setUniformf("uGlowEdge1", BlurSettings.lgGlowEdge1)
        }

        glBegin(GL_QUADS)
        glTexCoord2f(0f, 1f); glVertex2f(px.toFloat(), py.toFloat())
        glTexCoord2f(0f, 0f); glVertex2f(px.toFloat(), (py + ph).toFloat())
        glTexCoord2f(1f, 0f); glVertex2f((px + pw).toFloat(), (py + ph).toFloat())
        glTexCoord2f(1f, 1f); glVertex2f((px + pw).toFloat(), py.toFloat())
        glEnd()

        shader.unload()
        glBindTexture(GL_TEXTURE_2D, 0)
        glColor4f(1f, 1f, 1f, 1f)
        glDeleteTextures(tex)
        glMatrixMode(GL_PROJECTION); glPopMatrix()
        glMatrixMode(GL_MODELVIEW); glPopMatrix()
        glPopAttrib()
    }
}
