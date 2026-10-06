/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

/**
 * AutoBlock —— **只负责松格挡，不管攻击**（攻击交给别的模块/玩家自己）。
 *
 * 只有玩家输入右键才工作；触发时只发 `C07 RELEASE_USE_ITEM` 把格挡松开。
 *
 * - **Input** 模式：按住右键 + 点左键（上升沿）触发。
 * - **AimbotInput** 模式：`HypixelAimbot.readyToAttack` 为真触发（并 markHurt 换下一个目标）。
 * - **Both**：两个都用，谁先满足谁触发。
 */
object AutoBlock : Module("AutoBlock", Category.COMBAT) {

    private val mode by choices("Mode", arrayOf("Input", "AimbotInput", "Both"), "Input")
    private val requireRightClick by boolean("RequireRightClick", true) { mode == "Input" || mode == "Both" }

    private var wasAttackDown = false

    override fun onDisable() {
        wasAttackDown = false
    }

    val onGameTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler

        // 手上必须有剑才有格挡可松
        if (player.heldItem?.item !is ItemSword) return@handler

        var trigger = false

        // Input（左键触发）—— Input 或 Both
        if (mode == "Input" || mode == "Both") {
            if (!requireRightClick || mc.gameSettings.keyBindUseItem.isKeyDown) {
                val attackDown = mc.gameSettings.keyBindAttack.isKeyDown
                if (attackDown && !wasAttackDown) trigger = true
                wasAttackDown = attackDown
            } else {
                wasAttackDown = mc.gameSettings.keyBindAttack.isKeyDown
            }
        }

        // AimbotInput（HypixelAimbot 信号触发）—— AimbotInput 或 Both
        if (!trigger && (mode == "AimbotInput" || mode == "Both")) {
            val aimbot = HypixelAimbot
            if (aimbot.handleEvents() && aimbot.readyToAttack) {
                trigger = true
                aimbot.target?.let { aimbot.markHurt(it) }
            }
        }

        if (trigger) releaseBlock()
    }

    /** 只把格挡松开，不发任何攻击包。 */
    private fun releaseBlock() {
        val player = mc.thePlayer ?: return
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN
            )
        )
        player.clearItemInUse()
    }
}
