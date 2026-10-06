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

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.WorldEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer

object KillSay : Module("KillSay", Category.ADDONS) {

    private var target: Entity? = null

    private fun sendMessage(name: String) {
        val clientName = "SuperBounce"
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