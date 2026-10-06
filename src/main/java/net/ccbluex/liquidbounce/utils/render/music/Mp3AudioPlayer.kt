package net.ccbluex.liquidbounce.utils.render.music

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/**
 * 可控的 MP3 播放器。
 *
 * 没有直接用 JLayer 的高层 [javazoom.jl.player.Player]（它拿不到播放进度、也无法暂停），
 * 而是用它的低层 API（[Bitstream] + [Decoder]）自己解码成 PCM 写进 [SourceDataLine]，
 * 这样就能得到精确的进度、总时长，并支持暂停/恢复与音量。
 */
object Mp3AudioPlayer {

    /** MPEG-1 Layer III 每帧采样数 */
    private const val SAMPLES_PER_FRAME = 1152

    @Volatile private var line: SourceDataLine? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var volume = 1f

    @Volatile private var framesPlayed = 0L
    @Volatile private var totalFrames = 0L
    @Volatile private var sampleRate = 44100

    val isPlaying: Boolean get() = running && !paused
    val isPaused: Boolean get() = running && paused
    val isActive: Boolean get() = running

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
    }

    fun getVolume() = volume

    /** 当前播放到第几秒 */
    val position: Float
        get() = framesPlayed * SAMPLES_PER_FRAME.toFloat() / sampleRate

    /** 总时长（秒） */
    val duration: Float
        get() = totalFrames * SAMPLES_PER_FRAME.toFloat() / sampleRate

    /**
     * 开始播放。
     * @param openStream 每次调用都要返回一个**全新的**输入流（内部会读两遍：先统计时长再播放）。
     */
    @Synchronized
    fun play(openStream: () -> InputStream) {
        stop()

        totalFrames = try {
            countFrames(openStream())
        } catch (_: Throwable) {
            0L
        }
        framesPlayed = 0L
        running = true
        paused = false

        worker = thread(start = true, isDaemon = true, name = "MusicPlayer-Thread") {
            var bitstream: Bitstream? = null
            var output: SourceDataLine? = null
            try {
                bitstream = Bitstream(BufferedInputStream(openStream(), 1 shl 16))
                val decoder = Decoder()

                var header: Header? = bitstream.readFrame()
                while (running && header != null) {
                    while (running && paused) Thread.sleep(15)
                    if (!running) break

                    val sampleBuffer = decoder.decodeFrame(header, bitstream) as? SampleBuffer ?: break
                    val samples = sampleBuffer.buffer
                    val sampleCount = sampleBuffer.bufferLength

                    if (output == null) {
                        sampleRate = sampleBuffer.sampleFrequency.takeIf { it > 0 } ?: 44100
                        val channels = sampleBuffer.channelCount.takeIf { it > 0 } ?: 2
                        val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
                        val dl = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
                        dl.open(format, 1 shl 16)
                        dl.start()
                        output = dl
                        line = dl
                    }

                    val vol = volume
                    val bytes = ByteArray(sampleCount * 2)
                    for (i in 0 until sampleCount) {
                        var value = (samples[i] * vol).toInt()
                        if (value > 32767) value = 32767 else if (value < -32768) value = -32768
                        bytes[i * 2] = (value and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                    }
                    output.write(bytes, 0, bytes.size)

                    framesPlayed++
                    bitstream.closeFrame()
                    header = bitstream.readFrame()
                }
            } catch (_: Throwable) {
                // 播放期间的任何异常都静默处理（例如切换歌曲时流被关闭）
            } finally {
                try { output?.drain() } catch (_: Throwable) {}
                try { output?.stop() } catch (_: Throwable) {}
                try { output?.close() } catch (_: Throwable) {}
                try { bitstream?.close() } catch (_: Throwable) {}
                line = null
                running = false
                paused = false
            }
        }
    }

    /** 统计总帧数（用于计算时长） */
    private fun countFrames(input: InputStream): Long {
        val bitstream = Bitstream(BufferedInputStream(input, 1 shl 16))
        var count = 0L
        try {
            var header = bitstream.readFrame()
            while (header != null) {
                count++
                bitstream.closeFrame()
                header = bitstream.readFrame()
            }
        } catch (_: Throwable) {
        } finally {
            try { bitstream.close() } catch (_: Throwable) {}
            try { input.close() } catch (_: Throwable) {}
        }
        return count
    }

    @Synchronized
    fun togglePause() {
        if (running) paused = !paused
    }

    @Synchronized
    fun stop() {
        running = false
        paused = false

        val current = line
        line = null
        try { current?.flush() } catch (_: Throwable) {}
        try { current?.stop() } catch (_: Throwable) {}
        try { current?.close() } catch (_: Throwable) {}

        worker?.interrupt()
        worker = null
        framesPlayed = 0L
    }
}
