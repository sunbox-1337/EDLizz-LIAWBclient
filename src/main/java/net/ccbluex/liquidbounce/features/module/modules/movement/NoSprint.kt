/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.setSprintSafely

/**
 * NoSprint —— 开启后无论怎样都无法疾跑。
 *
 * 挂点在 MixinEntityPlayerSP.onLivingUpdate()：那里每次更新都会跑一遍原版疾跑判定
 * （含 Sprint 模块的 correctSprintState），紧跟着强制关掉客户端疾跑状态，
 * 疾跑就不会生效，之后 onUpdateWalkingPlayer() 里也不会再发出 START_SPRINTING 包。
 */
object NoSprint : Module("NoSprint", Category.MOVEMENT, gameDetecting = false) {

    /** 由 mixin 每 tick 调用：把当前疾跑状态强制关掉。 */
    fun stopSprinting() {
        mc.thePlayer?.let { it setSprintSafely false }
    }
}
