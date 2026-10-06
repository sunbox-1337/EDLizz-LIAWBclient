package net.ccbluex.liquidbounce.auth

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class AuthListener {
    @SubscribeEvent
    fun onGuiOpen(event: GuiOpenEvent) {
        if (event.gui is GuiMainMenu) {
            // 如果已认证则不再拦截主菜单
            if (AuthGuiScreen.isAuthenticated) {
                return
            }
            event.isCanceled = true
            Minecraft.getMinecraft().displayGuiScreen(AuthGuiScreen())
        }
    }
}