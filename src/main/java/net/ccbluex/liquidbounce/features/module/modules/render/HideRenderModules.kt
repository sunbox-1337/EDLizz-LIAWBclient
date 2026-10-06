package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module

object HideRenderModules : Module("renders", Category.RENDER) {
    // 保存每个渲染模块原来的隐藏状态
    private val originalHiddenStates = mutableMapOf<Module, Boolean>()

    override fun onEnable() {
        originalHiddenStates.clear()
        for (module in LiquidBounce.moduleManager) {
            if (module.category == Category.RENDER && module != this) {
                originalHiddenStates[module] = module.isHidden
                module.isHidden = true
            }
        }
    }

    override fun onDisable() {
        for ((module, wasHidden) in originalHiddenStates) {
            module.isHidden = wasHidden
        }
        originalHiddenStates.clear()
    }
}