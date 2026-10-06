package net.ccbluex.liquidbounce.utils

import javazoom.jl.decoder.JavaLayerException
import javazoom.jl.player.Player
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/**
 * JLayer工具类，用于播放MP3文件
 */
object JLayerUtils {
    // 使用线程池来管理音频播放线程
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "JLayer-Player").apply { isDaemon = true }
    }

    // 当前正在播放的Player实例
    private var currentPlayer: Player? = null
    private var isPlaying: Boolean = false
    private var currentPlaybackThread: Thread? = null

    /**
     * 播放MP3文件
     * @param filePath MP3文件路径
     * @return 是否成功开始播放
     */
    fun playMP3(resourcePath: String) {
        thread(start = true) { // 在新线程中播放，避免阻塞主线程
            try {
                // 使用当前类的ClassLoader加载资源
                val inputStream: InputStream = JLayerUtils::class.java.getResourceAsStream(resourcePath)
                    ?: throw IllegalArgumentException("音频资源未找到: $resourcePath")

                // 使用BufferedInputStream提高性能
                val bufferedStream = BufferedInputStream(inputStream)
                val player = Player(bufferedStream)
                player.play()

                // 播放完成后关闭流
                bufferedStream.close()
                inputStream.close()

            } catch (e: Exception) {
                println("JLayerUtils错误: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 从InputStream播放MP3
     * @param inputStream MP3文件的输入流
     * @return 是否成功开始播放
     */
    fun playStream(inputStream: InputStream): Boolean {
        try {
            // 先停止当前播放的声音
            stop()

            // 创建Player实例
            currentPlayer = Player(inputStream)
            isPlaying = true

            // 在单独的线程中播放音乐
            currentPlaybackThread = Thread {
                try {
                    currentPlayer?.play()
                } catch (e: JavaLayerException) {
                    println("JLayerUtils: 播放过程出错: ${e.message}")
                    e.printStackTrace()
                } finally {
                    synchronized(this) {
                        isPlaying = false
                        currentPlayer = null
                        currentPlaybackThread = null
                        try {
                            inputStream.close()
                        } catch (ignored: Exception) {}
                    }
                }
            }

            // 提交到线程池执行
            executorService.submit(currentPlaybackThread)
            return true
        } catch (e: JavaLayerException) {
            println("JLayerUtils: 创建播放器失败: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        synchronized(this) {
            if (isPlaying && currentPlayer != null) {
                try {
                    currentPlayer?.close()
                } catch (ignored: Exception) {}
                isPlaying = false
                currentPlayer = null
                currentPlaybackThread = null
            }
        }
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean {
        return isPlaying
    }

    /**
     * 关闭播放器并释放资源
     */
    fun shutdown() {
        stop()
        executorService.shutdown()
    }

    /**
     * 播放 WAV 音频，支持变速（倍速会同时改变速度与音调）。
     *
     * 早期版本用 Clip 的 SAMPLE_RATE 控制来变速，但很多声卡的 Clip 并不暴露该控制，
     * 结果就是"变速无效果 / 音调不变"。这里改为自己重采样后交给 SourceDataLine 播放，
     * 不依赖任何可选控制，稳定生效。
     *
     * @param resourcePath classpath 资源路径，例如 /assets/minecraft/lizz/sounds/bingbingbing.wav
     * @param speed 播放速度倍率，1.0 为原速
     * @param volume 音量，0.0 ~ 1.0
     */
    fun playWav(resourcePath: String, speed: Float = 1f, volume: Float = 1f) {
        thread(start = true) {
            var line: SourceDataLine? = null
            try {
                val raw = JLayerUtils::class.java.getResourceAsStream(resourcePath)
                    ?: throw IllegalArgumentException("音频资源未找到: $resourcePath")

                // 1. 统一解码成 16-bit PCM（小端），后续按字节重采样
                var decoded: AudioInputStream = AudioSystem.getAudioInputStream(BufferedInputStream(raw))
                val base = decoded.format
                val pcmFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.sampleRate,
                    16,
                    base.channels,
                    base.channels * 2,
                    base.sampleRate,
                    false
                )
                if (!decoded.format.matches(pcmFormat)) {
                    decoded = AudioSystem.getAudioInputStream(pcmFormat, decoded)
                }

                val srcBytes = decoded.readBytes()
                val frameSize = pcmFormat.channels * 2
                val srcFrames = srcBytes.size / frameSize
                if (srcFrames <= 0) return@thread

                // 2. 线性重采样：speed > 1 时样本变少 → 更快且音调更高（真正的倍速）
                val sp = speed.coerceIn(0.1f, 8f)
                val dstFrames = (srcFrames / sp).toInt().coerceAtLeast(1)
                val gain = volume.coerceIn(0f, 1f)
                val out = ByteArray(dstFrames * frameSize)
                for (i in 0 until dstFrames) {
                    val pos = i * sp
                    val i0 = pos.toInt().coerceAtMost(srcFrames - 1)
                    val i1 = (i0 + 1).coerceAtMost(srcFrames - 1)
                    val frac = pos - i0
                    val off0 = i0 * frameSize
                    val off1 = i1 * frameSize
                    val offOut = i * frameSize
                    for (c in 0 until pcmFormat.channels) {
                        val s0 = readShortLE(srcBytes, off0 + c * 2)
                        val s1 = readShortLE(srcBytes, off1 + c * 2)
                        val v = (s0 + (s1 - s0) * frac) * gain
                        val iv = v.toInt().coerceIn(-32768, 32767)
                        out[offOut + c * 2] = (iv and 0xFF).toByte()
                        out[offOut + c * 2 + 1] = ((iv shr 8) and 0xFF).toByte()
                    }
                }

                // 3. 按原采样率播放重采样后的数据
                val info = DataLine.Info(SourceDataLine::class.java, pcmFormat)
                val dataLine = AudioSystem.getLine(info) as SourceDataLine
                line = dataLine
                dataLine.open(pcmFormat)
                dataLine.start()
                dataLine.write(out, 0, out.size)
                dataLine.drain()
                dataLine.stop()
                dataLine.close()
                line = null
            } catch (e: Exception) {
                println("JLayerUtils错误(playWav): ${e.message}")
                e.printStackTrace()
                try {
                    line?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /** 读取小端 16-bit PCM 样本（带符号）。 */
    private fun readShortLE(bytes: ByteArray, offset: Int): Int {
        val lo = bytes[offset].toInt() and 0xFF
        val hi = bytes[offset + 1].toInt()
        return (hi shl 8) or lo
    }
}