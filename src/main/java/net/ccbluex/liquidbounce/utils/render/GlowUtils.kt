package net.ccbluex.liquidbounce.utils

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.TextureUtil
import org.lwjgl.opengl.GL11
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import com.jhlabs.image.GaussianFilter

object GlowUtils {
    private val shadowCache = HashMap<Long, Int>()

    /**
     * 绘制带模糊阴影的矩形
     * @param cornerRadius 圆角半径
     * @param onlyBorder   是否只绘制边框（模糊前画边框）
     * @param mask         是否启用模糊后裁切（内部透明，只保留边缘阴影）
     */
    @JvmOverloads
    fun drawGlow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        blurRadius: Int,
        color: Color,
        cornerRadius: Float = 0f,
        onlyBorder: Boolean = false,
        mask: Boolean = false
    ) {
        if (width <= 0f || height <= 0f) return
        GL11.glPushMatrix()
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01f)

        val _X = x - blurRadius
        val _Y = y - blurRadius

        // 画布按 1× 逻辑像素建立，形状用浮点坐标 + 抗锯齿填充（覆盖率即亚像素精度），
        // 消除整数取整造成的 ≤1px 错位；贴回时按同尺寸、浮点位置绘制，形状不拉伸也不偏移。
        val imageWidth = Math.ceil((width + blurRadius * 2f).toDouble()).toInt().coerceAtLeast(1)
        val imageHeight = Math.ceil((height + blurRadius * 2f).toDouble()).toInt().coerceAtLeast(1)

        val identifier = width.toRawBits().toLong() * 31L +
                height.toRawBits().toLong() * 37L +
                color.hashCode().toLong() * 41L +
                blurRadius.toLong() * 43L +
                cornerRadius.toRawBits().toLong() * 47L +
                (if (onlyBorder) 53L else 0L) +
                (if (mask) 59L else 0L)

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_ALPHA_TEST)
        GlStateManager.enableBlend()

        var texId = shadowCache[identifier]
        if (texId == null) {
            val original = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE)
            val g2 = original.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color

            // 形状用浮点坐标，位置/尺寸与传入矩形严格一致（画布内偏移 = blurRadius）
            val shapeX = blurRadius.toFloat()
            val shapeY = blurRadius.toFloat()

            if (onlyBorder) {
                g2.stroke = BasicStroke(Math.max(1f, blurRadius * 0.5f))
                if (cornerRadius > 0f) {
                    val d = cornerRadius * 2f
                    g2.draw(RoundRectangle2D.Float(shapeX, shapeY, width, height, d, d))
                } else {
                    g2.draw(Rectangle2D.Float(shapeX, shapeY, width, height))
                }
            } else {
                if (cornerRadius > 0f) {
                    val d = cornerRadius * 2f
                    g2.fill(RoundRectangle2D.Float(shapeX, shapeY, width, height, d, d))
                } else {
                    g2.fill(Rectangle2D.Float(shapeX, shapeY, width, height))
                }
            }
            g2.dispose()

            val op = GaussianFilter(blurRadius.toFloat())
            var blurred = op.filter(original, null)

            // 模糊后裁切：将原始矩形区域 alpha 置零
            if (mask && !onlyBorder) {
                val masked = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE)
                val mg = masked.createGraphics()
                mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                mg.composite = AlphaComposite.Src
                mg.drawImage(blurred, 0, 0, null)

                mg.composite = AlphaComposite.DstOut
                mg.color = Color(0, 0, 0, 255)
                if (cornerRadius > 0f) {
                    val d = cornerRadius * 2f
                    mg.fill(RoundRectangle2D.Float(shapeX, shapeY, width, height, d, d))
                } else {
                    mg.fill(Rectangle2D.Float(shapeX, shapeY, width, height))
                }
                mg.dispose()
                blurred = masked
            }

            texId = TextureUtil.uploadTextureImageAllocate(TextureUtil.glGenTextures(), blurred, true, false)
            shadowCache[identifier] = texId!!
        }

        GlStateManager.bindTexture(texId)
        GL11.glColor4f(1f, 1f, 1f, 1f)

        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(_X, _Y)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(_X, _Y + imageHeight)
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f(_X + imageWidth, _Y + imageHeight)
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f(_X + imageWidth, _Y)
        GL11.glEnd()

        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.resetColor()
        GL11.glEnable(GL11.GL_CULL_FACE)
        GL11.glPopMatrix()
    }

    /** 径向泛光复用的纹理 id，避免每帧新建纹理。 */
    private var radialGlowTexId = 0

    /**
     * 从 (glowX, glowY) 向外扩散的径向泛光，并按 [cornerRadius] 的圆角矩形裁切：
     * 圆角以外的像素直接不存在，因此不会在按钮圆角处漏光。坐标均为 GUI 坐标。
     *
     * @param glowRadius 泛光自中心向外扩散的半径
     */
    @JvmOverloads
    fun drawRoundedRadialGlow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        glowX: Float,
        glowY: Float,
        glowRadius: Float,
        color: Color,
        cornerRadius: Float = 0f
    ) {
        if (width <= 0f || height <= 0f || glowRadius <= 0f || color.alpha <= 0) return

        val imageWidth = Math.ceil(width.toDouble()).toInt().coerceAtLeast(1)
        val imageHeight = Math.ceil(height.toDouble()).toInt().coerceAtLeast(1)

        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val fadeColor = Color(color.red, color.green, color.blue, (color.alpha * 0.5f).toInt().coerceIn(0, 255))
        val paint = RadialGradientPaint(
            Point2D.Float(glowX - x, glowY - y),
            glowRadius,
            floatArrayOf(0f, 0.5f, 1f),
            arrayOf(color, fadeColor, Color(color.red, color.green, color.blue, 0))
        )
        g2.paint = paint
        g2.fillRect(0, 0, imageWidth, imageHeight)

        // 圆角裁切：圆角之外一律抹掉
        g2.composite = AlphaComposite.DstIn
        g2.color = Color(0, 0, 0, 255)
        if (cornerRadius > 0f) {
            val d = cornerRadius * 2f
            g2.fill(RoundRectangle2D.Float(0f, 0f, width, height, d, d))
        } else {
            g2.fill(Rectangle2D.Float(0f, 0f, width, height))
        }
        g2.dispose()

        GL11.glPushMatrix()
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01f)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_ALPHA_TEST)
        GlStateManager.enableBlend()

        if (radialGlowTexId == 0) {
            radialGlowTexId = TextureUtil.glGenTextures()
        }
        TextureUtil.uploadTextureImage(radialGlowTexId, image)
        // 画布是 1× 逻辑像素，放大到屏幕需要线性过滤才是平滑渐变
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)

        GlStateManager.bindTexture(radialGlowTexId)
        GL11.glColor4f(1f, 1f, 1f, 1f)

        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(x, y)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(x, y + imageHeight)
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f(x + imageWidth, y + imageHeight)
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f(x + imageWidth, y)
        GL11.glEnd()

        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.resetColor()
        GL11.glEnable(GL11.GL_CULL_FACE)
        GL11.glPopMatrix()
    }

    /**
     * 绘制由多个矩形拼成的整体阴影。
     *
     * 与逐矩形调用 [drawGlow] 不同，这里把所有矩形一次性画进同一张画布再统一模糊，
     * 相邻矩形之间不会产生接缝，因此不会出现逐行阴影在上下方向互相渗透（污染）的问题。
     *
     * @param rects 每个元素为 floatArrayOf(left, top, right, bottom)，使用当前 GL 正交坐标
     * @param blurRadius 模糊半径
     * @param color 阴影颜色
     * @param mask 是否挖空矩形自身，只保留边缘发光
     */
    @JvmOverloads
    fun drawCompositeGlow(
        rects: List<FloatArray>,
        blurRadius: Int,
        color: Color,
        mask: Boolean = false,
        cornerRadius: Float = 0f
    ) {
        if (rects.isEmpty()) return

        // 计算所有矩形的并集边界
        var uLeft = Float.MAX_VALUE
        var uTop = Float.MAX_VALUE
        var uRight = -Float.MAX_VALUE
        var uBottom = -Float.MAX_VALUE
        for (r in rects) {
            if (r[2] <= r[0] || r[3] <= r[1]) continue
            uLeft = minOf(uLeft, r[0])
            uTop = minOf(uTop, r[1])
            uRight = maxOf(uRight, r[2])
            uBottom = maxOf(uBottom, r[3])
        }
        if (uRight <= uLeft || uBottom <= uTop) return

        // 画布按 1× 逻辑像素建立，矩形用浮点坐标 + 抗锯齿填充（覆盖率即亚像素精度），
        // 消除整数取整造成的 ≤1px 错位；模糊核保持 blurRadius、画布不放大，
        // 单次重建的卷积量只剩原来的百分之一量级（原来 4× 画布 × 4× 半径 = 64 倍）。
        val blur = blurRadius
        val extentW = uRight - uLeft + blurRadius * 2f
        val extentH = uBottom - uTop + blurRadius * 2f
        val imageWidth = Math.ceil(extentW.toDouble()).toInt().coerceAtLeast(1)
        val imageHeight = Math.ceil(extentH.toDouble()).toInt().coerceAtLeast(1)

        // 缓存键：包含所有矩形的相对位置
        var identifier = 17L
        identifier = identifier * 31 + imageWidth
        identifier = identifier * 31 + imageHeight
        identifier = identifier * 31 + color.hashCode()
        identifier = identifier * 31 + blurRadius
        identifier = identifier * 31 + (if (mask) 59 else 0)
        identifier = identifier * 31 + cornerRadius.toRawBits()
        for (r in rects) {
            identifier = identifier * 31 + (r[0] - uLeft).toRawBits()
            identifier = identifier * 31 + (r[1] - uTop).toRawBits()
            identifier = identifier * 31 + (r[2] - uLeft).toRawBits()
            identifier = identifier * 31 + (r[3] - uTop).toRawBits()
        }

        GL11.glPushMatrix()
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01f)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_ALPHA_TEST)
        GlStateManager.enableBlend()

        var texId = shadowCache[identifier]
        if (texId == null) {
            val original = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE)
            val g2 = original.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            // 所有矩形合成一条路径一次性填充：共享边不会因为分别填充叠加出半透明接缝
            val shape = Path2D.Float()
            for (r in rects) {
                val sw = r[2] - r[0]
                val sh = r[3] - r[1]
                if (sw <= 0f || sh <= 0f) continue
                val fillX = blur + (r[0] - uLeft)
                val fillY = blur + (r[1] - uTop)
                if (cornerRadius > 0f) {
                    val d = cornerRadius * 2f
                    shape.append(RoundRectangle2D.Float(fillX, fillY, sw, sh, d, d), false)
                } else {
                    shape.append(Rectangle2D.Float(fillX, fillY, sw, sh), false)
                }
            }
            g2.fill(shape)
            g2.dispose()

            val op = GaussianFilter(blur.toFloat())
            var blurred = op.filter(original, null)

            if (mask) {
                val masked = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE)
                val mg = masked.createGraphics()
                mg.composite = AlphaComposite.Src
                mg.drawImage(blurred, 0, 0, null)

                mg.composite = AlphaComposite.DstOut
                mg.color = Color(0, 0, 0, 255)
                val cutout = Path2D.Float()
                for (r in rects) {
                    val sw = r[2] - r[0]
                    val sh = r[3] - r[1]
                    if (sw <= 0f || sh <= 0f) continue
                    val maskX = blur + (r[0] - uLeft)
                    val maskY = blur + (r[1] - uTop)
                    if (cornerRadius > 0f) {
                        val d = cornerRadius * 2f
                        cutout.append(RoundRectangle2D.Float(maskX, maskY, sw, sh, d, d), false)
                    } else {
                        cutout.append(Rectangle2D.Float(maskX, maskY, sw, sh), false)
                    }
                }
                mg.fill(cutout)
                mg.dispose()
                blurred = masked
            }

            texId = TextureUtil.uploadTextureImageAllocate(TextureUtil.glGenTextures(), blurred, true, false)
            shadowCache[identifier] = texId!!
        }

        GlStateManager.bindTexture(texId)
        GL11.glColor4f(1f, 1f, 1f, 1f)

        // 纹理 1 像素 = 1 逻辑单位贴回，形状不会被拉伸，位置不会偏移
        val drawX = uLeft - blurRadius
        val drawY = uTop - blurRadius
        val drawW = imageWidth.toFloat()
        val drawH = imageHeight.toFloat()

        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(drawX, drawY)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(drawX, drawY + drawH)
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f(drawX + drawW, drawY + drawH)
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f(drawX + drawW, drawY)
        GL11.glEnd()

        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.resetColor()
        GL11.glEnable(GL11.GL_CULL_FACE)
        GL11.glPopMatrix()
    }
}