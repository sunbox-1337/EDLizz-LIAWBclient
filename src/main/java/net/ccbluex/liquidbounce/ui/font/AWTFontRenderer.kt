/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.font

import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.kotlin.LruCache
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.GlStateManager.bindTexture
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A memory-optimized bitmap-based font renderer using AWT [Font].
 *
 * @author opZywl
 */
@SideOnly(Side.CLIENT)
class AWTFontRenderer(
    val font: Font,
    startChar: Int = 0,
    private val stopChar: Int = 255,
    private val loadingScreen: Boolean = false,
    /**
     * 是否启用动态字形（中文等图集外字符）。
     * 只有中文字体才开：开启后会额外占一张 1024×1024 图集，
     * 普通字体保持原来的小图集，不增加显存。
     */
    private val dynamicGlyphs: Boolean = false
) : MinecraftInstance {

    companion object : Listenable {
        var assumeNonVolatile: Boolean = false

        /** All active font renderers (for GC tasks). */
        private val activeFontRenderers = mutableListOf<AWTFontRenderer>()

        /**
         * Runs a block with [assumeNonVolatile] = true, then restores it.
         */
        inline fun assumeNonVolatile(block: () -> Unit) {
            assumeNonVolatile = true
            try {
                block()
            } finally {
                assumeNonVolatile = false
            }
        }

        // Garbage collection constants
        private const val GC_TICKS = 600                    // Do GC every 600 frames
        private const val CACHED_FONT_REMOVAL_TIME = 30000L // 30s time-based eviction
        private const val MAX_CACHED_STRINGS = 255          // LRU cache size limit

        /** 图集宽度：预烘焙 + 动态中文字形共用一张，固定宽度保证 UV 稳定 */
        private const val ATLAS_SIZE = 2048

        /** 动态字形预留的高度带（像素），用完就回退原版字体 */
        private const val DYNAMIC_BAND = 512

        private const val GLYPH_GAP = 1

        private var gcTicks = 0

        /**
         * Should be called each frame or so. Every 600 frames, we run garbage collection
         * on every active font renderer.
         */
        private val onRender2D = handler<Render2DEvent>(priority = Byte.MIN_VALUE) {
            if (++gcTicks > GC_TICKS) {
                activeFontRenderers.forEach { it.collectGarbage() }
                gcTicks = 0
            }
        }
    }

    /**
     * Info about each character's location in the texture.
     */
    private data class CharLocation(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    /**
     * Stores a compiled display list for a specific string, plus a "last usage" timestamp.
     */
    private data class CachedFont(
        val displayList: Int,
        var lastUsage: Long,
        var deleted: Boolean = false
    )

    private val charLocations = arrayOfNulls<CharLocation>(stopChar)

    /**
     * 预烘焙范围之外的字形（中文等）按需画进同一张图集的空闲区域。
     * 图集固定 2048×2048，所以已有字符的 UV 不会因为新增字形而失效，
     * 已经编译好的字符串 display list 也不用重建。
     */
    private val dynamicLocations = HashMap<Int, CharLocation>()

    private var dynamicX = 0
    private var dynamicY = 0
    private var dynamicRowHeight = 0


    /**
     * We store strings in an LRU-like map with time-based eviction:
     * - If the size exceeds [MAX_CACHED_STRINGS], we remove the eldest entry.
     * - If an entry hasn't been used for 30s, we remove it.
     */
    private val cachedStringFonts = LruCache<String, CachedFont>(MAX_CACHED_STRINGS)

    /**
     * We don't do GC for the cached widths because they are constant for each string
     */
    private val cachedStringWidths = LruCache<String, Int>(MAX_CACHED_STRINGS)

    private var textureID: Int = -1
    private var textureWidth: Int = 0
    private var textureHeight: Int = 0
    private var fontHeight: Int = -1

    /**
     * Typical "font height" to use in layout, derived from [fontHeight].
     * Adjust as needed.
     */
    val height: Int
        get() = (fontHeight - 8) / 2

    init {
        // Generate the large bitmap with all glyphs
        renderBitmap(startChar, stopChar)

        // Register for GC tasks
        activeFontRenderers += this
    }

    /**
     * Draw [text] at ([x], [y]) with [color]. Scales by 0.25 => typical UI text size.
     */
    fun drawString(text: String, x: Double, y: Double, color: Int) {
        // 图集外的字符（中文等）先补进图集，否则会掉回原版字体
        ensureGlyphs(text)

        // Scale down => 0.25 => then everything is "2 * x" in real coords
        glPushMatrix()
        glScaled(0.25, 0.25, 0.25)

        // Shift to final position
        glTranslated(x * 2.0, y * 2.0 - 2.0, 0.0)

        if (loadingScreen) {
            glBindTexture(GL_TEXTURE_2D, textureID)
        } else {
            bindTexture(textureID)
        }

        // Extract ARGB
        val (alpha, red, green, blue) = ColorUtils.unpackARGBFloatValue(color)
        glColor4f(red, green, blue, alpha)

        // 1) If we've cached this text, just call the display list
        val cached = cachedStringFonts[text]
        if (cached != null) {
            glCallList(cached.displayList)
            cached.lastUsage = System.currentTimeMillis()
            glPopMatrix()
            return
        }

        // 2) Not cached => build it now
        var listID = -1
        if (assumeNonVolatile) {
            listID = glGenLists(1)
            glNewList(listID, GL_COMPILE_AND_EXECUTE)
        }

        glBegin(GL_QUADS)

        var currX = 0f
        var fallbackWidth = 0f // fallback for MC glyphs

        for (char in text) {
            val loc = locate(char.code)
            if (loc == null) {
                // Fallback => break quads, draw with MC font
                glEnd()
                GlStateManager.resetColor()

                glPushMatrix()

                // Because we scaled by 0.25 => revert
                val rev = 4.0f
                glScalef(rev, rev, rev)

                // Then scale by (font.size / 32.0)
                val scale = font.size / 32.0f
                glScalef(scale, scale, 1.0f)

                mc.fontRendererObj.posY = 1.0f
                mc.fontRendererObj.posX = (currX / rev) + fallbackWidth

                val fallbackW = mc.fontRendererObj.renderUnicodeChar(char, false).coerceAtLeast(0f)
                fallbackWidth += fallbackW

                if (loadingScreen) {
                    glBindTexture(GL_TEXTURE_2D, textureID)
                } else {
                    bindTexture(textureID)
                }
                glPopMatrix()
                glBegin(GL_QUADS)
            } else {
                drawChar(loc, currX + (fallbackWidth * 4f), 0f)
                currX += (loc.width - 8f)
            }
        }

        glEnd()

        if (assumeNonVolatile && listID >= 0) {
            // Insert into our LRU + time-based map
            cachedStringFonts[text] = CachedFont(listID, System.currentTimeMillis())
            glEndList()
        }

        glPopMatrix()
    }

    /**
     * Returns the pixel-width of [text]. If a character is not in [charLocations],
     * we fallback to MC's font (approx).
     */
    fun getStringWidth(text: String): Int = cachedStringWidths.getOrPut(text) {
        var myWidth = 0
        var fallbackWidth = 0f
        val fallbackScale = font.size / 32f

        for (char in text) {
            val loc = locate(char.code) ?: ensureGlyph(char.code)
            if (loc == null) {
                val w = mc.fontRendererObj.getCharWidth(char)
                fallbackWidth += ((w + 8) * fallbackScale).coerceAtLeast(0f)
            } else {
                myWidth += (loc.width - 8)
            }
        }

        (myWidth / 2) + fallbackWidth.roundToInt()
    }

    /**
     * Disposes of this font, removing it from the list and freeing texture memory.
     */
    fun dispose() {
        if (textureID != -1) {
            glDeleteTextures(textureID)
            textureID = -1
        }
        activeFontRenderers.remove(this)
    }

    /** If the user forgets to call [dispose], still free resources. */
    protected fun finalize() {
        dispose()
    }

    private fun drawChar(loc: CharLocation, x: Float, y: Float) {
        val w = loc.width.toFloat()
        val h = loc.height.toFloat()

        val u = loc.x.toFloat() / textureWidth
        val v = loc.y.toFloat() / textureHeight
        val uw = w / textureWidth
        val vh = h / textureHeight

        // 4 corners
        glTexCoord2f(u, v)
        glVertex2f(x, y)

        glTexCoord2f(u, v + vh)
        glVertex2f(x, y + h)

        glTexCoord2f(u + uw, v + vh)
        glVertex2f(x + w, y + h)

        glTexCoord2f(u + uw, v)
        glVertex2f(x + w, y)
    }

    /**
     * Builds the single large texture with [startChar] to [stopChar] glyphs.
     */
    private fun renderBitmap(startChar: Int, stopChar: Int) {
        val fontImages = arrayOfNulls<BufferedImage>(stopChar)

        var rowHeight = 0
        var charX = 0
        var charY = 0

        for (charCode in startChar until stopChar) {
            val charImg = drawCharToImage(charCode.toChar())
            val cw = charImg.width
            val ch = charImg.height

            if (ch > fontHeight) {
                fontHeight = ch
            }

            val loc = CharLocation(charX, charY, cw, ch)
            charLocations[charCode] = loc
            fontImages[charCode] = charImg

            charX += cw
            if (cw > 0 && ch > rowHeight) {
                rowHeight = ch
            }
            // If exceeding ~2k width, break line
            if (charX > 2048) {
                if (charX > textureWidth)
                    textureWidth = charX
                charX = 0
                charY += rowHeight
                rowHeight = 0
            }
        }
        // finalize
        textureWidth = max(textureWidth, charX)
        textureHeight = charY + rowHeight

        // Big final image
        val bigImage = BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB)
        val g = bigImage.createGraphics()
        g.font = font
        g.color = Color(255, 255, 255, 0) // transparent
        g.fillRect(0, 0, textureWidth, textureHeight)
        g.color = Color.WHITE

        // Draw each char subimage
        for (charCode in startChar until stopChar) {
            val subImg = fontImages[charCode] ?: continue
            val loc = charLocations[charCode] ?: continue
            g.drawImage(subImg, loc.x, loc.y, null)
        }

        // Upload to GPU
        if (!dynamicGlyphs) {
            textureID = TextureUtil.uploadTextureImageAllocate(TextureUtil.glGenTextures(), bigImage, true, true)
            return
        }

        // 中文等动态字形：预烘焙字形放左上，下面留一条固定高度的空间按需填充
        val atlasWidth = maxOf(textureWidth, ATLAS_SIZE)
        val atlasHeight = textureHeight + DYNAMIC_BAND

        val canvas = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        val canvasGraphics = canvas.createGraphics()
        canvasGraphics.drawImage(bigImage, 0, 0, null)
        canvasGraphics.dispose()

        textureID = TextureUtil.uploadTextureImageAllocate(TextureUtil.glGenTextures(), canvas, true, true)

        // 动态分配从预烘焙区域下方开始
        dynamicY = textureHeight + GLYPH_GAP
        dynamicX = 0
        dynamicRowHeight = 0

        // UV 分母改用图集尺寸；已有字形坐标不用动
        textureWidth = atlasWidth
        textureHeight = atlasHeight
    }

    /**
     * Draws a single char [c] into a small [BufferedImage].
     */
    private fun drawCharToImage(c: Char): BufferedImage {
        // measure to get width/height
        val measureImg = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val measureG = measureImg.createGraphics()
        measureG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        measureG.font = font

        val fm = measureG.fontMetrics
        var w = fm.charWidth(c) + 8
        if (w <= 0) w = 7
        var h = fm.height + 3
        if (h <= 0) h = font.size

        // real
        val charImg = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2d = charImg.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.font = font
        g2d.color = Color.WHITE
        g2d.drawString(c.toString(), 3, 1 + fm.ascent)
        return charImg
    }

    /** 预烘焙图集或动态图集里的字形位置 */
    private fun locate(charCode: Int): CharLocation? =
        charLocations.getOrNull(charCode) ?: dynamicLocations[charCode]

    /** 整串里只要有一个没烘焙的字符，就补一遍图集 */
    private fun ensureGlyphs(text: String) {
        if (!dynamicGlyphs) return

        var missing = false
        for (char in text) {
            if (char.code >= stopChar && !dynamicLocations.containsKey(char.code)) {
                missing = true
                break
            }
        }
        if (!missing) return

        for (char in text) {
            if (char.code >= stopChar) ensureGlyph(char.code)
        }
    }

    /** 图集满了之后不再重试，避免每帧重复渲染失败的字形 */
    private val failedGlyphs = HashSet<Int>()

    /**
     * 预烘焙范围外的字符（中文等）按需生成字形，写进图集空白区域。
     * 图集满了返回 null，调用方回退到原版字体。
     */
    private fun ensureGlyph(charCode: Int): CharLocation? {
        if (!dynamicGlyphs) return null

        dynamicLocations[charCode]?.let { return it }
        if (failedGlyphs.contains(charCode)) return null

        val image = drawCharToImage(charCode.toChar())
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) {
            failedGlyphs += charCode
            return null
        }

        if (dynamicX + w + GLYPH_GAP > textureWidth) {
            dynamicX = 0
            dynamicY += dynamicRowHeight + GLYPH_GAP
            dynamicRowHeight = 0
        }
        if (dynamicY + h + GLYPH_GAP > textureHeight) {
            failedGlyphs += charCode
            return null
        }

        val location = CharLocation(dynamicX, dynamicY, w, h)
        uploadSubImage(image, dynamicX, dynamicY)

        dynamicLocations[charCode] = location
        dynamicX += w + GLYPH_GAP
        if (h > dynamicRowHeight) dynamicRowHeight = h

        return location
    }

    /** 只把这一小块字形上传到图集的对应位置 */
    private fun uploadSubImage(image: BufferedImage, x: Int, y: Int) {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getRGB(0, 0, w, h, pixels, 0, w)

        val buffer = BufferUtils.createByteBuffer(w * h * 4)
        for (pixel in pixels) {
            buffer.put((pixel shr 16).toByte())
            buffer.put((pixel shr 8).toByte())
            buffer.put(pixel.toByte())
            buffer.put((pixel ushr 24).toByte())
        }
        buffer.flip()

        val previous = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, textureID)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        glBindTexture(GL_TEXTURE_2D, previous)
    }

    /**
     * Removes unused / old strings from the cache. Called by global GC every so often.
     */
    private fun collectGarbage() {
        val now = System.currentTimeMillis()

        with(cachedStringFonts.entries.iterator()) {
            while (hasNext()) {
                val cached = next().value
                if (!cached.deleted && (now - cached.lastUsage) > CACHED_FONT_REMOVAL_TIME) {
                    glDeleteLists(cached.displayList, 1)
                    cached.deleted = true
                    remove()
                }
            }
        }
    }
}
