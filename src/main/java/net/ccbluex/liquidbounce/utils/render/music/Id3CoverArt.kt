package net.ccbluex.liquidbounce.utils.render.music

import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * 从 MP3 里读出内嵌封面（ID3v2 的 APIC 帧）。
 *
 * 只做最必要的解析：跳到 ID3v2 标签、遍历帧、找到 APIC、取出图片字节交给 [ImageIO] 解码。
 * 如果文件里没有内嵌封面，再尝试同目录下的 cover.jpg / folder.jpg 等。
 */
object Id3CoverArt {

    private val LOCAL_COVERS = arrayOf(
        "cover.jpg", "cover.png", "cover.jpeg",
        "folder.jpg", "folder.png",
        "front.jpg", "front.png",
        "album.jpg", "albumart.jpg"
    )

    /** 读取封面：先解析 ID3v2 内嵌图，失败则找同目录封面图片。 */
    fun readCover(file: File): BufferedImage? {
        try {
            BufferedInputStream(file.inputStream(), 1 shl 16).use { input ->
                readFromStream(input)?.let { return it }
            }
        } catch (_: Exception) {
            // 忽略，走下面的同目录封面
        }

        val dir = file.parentFile ?: return null
        for (name in LOCAL_COVERS) {
            val cover = File(dir, name)
            if (cover.isFile) {
                try {
                    return ImageIO.read(cover)
                } catch (_: Exception) {
                    // 换下一个
                }
            }
        }
        return null
    }

    /** 从流里解析 ID3v2 的 APIC 封面。 */
    fun readFromStream(raw: InputStream): BufferedImage? {
        val input = if (raw is BufferedInputStream) raw else BufferedInputStream(raw)
        try {
            val header = ByteArray(10)
            var read = 0
            while (read < 10) {
                val r = input.read(header, read, 10 - read)
                if (r < 0) return null
                read += r
            }

            if (header[0].toInt().toChar() != 'I' ||
                header[1].toInt().toChar() != 'D' ||
                header[2].toInt().toChar() != '3'
            ) return null

            val major = header[3].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            val tagSize = synchsafe(header, 6)
            if (tagSize <= 0) return null

            val tag = ByteArray(tagSize)
            var offset = 0
            while (offset < tagSize) {
                val r = input.read(tag, offset, tagSize - offset)
                if (r < 0) break
                offset += r
            }

            var pos = 0
            // 跳过扩展头
            if (flags and 0x40 != 0) {
                pos += if (major >= 4) synchsafe(tag, 0) else beInt(tag, 0) + 4
            }

            while (pos + 10 <= tag.size) {
                val id = String(tag, pos, 4, Charsets.ISO_8859_1)
                if (id[0] == '\u0000') break

                val frameSize = if (major >= 4) synchsafe(tag, pos + 4) else beInt(tag, pos + 4)
                if (frameSize <= 0 || pos + 10 + frameSize > tag.size) break

                if (id == "APIC") {
                    return parseApic(tag, pos + 10, frameSize)
                }
                pos += 10 + frameSize
            }
        } catch (_: Exception) {
            // 解析失败视为没有封面
        }
        return null
    }

    private fun parseApic(tag: ByteArray, start: Int, size: Int): BufferedImage? {
        var i = start
        val end = start + size
        if (i >= end) return null

        val encoding = tag[i].toInt() and 0xFF
        i++

        // MIME 类型，以 0 结尾
        while (i < end && tag[i].toInt() != 0) i++
        i++

        // 图片类型（1 字节）
        if (i >= end) return null
        i++

        // 描述，以 0 结尾（UTF-16 编码是双字节 0 结尾）
        if (encoding == 1 || encoding == 2) {
            while (i + 1 < end && !(tag[i].toInt() == 0 && tag[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < end && tag[i].toInt() != 0) i++
            i++
        }

        if (i >= end) return null

        val imageBytes = tag.copyOfRange(i, end)
        return try {
            ImageIO.read(ByteArrayInputStream(imageBytes))
        } catch (_: Exception) {
            null
        }
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
        ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
        ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
        (bytes[offset + 3].toInt() and 0x7F)

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)
}
