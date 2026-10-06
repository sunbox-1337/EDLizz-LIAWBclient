package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.InfoDisplay
import net.minecraft.block.BlockAir
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.S22PacketMultiBlockChange
import net.minecraft.network.play.server.S23PacketBlockChange
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.sqrt

object AntiCheat : Module("AntiCheat", Category.MISC) {

    // ========== Scaffold 检测参数 ==========
    private val placeThreshold by int("PlaceThreshold", 5, 1..20)
    private val speedThreshold by float("SpeedThreshold", 8f, 1f..20f)

    // ========== Velocity 检测参数 ==========
    private val velocityCheckTicks by int("VelocityCheckTicks", 5, 1..20)
    private val velocityCooldown by int("VelocityCooldown", 60, 20..200)
    private val velocityMoveThreshold by float("VelocityMoveThreshold", 0.01f, 0.0f..0.5f)

    // Scaffold 内部状态
    private val placeTimestamps = mutableListOf<Long>()
    private val playerBpsMap = mutableMapOf<EntityPlayer, BpsData>()

    // Velocity 内部状态
    private var lastHurtPos: Vec3? = null
    private var hurtCheckTicks = 0
    private var lastVelocityWarningTick = -velocityCooldown
    private var lastAttackerName: String? = null   // 记录最近攻击者的名字

    private data class BpsData(var lastX: Double, var lastZ: Double, var lastTime: Long, var bps: Double = 0.0)

    override fun onEnable() {
        placeTimestamps.clear()
        playerBpsMap.clear()
        hurtCheckTicks = 0
        lastHurtPos = null
        lastAttackerName = null
    }

    // ========== Scaffold 检测：监听方块变更包 ==========
    val onPacket = handler<PacketEvent> { event ->
        if (event.packet is S23PacketBlockChange || event.packet is S22PacketMultiBlockChange) {
            placeTimestamps.add(System.currentTimeMillis())
        }
    }

    // ========== Velocity 检测：记录受伤时位置与攻击者 ==========
    @SubscribeEvent
    fun onHurt(event: LivingHurtEvent) {
        val attacker: Entity? = event.source.entity
        if (event.entityLiving === mc.thePlayer && attacker != null) {
            lastHurtPos = Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)
            lastAttackerName = attacker.name   // 记录攻击者名称
            hurtCheckTicks = velocityCheckTicks
        }
    }

    // ========== 主检测逻辑 ==========
    val onTick = handler<GameTickEvent> {
        // ---- Scaffold 检测 ----
        val now = System.currentTimeMillis()
        placeTimestamps.removeAll { now - it > 1000 }
        val currentPlaceRate = placeTimestamps.size

        if (currentPlaceRate >= placeThreshold) {
            val world = mc.theWorld ?: return@handler
            for (player in world.playerEntities) {
                if (player == mc.thePlayer || player.isDead) continue
                updatePlayerBps(player)
                val bps = playerBpsMap[player]?.bps ?: 0.0
                if (bps >= speedThreshold) {
                    InfoDisplay.showLog("§c${player.name} Fail /scaffold - dec hacker")
                    placeTimestamps.clear()
                    break
                }
            }
        }

        // ---- Velocity 检测（含阻挡判定） ----
        if (hurtCheckTicks > 0) {
            val pos = lastHurtPos ?: return@handler
            val moved = mc.thePlayer.getDistance(pos.xCoord, pos.yCoord, pos.zCoord) > velocityMoveThreshold
            if (!moved) {
                // 检查是否因地形阻挡无法移动
                if (isPlayerBlocked()) {
                    hurtCheckTicks = 0
                    return@handler
                }
                hurtCheckTicks--
                if (hurtCheckTicks == 0) {
                    val currentTick = mc.thePlayer.ticksExisted
                    if (currentTick - lastVelocityWarningTick >= velocityCooldown) {
                        val attackerName = lastAttackerName ?: "Unknown"
                        InfoDisplay.showLog("§c$attackerName Fail /velocity - dec hacker")
                        lastVelocityWarningTick = currentTick
                    }
                }
            } else {
                hurtCheckTicks = 0
            }
        }
    }

    /**
     * 检查玩家水平四个方向是否被实体方块完全阻挡（两层高度）
     */
    private fun isPlayerBlocked(): Boolean {
        val player = mc.thePlayer ?: return true
        val world = mc.theWorld ?: return true
        val basePos = BlockPos(player.posX, player.posY, player.posZ)

        val directions = listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1)
        )
        for (dir in directions) {
            for (yOffset in 0..1) {
                val checkPos = basePos.add(dir).add(0, yOffset, 0)
                val block = world.getBlockState(checkPos).block
                if (block is BlockAir || !block.isFullBlock || block == Blocks.snow_layer) {
                    return false
                }
            }
        }
        return true
    }

    private fun updatePlayerBps(player: EntityPlayer) {
        val data = playerBpsMap.getOrPut(player) {
            BpsData(player.posX, player.posZ, System.currentTimeMillis())
        }
        val now = System.currentTimeMillis()
        val dt = now - data.lastTime
        if (dt > 0) {
            val dx = player.posX - data.lastX
            val dz = player.posZ - data.lastZ
            val dist = sqrt(dx * dx + dz * dz)
            data.bps = dist * (1000.0 / dt)
        }
        data.lastX = player.posX
        data.lastZ = player.posZ
        data.lastTime = now
    }
}