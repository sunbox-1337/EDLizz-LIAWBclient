package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.KeyEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemBucket
import net.minecraft.item.ItemEnderPearl
import net.minecraft.item.ItemFishingRod
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import org.lwjgl.input.Keyboard

object OffHandManager : Module("OffHandManager", Category.PLAYER) {

    val swapKeyBind by text("SwapKeyBind", "F")
    val canUse by boolean("CanUse", true)

    private var shouldSwap = false

    override fun onDisable() {
        shouldSwap = false
    }

    val onKey = handler<KeyEvent> { event ->
        if (event.key == Keyboard.getKeyIndex(swapKeyBind)) {
            shouldSwap = true
        }
    }

    val onTick = handler<GameTickEvent> {
        if (shouldSwap) {
            // 1.8.9 不支持真正的副手交换，此处发送一个空的切换包以保留按键反馈
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer?.inventory?.currentItem ?: 0))
            shouldSwap = false
        }
        if (mc.gameSettings.keyBindUseItem.isKeyDown && canUse) {
            val held = mc.thePlayer?.heldItem
            if (held == null) {
                // 空手时模拟使用副手
                mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(null))
            } else {
                val item = held.item
                if (item !is ItemFood && item !is ItemSword && item !is ItemBow &&
                    item !is ItemFishingRod && item !is ItemEnderPearl && item !is ItemBucket
                ) {
                    // 主手不是特殊物品，模拟使用副手（发送当前主手物品的放置包）
                    mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(held))
                }
            }
        }
    }
}