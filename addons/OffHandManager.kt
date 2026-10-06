/*
 * NekoBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/RouQingNeko1024/NekoBounce
 * Code By GoldBounce,Lizz,NightSky,FDP
 * https://github.com/SkidderMC/FDPClient
 * https://github.com/qm123pz/NightSky-Client
 * https://github.com/bzym2/GoldBounce/
 */
//SKID SuperBounce
//By NekoBanka
package net.ccbluex.liquidbounce.features.module.modules.addons

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.KeyEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.ViaPacket
import net.minecraft.item.*
import org.lwjgl.input.Keyboard

object OffHandManager : Module("OffHandManager", Category.ADDONS) {

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
            ViaPacket.sendSwapHeldItem()
            shouldSwap = false
        }
        if (mc.gameSettings.keyBindUseItem.isKeyDown && canUse) {
            val held = mc.thePlayer?.heldItem
            if (held == null) {
                ViaPacket.sendOffHandUseItem()
            } else {
                val item = held.item
                if (item !is ItemFood && item !is ItemSword && item !is ItemBow &&
                    item !is ItemFishingRod && item !is ItemEnderPearl && item !is ItemBucket) {
                    ViaPacket.sendOffHandUseItem()
                }
            }
        }
    }
}