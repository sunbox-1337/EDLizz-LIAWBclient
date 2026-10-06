package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemTool
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C0EPacketClickWindow

object ArmorBreaker : Module("ArmorBreaker", Category.COMBAT) {

    // 触发模式
    private val sendEvent by choices("SendEvent", arrayOf("Packet"), "Packet")

    // 护甲部位检查
    private val checkHelmet by boolean("CheckHelmet", true)
    private val checkChestplate by boolean("CheckChestplate", true)
    private val checkLeggings by boolean("CheckLeggings", true)
    private val checkBoots by boolean("CheckBoots", true)

    // 切换方式
    private val silentSwitch by boolean("SilentSwitch", false)
    private val switchBack by boolean("SwitchBack", true)

    // 可见切换时的方法
    private val clickMode by choices("ClickMode", arrayOf("WindowClick", "SendPacket"), "WindowClick")

    // 条件
    private val onlySneaking by boolean("OnlySneaking", false)

    // 状态
    private var switched = false
    private var originalSlot = -1

    val onPacket = handler<PacketEvent> {
        if (sendEvent != "Packet") return@handler
        val packet = it.packet
        if (packet !is C02PacketUseEntity) return@handler
        if (packet.action != C02PacketUseEntity.Action.ATTACK) return@handler

        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        if (onlySneaking && !player.isSneaking) return@handler

        val entity = packet.getEntityFromWorld(world) ?: return@handler
        if (entity !is EntityLivingBase) return@handler

        if (!hasTargetArmor(entity)) return@handler
        if (switched) return@handler

        // 找快捷栏里伤害最高的斧头（反射获取攻击力）
        var bestSlot = -1
        var bestDamage = 0f
        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i) ?: continue
            val item = stack.item
            if (item !is ItemAxe) continue

            val damage = try {
                val field = ItemTool::class.java.getDeclaredField("damageVsEntity")
                field.isAccessible = true
                (field.get(item) as? Float) ?: 0f
            } catch (_: Exception) {
                // 无法反射时使用默认伤害 3（木斧）
                3f
            }

            if (damage > bestDamage) {
                bestDamage = damage
                bestSlot = i
            }
        }
        if (bestSlot == -1 || bestSlot == player.inventory.currentItem) return@handler

        it.cancelEvent()

        originalSlot = player.inventory.currentItem

        // 切换到斧头
        if (silentSwitch) {
            net.ccbluex.liquidbounce.utils.inventory.SilentHotbar.selectSlotSilently(
                this, bestSlot, render = false, resetManually = true
            )
        } else {
            when (clickMode) {
                "WindowClick" -> {
                    mc.playerController.windowClick(
                        player.openContainer.windowId,
                        bestSlot + 36,
                        player.inventory.currentItem,
                        2,
                        player
                    )
                }
                "SendPacket" -> {
                    val p = C0EPacketClickWindow(
                        player.openContainer.windowId,
                        bestSlot + 36,
                        player.inventory.currentItem,
                        2,
                        player.inventory.getStackInSlot(player.inventory.currentItem + 36),
                        player.openContainer.getNextTransactionID(player.inventory)
                    )
                    sendPacket(p)
                }
            }
        }
        switched = true

        // 重新发送攻击包
        mc.netHandler.addToSendQueue(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))

        // 切回原武器
        if (switchBack) {
            if (silentSwitch) {
                net.ccbluex.liquidbounce.utils.inventory.SilentHotbar.resetSlot(this)
            } else {
                when (clickMode) {
                    "WindowClick" -> {
                        mc.playerController.windowClick(
                            player.openContainer.windowId,
                            originalSlot + 36,
                            player.inventory.currentItem,
                            2,
                            player
                        )
                    }
                    "SendPacket" -> {
                        val p = C0EPacketClickWindow(
                            player.openContainer.windowId,
                            originalSlot + 36,
                            player.inventory.currentItem,
                            2,
                            player.inventory.getStackInSlot(player.inventory.currentItem + 36),
                            player.openContainer.getNextTransactionID(player.inventory)
                        )
                        sendPacket(p)
                    }
                }
            }
            switched = false
        }
    }

    private fun hasTargetArmor(entity: EntityLivingBase): Boolean {
        if (checkBoots && entity.getEquipmentInSlot(1) != null) return true
        if (checkLeggings && entity.getEquipmentInSlot(2) != null) return true
        if (checkChestplate && entity.getEquipmentInSlot(3) != null) return true
        if (checkHelmet && entity.getEquipmentInSlot(4) != null) return true
        return false
    }

    override fun onDisable() {
        if (switched && switchBack) {
            val player = mc.thePlayer ?: return
            if (silentSwitch) {
                net.ccbluex.liquidbounce.utils.inventory.SilentHotbar.resetSlot(this)
            } else {
                when (clickMode) {
                    "WindowClick" -> {
                        mc.playerController.windowClick(
                            player.openContainer.windowId,
                            originalSlot + 36,
                            player.inventory.currentItem,
                            2,
                            player
                        )
                    }
                    "SendPacket" -> {
                        val p = C0EPacketClickWindow(
                            player.openContainer.windowId,
                            originalSlot + 36,
                            player.inventory.currentItem,
                            2,
                            player.inventory.getStackInSlot(player.inventory.currentItem + 36),
                            player.openContainer.getNextTransactionID(player.inventory)
                        )
                        sendPacket(p)
                    }
                }
            }
        }
        switched = false
        originalSlot = -1
    }
}