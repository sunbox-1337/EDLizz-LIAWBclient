package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.ClickWindowEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.item.Item
import net.minecraft.item.ItemArmor

object SaveArmor : Module("SaveArmor", Category.PLAYER) {

    val maxStore by int("MaxStore", 1, 1..5)

    private var pass = false

    private fun check(): Boolean {
        val counts = IntArray(4)
        for (i in 9 until 45) {
            val stack = mc.thePlayer?.inventoryContainer?.getSlot(i)?.stack ?: continue
            if (stack.item !is ItemArmor) continue
            val type = Item.getIdFromItem(stack.item) - 310
            if (type in 0..3) counts[type]++
        }
        return counts.any { it > maxStore }
    }

    val onUpdate = handler<UpdateEvent> {
        if (!check()) return@handler
        val counts = IntArray(4)
        for (i in 9 until 45) {
            val stack = mc.thePlayer?.inventoryContainer?.getSlot(i)?.stack ?: continue
            if (stack.item !is ItemArmor) continue
            val type = Item.getIdFromItem(stack.item) - 310
            if (type in 0..3) counts[type]++
        }
        val targetType = (0..3).firstOrNull { counts[it] > maxStore } ?: return@handler
        for (i in 9 until 45) {
            val stack = mc.thePlayer?.inventoryContainer?.getSlot(i)?.stack ?: continue
            if (stack.item !is ItemArmor) continue
            val type = Item.getIdFromItem(stack.item) - 310
            if (type == targetType && counts[targetType] > maxStore) {
                pass = true
                mc.playerController.windowClick(mc.thePlayer?.openContainer?.windowId ?: 0, i, 1, 4, mc.thePlayer)
                pass = false
                counts[targetType]--
            }
        }
    }

    val onClickWindow = handler<ClickWindowEvent> { event ->
        if (event.slotId == -999) return@handler
        val stack = mc.thePlayer?.inventoryContainer?.getSlot(event.slotId)?.stack ?: return@handler
        val id = Item.getIdFromItem(stack.item)
        if (event.mode == 4 && stack.item is ItemArmor && id in 310..313) {
            if (!pass) event.cancelEvent()
        }
    }
}