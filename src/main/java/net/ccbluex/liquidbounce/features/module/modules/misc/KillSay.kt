package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.WorldEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer

object KillSay : Module("KillSay", Category.MISC) {

    private var target: Entity? = null

    private fun sendMessage(name: String) {
        val clientName = "LIAWB"
        mc.thePlayer?.sendChatMessage("@" + name + " 人生自古谁无死，你已被" + clientName + " 击败")
    }

    val onUpdate = handler<UpdateEvent> {
        val entity = target ?: return@handler
        if (entity.isDead && !mc.thePlayer!!.isDead && !mc.thePlayer!!.isSpectator) {
            sendMessage(entity.name)
            target = null
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        val entity = event.targetEntity
        if (entity is EntityPlayer) {
            target = entity
        }
    }

    val onWorld = handler<WorldEvent> {
        target = null
    }
}