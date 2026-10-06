package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.JLayerUtils
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C02PacketUseEntity

/**
 * 击杀音效：当我们攻击过的目标死亡时播放音效。
 * AutoSpeed 模式下连杀会逐次提升播放倍速，一段时间没有击杀后恢复原速。
 */
object KillSound : Module("KillSound", Category.MISC) {

    private val mode by choices("Mode", arrayOf("Normal", "AutoSpeed"), "AutoSpeed")

    // 目前只有这一个音效，后续可继续往数组里加
    private val sound by choices("Sound", arrayOf("BingBingBing"), "BingBingBing")

    private val volume by float("Volume", 1f, 0.1f..1f)

    // 连杀判定/恢复的时间（秒），仅 AutoSpeed 模式有效
    private val resetTime by int("ResetTime", 3, 1..15) { mode == "AutoSpeed" }

    // 倍速上限，仅 AutoSpeed 模式有效
    private val maxSpeed by float("MaxSpeed", 2f, 1f..4f) { mode == "AutoSpeed" }

    // 每连杀一次提升的倍速
    private val speedPerKill = 0.1f

    // 攻击目标 -> 最后一次攻击时间；用于判断目标是否被我们打死
    private val attackedTargets = HashMap<EntityLivingBase, Long>()

    // 目标超时（毫秒）：超过这个时间既没死也没再被打，就不再追踪
    private val trackTimeout = 5000L

    private var killStreak = 0
    private var lastKillTime = 0L

    // 监听我们发出的攻击包（KillAura 和手动攻击都走 C02PacketUseEntity）
    val onPacket = handler<PacketEvent> { event ->
        if (event.eventType != EventState.SEND) return@handler
        val packet = event.packet
        if (packet !is C02PacketUseEntity) return@handler
        if (packet.action != C02PacketUseEntity.Action.ATTACK) return@handler

        val world = mc.theWorld ?: return@handler
        val entity = packet.getEntityFromWorld(world) as? EntityLivingBase ?: return@handler

        attackedTargets[entity] = System.currentTimeMillis()
    }

    // 每个 tick 检查被攻击的目标是否死亡
    val onUpdate = handler<UpdateEvent> {
        if (attackedTargets.isEmpty()) return@handler

        val now = System.currentTimeMillis()
        val iterator = attackedTargets.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val entity = entry.key

            if (entity.health <= 0f || entity.isDead) {
                iterator.remove()
                onKill()
            } else if (now - entry.value > trackTimeout) {
                iterator.remove()
            }
        }
    }

    private fun onKill() {
        val now = System.currentTimeMillis()
        // 在 resetTime 内再次击杀视为连杀
        killStreak = if (now - lastKillTime <= resetTime * 1000L) killStreak + 1 else 1
        lastKillTime = now
        playKillSound()
    }

    private fun playKillSound() {
        val speed = if (mode == "AutoSpeed") {
            (1f + speedPerKill * (killStreak - 1)).coerceAtMost(maxSpeed)
        } else {
            1f
        }

        val path = "/assets/minecraft/${CLIENT_NAME.lowercase()}/sounds/${soundFile()}.wav"
        try {
            JLayerUtils.playWav(path, speed, volume)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun soundFile() = when (sound) {
        else -> "bingbingbing"
    }

    override fun onDisable() {
        attackedTargets.clear()
        killStreak = 0
        lastKillTime = 0L
    }
}
