/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.world.Fucker
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.LiquidBounce
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemSword
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.Vec3
import java.awt.Color
import kotlin.streams.toList

object LagRange : Module("LagRange", Category.COMBAT) {

    /**
     * SETTINGS
     */
    private val delay by int("Delay", 150, 0..1000)
    private val range by float("Range", 10.0F, 3.0F..100.0F)
    private val weaponsOnly by boolean("WeaponsOnly", true)
    private val allowTools by boolean("AllowTools", false) { weaponsOnly }
    private val botCheck by boolean("BotCheck", true)
    private val teams by boolean("Teams", true)
    private val showPosition by choices("ShowPosition", arrayOf("NONE", "DEFAULT", "HUD"), "NONE")

    /**
     * VALUES
     */
    private var tickIndex = -1
    private var delayCounter = 0L
    private var hasTarget = false
    private var lastPosition: Vec3? = null
    private var currentPosition: Vec3? = null

    private fun isValidTarget(entityPlayer: EntityPlayer): Boolean {
        if (entityPlayer == mc.thePlayer || entityPlayer == mc.thePlayer.ridingEntity) {
            return false
        }
        if (entityPlayer == mc.renderViewEntity || entityPlayer == mc.renderViewEntity?.ridingEntity) {
            return false
        }
        if (entityPlayer.deathTime > 0) {
            return false
        }

        // 检查是否是好友
        if (isFriend(entityPlayer)) {
            return false
        }

        // 使用自定义的团队检测方法
        return if (teams) {
            !isSameTeam(entityPlayer)
        } else {
            true
        } && (!botCheck || !isBot(entityPlayer))
    }

    // 简单的友军检测
    private fun isFriend(entity: EntityPlayer): Boolean {
        // 这里可以添加自定义的好友列表检查
        return false
    }

    // 简单的队伍检测
    private fun isSameTeam(entity: EntityPlayer): Boolean {
        val player = mc.thePlayer ?: return false

        // 基础队伍检测 - 检查队伍颜色
        if (player.team != null && entity.team != null && player.team.isSameTeam(entity.team)) {
            return true
        }

        // 检查名称颜色
        val playerName = player.displayName?.formattedText ?: ""
        val entityName = entity.displayName?.formattedText ?: ""

        if (playerName.isNotEmpty() && entityName.isNotEmpty()) {
            val playerColor = if (playerName.length > 2) playerName.substring(0, 2) else ""
            val entityColor = if (entityName.length > 2) entityName.substring(0, 2) else ""

            if (playerColor == entityColor && playerColor.startsWith("§")) {
                return true
            }
        }

        // 检查皮革盔甲颜色
        for (i in 0..3) {
            val playerArmor = player.getCurrentArmor(i) ?: continue
            val entityArmor = entity.getCurrentArmor(i) ?: continue

            if (playerArmor.item is net.minecraft.item.ItemArmor && entityArmor.item is net.minecraft.item.ItemArmor) {
                val playerItem = playerArmor.item as net.minecraft.item.ItemArmor
                val entityItem = entityArmor.item as net.minecraft.item.ItemArmor

                if (playerItem.getColor(playerArmor) == entityItem.getColor(entityArmor) &&
                    entityItem.armorMaterial == net.minecraft.item.ItemArmor.ArmorMaterial.LEATHER) {
                    return true
                }
            }
        }

        return false
    }

    // 简单的机器人检测
    private fun isBot(entity: EntityPlayer): Boolean {
        // 这里可以添加自定义的机器人检测逻辑
        return false
    }

    private fun shouldResetOnPacket(packet: Packet<*>): Boolean {
        return when (packet) {
            is C02PacketUseEntity -> true
            is C07PacketPlayerDigging -> packet.status != Action.RELEASE_USE_ITEM
            is C08PacketPlayerBlockPlacement -> {
                val item = packet.stack
                item == null || item.item !is ItemSword
            }
            else -> false
        }
    }

