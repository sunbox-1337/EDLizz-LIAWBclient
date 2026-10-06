package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.StringUtils

object AutoAdvertiser : Module("AutoAdvertiser", Category.MISC) {

    private val mode by choices("Mode", arrayOf("Interval", "Kill", "Both"), "Both")
    private val interval by int("Interval", 60, 10..600) { mode in listOf("Interval", "Both") }
    private val advertiseMessage by text("AdvertiseMessage", "LIAWB client - best lezi minecraft hacker client >815061613")
    private val killMessage by text("KillMessage", "@%player% 你被世界上最zhao笑的客户端击败了，不妨来看看？---815061613")
    private val stripColors by boolean("StripColors", true)

    private var lastAdvertiseTime = 0L
    private val trackedPlayers = mutableMapOf<Int, Pair<Float, String>>()

    override fun onEnable() {
        lastAdvertiseTime = System.currentTimeMillis()
        trackedPlayers.clear()
    }

    val onGameTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler
        val currentTime = System.currentTimeMillis()

        // 如果玩家死亡，不发送任何消息
        if (!player.isEntityAlive) return@handler

        // 间隔发送广告
        if (mode in listOf("Interval", "Both") && currentTime - lastAdvertiseTime >= interval * 1000L) {
            val msg = prepareMessage(advertiseMessage, null)
            player.sendChatMessage(msg)
            lastAdvertiseTime = currentTime
        }

        // 击杀检测
        if (mode in listOf("Kill", "Both")) {
            for (entity in world.loadedEntityList) {
                if (entity is EntityPlayer && entity != player) {
                    val entityId = entity.entityId
                    if (entity.isDead && trackedPlayers.containsKey(entityId)) {
                        val targetName = entity.name
                        val msg = prepareMessage(killMessage, targetName)
                        player.sendChatMessage(msg)
                        trackedPlayers.remove(entityId)
                    }
                }
            }
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        val target = event.targetEntity ?: return@handler
        if (target !is EntityPlayer || target == mc.thePlayer) return@handler
        if (mode in listOf("Kill", "Both")) {
            trackedPlayers[target.entityId] = Pair(target.health, target.name)
        }
    }

    private fun prepareMessage(template: String, playerName: String?): String {
        var msg = template
        if (playerName != null) {
            msg = msg.replace("%player%", playerName)
        }
        if (stripColors) {
            msg = StringUtils.stripControlCodes(msg)
        }
        return msg
    }

    override val tag: String
        get() = mode
}