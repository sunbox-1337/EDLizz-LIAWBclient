/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.utils.client.PacketUtils
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.canClickInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.hasScheduledInLastLoop
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenContainer
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.settings.GameSettings
import net.minecraft.client.settings.KeyBinding
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.client.C0EPacketClickWindow
import org.lwjgl.input.Mouse

object InventoryMove : Module("InventoryMove", Category.MOVEMENT, gameDetecting = false) {

    private val notInChests by boolean("NotInChests", false)
    val aacAdditionPro by boolean("AACAdditionPro", false)
    private val intave by boolean("Intave", false)
    private val saveC0E by boolean("SaveC0E", false)
    private val onlyChest by boolean("Onlychest", false) { saveC0E }
    private val noSprintWhenClosed by boolean("NoSprintWhenClosed", false) { saveC0E }

    // 新增：允许在 GUI 中自由转动视角（默认开启）
    private val freeLook by boolean("FreeLook", true)

    private val isIntave = (mc.currentScreen is GuiInventory || mc.currentScreen is GuiChest) && intave
    private val clickWindowList = ArrayDeque<C0EPacketClickWindow>()

    private val noMove by +InventoryManager.noMoveValue
    private val noMoveAir by +InventoryManager.noMoveAirValue
    private val noMoveGround by +InventoryManager.noMoveGroundValue
    private val undetectable by +InventoryManager.undetectableValue

    private val silentlyCloseAndReopen by boolean(
        "SilentlyCloseAndReopen",
        false
    ) { noMove && (noMoveAir || noMoveGround) }

    private val reopenOnClick by boolean(
        "ReopenOnClick",
        false
    ) { silentlyCloseAndReopen && noMove && (noMoveAir || noMoveGround) }

    private val inventoryMotion by float("InventoryMotion", 1F, 0F..2F)

    private val affectedBindings = arrayOf(
        mc.gameSettings.keyBindForward,
        mc.gameSettings.keyBindBack,
        mc.gameSettings.keyBindRight,
        mc.gameSettings.keyBindLeft,
        mc.gameSettings.keyBindJump,
        mc.gameSettings.keyBindSprint
    )

    // 标记是否正处于 FreeLook 捕获状态，用于在模块关闭时正确释放
    private var freeLookActive = false

    val onUpdate = handler<UpdateEvent>(priority = -1) {
        val player = mc.thePlayer ?: return@handler
        val screen = mc.currentScreen

        if (shouldFreezeInputs(screen)) {
            unPressKeys()
            return@handler
        }

        // 仅在打开箱子/背包且 FreeLook 开启时捕获鼠标并更新视角
        if (freeLook && (screen is GuiChest || screen is GuiInventory)) {
            grabMouse()
            freeLookActive = true

            val dx = Mouse.getDX()
            val dy = Mouse.getDY()
            if (dx != 0 || dy != 0) {
                val sensitivity = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f
                val deltaYaw = dx * sensitivity * 0.15f
                val deltaPitch = -dy * sensitivity * 0.15f
                player.rotationYaw += deltaYaw
                player.rotationPitch = (player.rotationPitch + deltaPitch).coerceIn(-90f, 90f)
            }
        } else {
            // 离开 GUI 时，如果之前处于捕获状态，交给游戏自己处理，不再手动释放
            if (freeLookActive) {
                freeLookActive = false
                // 无需手动释放，Minecraft 会在屏幕切换时正确重置鼠标
            }
        }

        // 原有的移动逻辑
        if (screen is GuiInventory || screen is GuiChest) {
            player.motionX *= inventoryMotion
            player.motionZ *= inventoryMotion
        }

        if (silentlyCloseAndReopen && screen is GuiInventory) {
            if (canClickInventory(closeWhenViolating = true) && !reopenOnClick) serverOpenInventory = true
        }

        for (affectedBinding in affectedBindings)
            affectedBinding.pressed =
                isButtonPressed(affectedBinding) || affectedBinding == mc.gameSettings.keyBindSprint && Sprint.handleEvents() && Sprint.mode == "Legit" && (!Sprint.onlyOnSprintPress || mc.thePlayer.isSprinting) || affectedBinding == mc.gameSettings.keyBindForward && AutoWalk.handleEvents()
    }

    private fun grabMouse() {
        mc.inGameHasFocus = true
        Mouse.setGrabbed(true)
    }

    private fun shouldFreezeInputs(screen: GuiScreen?): Boolean {
        if (screen is GuiChat || screen is GuiIngameMenu) return true
        if (undetectable && (screen != null && screen !is GuiHudDesigner && screen !is ClickGui)) return true
        if (notInChests && screen is GuiChest) return true
        return false
    }

    val onStrafe = handler<StrafeEvent> {
        if (isIntave) {
            mc.gameSettings.keyBindSneak.pressed = true
        }
    }

    val onJump = handler<JumpEvent> { event ->
        if (isIntave) event.cancelEvent()
    }

    val onClick = handler<ClickWindowEvent> { event ->
        if (!canClickInventory()) event.cancelEvent()
        else if (reopenOnClick) {
            hasScheduledInLastLoop = false
            serverOpenInventory = true
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        val player = mc.thePlayer ?: return@handler

        if (!saveC0E) return@handler

        if (onlyChest) {
            val currentScreen = mc.currentScreen
            if (currentScreen !is net.minecraft.client.gui.inventory.GuiChest) {
                return@handler
            }
        }

        if (noSprintWhenClosed) {
            if (clickWindowList.isNotEmpty() && !(serverOpenInventory || serverOpenContainer)) player.isSprinting =
                false

            if (packet is C0DPacketCloseWindow) {
                event.cancelEvent()
                player.isSprinting = false
                if (!player.serverSprintState) PacketUtils.sendPacket(C0DPacketCloseWindow(), false)
            }
        }

        if (serverOpenInventory || serverOpenContainer) {
            if (packet is C0EPacketClickWindow) {
                clickWindowList.add(packet)
                event.cancelEvent()
            }
        } else if (clickWindowList.isNotEmpty()) {
            clickWindowList.forEach {
                PacketUtils.sendPacket(it, false)
            }
            clickWindowList.clear()
        }
    }

    override fun onDisable() {
        for (affectedBinding in affectedBindings) affectedBinding.pressed = isButtonPressed(affectedBinding)
        // 确保模块关闭时释放鼠标（如果处于捕获状态）
        if (freeLookActive) {
            Mouse.setGrabbed(false)
            mc.inGameHasFocus = false
            freeLookActive = false
        }
    }

    private fun isButtonPressed(keyBinding: KeyBinding): Boolean {
        return if (keyBinding.keyCode < 0) {
            Mouse.isButtonDown(keyBinding.keyCode + 100)
        } else {
            GameSettings.isKeyDown(keyBinding)
        }
    }

    private fun unPressKeys() {
        affectedBindings.forEach {
            it.pressed = false
        }
    }

    override val tag
        get() = when {
            aacAdditionPro -> "AACAdditionPro"
            inventoryMotion != 1F -> inventoryMotion.toString()
            else -> null
        }
}