    // 简化的物品检查 - 只检查是否是剑
    private fun isHoldingWeapon(): Boolean {
        val heldItem = mc.thePlayer?.heldItem ?: return false
        return heldItem.item is ItemSword
    }

    // 简化的工具检查
    private fun isHoldingTool(): Boolean {
        val heldItem = mc.thePlayer?.heldItem ?: return false
        val itemName = heldItem.unlocalizedName.toLowerCase()
        return itemName.contains("pickaxe") || itemName.contains("axe") ||
                itemName.contains("shovel") || itemName.contains("hoe")
    }

    val onTick = handler<UpdateEvent> {
        if (!state) return@handler

        hasTarget = false

        val fucker = LiquidBounce.moduleManager.getModule(Fucker::class.java)
        if ((fucker == null || !fucker.state) &&
            !mc.playerController.isHittingBlock &&
            (!mc.thePlayer.isUsingItem || mc.thePlayer.isBlocking) &&
            (!weaponsOnly || isHoldingWeapon() || (allowTools && isHoldingTool()))
        ) {
            val players = mc.theWorld.loadedEntityList
                .stream()
                .filter { it is EntityPlayer }
                .map { it as EntityPlayer }
                .filter { isValidTarget(it) }
                .toList()

            if (players.isEmpty()) {
                tickIndex = -1
            } else {
                for (player in players) {
                    val distance = mc.thePlayer.getDistanceToEntity(player)
                    if (distance <= range) {
                        if (tickIndex < 0) {
                            tickIndex = 0
                            delayCounter += delay.toLong()
                            while (delayCounter > 0L) {
                                delayCounter -= 50
                                tickIndex++
                            }
                        }
                        hasTarget = true
                        return@handler
                    }
                }
            }
        } else {
            tickIndex = -1
        }
    }

    val onPostTick = handler<GameTickEvent> {
        if (!state) return@handler

        val savedPosition = Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)
        if (currentPosition == null) {
            lastPosition = savedPosition
        } else {
            lastPosition = currentPosition
        }
        currentPosition = savedPosition
    }

    val onPacket = handler<PacketEvent> {
        if (!state) return@handler

        if (shouldResetOnPacket(it.packet)) {
            tickIndex = -1
        }
    }

    val onRender3D = handler<Render3DEvent> {
        if (!state) return@handler

        if (showPosition != "NONE" &&
            mc.gameSettings.thirdPersonView != 0 &&
            hasTarget &&
            lastPosition != null &&
            currentPosition != null
        ) {
            var color = Color.WHITE
            when (showPosition) {
                "DEFAULT" -> color = Color.RED // 使用红色作为默认颜色
                "HUD" -> color = Color.CYAN // 使用青色作为HUD颜色
            }

            val x = currentPosition!!.xCoord + (lastPosition!!.xCoord - currentPosition!!.xCoord) * it.partialTicks
            val y = currentPosition!!.yCoord + (lastPosition!!.yCoord - currentPosition!!.yCoord) * it.partialTicks
            val z = currentPosition!!.zCoord + (lastPosition!!.zCoord - currentPosition!!.zCoord) * it.partialTicks

            val size = mc.thePlayer.collisionBorderSize
            val aabb = AxisAlignedBB(
                x - mc.thePlayer.width / 2.0,
                y,
                z - mc.thePlayer.width / 2.0,
                x + mc.thePlayer.width / 2.0,
                y + mc.thePlayer.height,
                z + mc.thePlayer.width / 2.0
            )
                .expand(size.toDouble(), size.toDouble(), size.toDouble())
                .offset(
                    -mc.renderManager.renderPosX,
                    -mc.renderManager.renderPosY,
                    -mc.renderManager.renderPosZ
                )

            // 修复drawFilledBox参数问题
            RenderUtils.drawFilledBox(aabb)
        }
    }

    override fun onDisable() {
        tickIndex = -1
        delayCounter = 0L
        hasTarget = false
        lastPosition = null
        currentPosition = null
    }

    override val tag: String
        get() = "${delay}ms"
}