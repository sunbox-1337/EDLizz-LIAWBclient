package net.ccbluex.liquidbounce.utils.render.music

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.jar.JarFile

/**
 * 一首歌：要么来自磁盘文件，要么来自 classpath 里的内置资源。
 */
class MusicTrack(
    val title: String,
    val file: File? = null,
    val resource: String? = null,
) {
    val displayName: String get() = title

    fun openStream(): InputStream =
        file?.let { BufferedInputStream(it.inputStream()) }
            ?: MusicLibrary.openResource(resource!!)
            ?: throw IllegalStateException("无法打开音频: $title")

    fun readCover(): BufferedImage? = try {
        file?.let { Id3CoverArt.readCover(it) }
            ?: resource?.let { res ->
                MusicLibrary.openResource(res)?.use { Id3CoverArt.readFromStream(it) }
            }
    } catch (_: Exception) {
        null
    }

    override fun toString() = title
}

/**
 * 扫描音乐：外部文件夹 + classpath 内置资源目录。
 */
object MusicLibrary {

    private val EXTENSIONS = arrayOf(".mp3", ".wav", ".ogg", ".flac", ".m4a")

    /** classpath 里内置音乐的目录前缀，对应 src/main/resources/assets/minecraft/lizz/music */
    const val RESOURCE_DIR = "/assets/minecraft/lizz/music"

    fun isAudio(name: String): Boolean {
        val lower = name.lowercase()
        return EXTENSIONS.any { lower.endsWith(it) }
    }

    fun openResource(path: String): InputStream? {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return MusicLibrary::class.java.getResourceAsStream(normalized)
    }

    /** 默认外部音乐目录：<游戏目录>/lizz/music */
    fun defaultFolder(gameDir: File): File = File(gameDir, "${CLIENT_NAME.lowercase()}/music")

    /** 扫描外部文件夹（不存在则创建）。 */
    fun scanFolder(folder: File): List<MusicTrack> {
        if (!folder.exists()) folder.mkdirs()
        val files = folder.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { it.isFile && isAudio(it.name) }
            .sortedBy { it.name.lowercase() }
            .map { MusicTrack(stripExtension(it.name), file = it) }
            .toList()
    }

    /** 扫描 classpath 内置资源目录（开发环境下是 file:，打包后是 jar:）。 */
    fun scanResources(): List<MusicTrack> {
        val names = mutableListOf<String>()
        try {
            val url = MusicLibrary::class.java.getResource(RESOURCE_DIR) ?: return emptyList()
            when (url.protocol) {
                "file" -> {
                    File(url.toURI()).listFiles()?.forEach {
                        if (it.isFile && isAudio(it.name)) names += "$RESOURCE_DIR/${it.name}"
                    }
                }

                "jar" -> {
                    val path = url.path
                    val jarPath = URLDecoder.decode(path.substring(5, path.indexOf('!')), "UTF-8")
                    JarFile(File(jarPath)).use { jar ->
                        val entries = jar.entries()
                        val prefix = "${RESOURCE_DIR.trimStart('/')}/"
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory && entry.name.startsWith(prefix) && isAudio(entry.name)) {
                                names += "/${entry.name}"
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 没有内置音乐目录就当作空
        }
        return names.sorted().map { MusicTrack(stripExtension(File(it).name), resource = it) }
    }

    private fun stripExtension(name: String): String =
        name.substringBeforeLast('.', name)
}
