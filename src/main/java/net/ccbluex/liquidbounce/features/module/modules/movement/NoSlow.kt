/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import io.netty.buffer.Unpooled
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.player.Gapple
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.ViaUseItem
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.hasMotion
import net.ccbluex.liquidbounce.utils.timing.TickTimer
import net.minecraft.block.*
import net.minecraft.item.*
import net.minecraft.network.PacketBuffer
import net.minecraft.network.handshake.client.C00Handshake
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.DROP_ITEM
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraft.network.play.server.S2FPacketSetSlot
import net.minecraft.network.status.client.C00PacketServerQuery
import net.minecraft.network.status.client.C01PacketPing
import net.minecraft.network.status.server.S01PacketPong
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3


object NoSlow : Module("NoSlow", Category.MOVEMENT, gameDetecting = false) {

    private val swordMode by choices(
        "SwordMode",
        arrayOf("None", "NCP", "UpdatedNCP", "AAC5", "SwitchItem", "InvalidC08", "Blink","PostPlace", "GrimAC", "BlocksMC", "SilenceGrim", "MoonVia"),
        "None"
    )

    private val bmcTicks by int("BMC Ticks", 1, 1..20) { swordMode == "BlocksMC" }
    private val bmcOldOffset by boolean("BMC OldOffset", false) { swordMode == "BlocksMC" }
    private val reblinkTicks by int("ReblinkTicks", 10, 1..20) { swordMode == "Blink" }

    private val blockForwardMultiplier by float("BlockForwardMultiplier", 1f, 0.2F..1f)
    private val blockStrafeMultiplier by float("BlockStrafeMultiplier", 1f, 0.2F..1f)

    private val consumeMode by choices(
        "ConsumeMode",
        arrayOf("None", "UpdatedNCP", "AAC5", "SwitchItem", "InvalidC08", "Intave", "Drop", "BlocksMC", "SilenceSW", "SilenceBW", "MoonGrim", "BowDraw"),
        "None"
    )

    private val consumeForwardMultiplier by float("ConsumeForwardMultiplier", 1f, 0.2F..1f)
    private val consumeStrafeMultiplier by float("ConsumeStrafeMultiplier", 1f, 0.2F..1f)
    private val consumeFoodOnly by boolean(
        "ConsumeFood",
        true
    ) { consumeForwardMultiplier > 0.2F || consumeStrafeMultiplier > 0.2F }
    private val consumeDrinkOnly by boolean(
        "ConsumeDrink",
        true
    ) { consumeForwardMultiplier > 0.2F || consumeStrafeMultiplier > 0.2F }

    private val bowPacket by choices(
        "BowMode",
        arrayOf("None", "UpdatedNCP", "AAC5", "SwitchItem", "InvalidC08", "MoonVia"),
        "None"
    )

    private val bowForwardMultiplier by float("BowForwardMultiplier", 1f, 0.2F..1f)
    private val bowStrafeMultiplier by float("BowStrafeMultiplier", 1f, 0.2F..1f)

    // Blocks
    val soulSand by boolean("SoulSand", true)
    val liquidPush by boolean("LiquidPush", true)

    private var shouldSwap = false
    private var shouldBlink = true
    private var shouldNoSlow = false

    private var hasDropped = false

    private val BlinkTimer = TickTimer()
    // blocksmc add
    private var randomFactor = 0f
    private var sent = false

    // consume BlocksMC tick counter (移植自你给的 BlocksMCNoSlow)
    private var consumeTickCycle = 0

    // ---- MoonLight GrimAC（跨版本 1.9+/1.12.2）金苹果假吃状态 ----
    private var moonEat = true
    private var moonLastFoodAmount = 0
    private var moonFoodSpeed = 0.2f

    // ---- SilenceFix NoSlow 移植状态 ----
    /** SilenceSW/BW：已发过「假动作」包，服务端这一段不认为你在使用物品。 */
    private var silenceIsSlow = false
    /** SilenceSW：金苹果假吃流程进行中（C07 RELEASE 已被吞）。 */
    private var silenceStartedEat = false
    private var silenceEatTicks = 0
    private var silenceTempAppleCount = 0
    /** SilenceBW：特效系统窗口已被吞，松开右键时要补 closeScreen。 */
    private var silenceXinXin = false

    override fun onDisable() {
        shouldSwap = false
        shouldBlink = true
        BlinkTimer.reset()
        BlinkUtils.unblink()
        silenceIsSlow = false
        silenceStartedEat = false
        silenceEatTicks = 0
        silenceXinXin = false
        moonEat = true
        moonLastFoodAmount = 0
        moonFoodSpeed = 0.2f
    }

    val onMotion = handler<MotionEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        val heldItem = player.heldItem ?: return@handler
        val isUsingItem = usingItemFunc()

        // ---- SilenceFix NoSlow 的 tick 逻辑（原 onTick + onUpdate，PRE 驱动）----
        if (event.eventState == EventState.PRE && (consumeMode == "SilenceSW" || consumeMode == "SilenceBW")) {
            // 金苹果假吃计数；进度被打断（数量对不上）就复位
            if (silenceStartedEat && heldItem.item is ItemAppleGold) {
                silenceEatTicks++
                val stackSize = heldItem.stackSize
                if (stackSize <= 0 || stackSize < silenceTempAppleCount - 1) {
                    silenceEatTicks = 0
                    silenceStartedEat = false
                }
            } else {
                silenceEatTicks = 0
            }
            // 吃的进度靠自动重按右键推进，客户端把按键松开避免 Vanilla 重置
            if (silenceEatTicks >= 1) {
                mc.gameSettings.keyBindUseItem.pressed = false
            }
            // 换到剑/弓 → 假状态作废
            if (heldItem.item is ItemSword || heldItem.item is ItemBow) {
                silenceIsSlow = false
            }
            // 松开使用键 → 复位；BW 模式补关被吞的窗口
            if (!mc.gameSettings.keyBindUseItem.isKeyDown) {
                silenceIsSlow = false
                if (consumeMode == "SilenceBW" && silenceXinXin) {
                    player.closeScreen()
                    silenceXinXin = false
                }
            }
        }

        // ---- MoonLight 移植：跨版本（1.9+，如先跨 1.12.2）格挡/拉弓 ----
        // 每个移动 tick 的 POST 通过 Via 管线补发一个 1.9 协议的 Use Item，
        // 由 ViaRewind 之后的协议链翻译给服务器 —— 服务端按 1.9+ 分支
        // 认为你在持续使用物品（格挡外观/判定恢复），减速判定与 1.8 不同。
        if ((swordMode == "MoonVia" || bowPacket == "MoonVia") && event.eventState == EventState.POST &&
            isUsingItem && (heldItem.item is ItemSword || heldItem.item is ItemBow)
        ) {
            ViaUseItem.sendUseItem()
        }

        // ---- MoonLight 移植：GrimAC 金苹果假吃（跨版本吃金苹果不减速、不被打断）----
        if (consumeMode == "MoonGrim") {
            updateMoonGapple(heldItem)
        }

        // ---- BowDraw：吃食物过程中每 tick 强制发一个「拉弓包」（use item）----
        // 跨版本用 Via 管线注入 1.9 UseItem，直连用原生 C08 使用物品；
        // 让服务端始终认为你在持续进食，金苹果/药水吃到一半不被协议转换打断。
        // 发完继续走下面的 consumeMode 剩余逻辑（不 return，不干扰其它分支）。
        if (consumeMode == "BowDraw" && event.eventState == EventState.POST && isUsingItem &&
            isMoonConsumable()
        ) {
            if (ViaUseItem.isCrossVersionTo9Plus()) {
                ViaUseItem.sendUseItem()
            } else {
                sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, heldItem, 0f, 0f, 0f), false)
            }
        }

        if (!hasMotion && !shouldSwap)
            return@handler

        if (isUsingItem || shouldSwap) {
            if (heldItem.item !is ItemSword && heldItem.item !is ItemBow && (consumeFoodOnly && heldItem.item is ItemFood ||
                        consumeDrinkOnly && (heldItem.item is ItemPotion || heldItem.item is ItemBucketMilk))
            ) {
                when (consumeMode.lowercase()) {
                    "aac5" ->
                        sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, heldItem, 0f, 0f, 0f))

                    "switchitem" ->
                        if (event.eventState == EventState.PRE) {
                            updateSlot()
                        }

                    "updatedncp" ->
                        if (event.eventState == EventState.PRE && shouldSwap) {
                            updateSlot()
                            sendPacket(C08PacketPlayerBlockPlacement(BlockPos.ORIGIN, 255, heldItem, 0f, 0f, 0f))
                            shouldSwap = false
                        }

                    "invalidc08" -> {
                        if (event.eventState == EventState.PRE) {
                            if (InventoryUtils.hasSpaceInInventory()) {
                                if (player.ticksExisted % 3 == 0)
                                    sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 1, null, 0f, 0f, 0f))
                            }
                        }
                    }

                    "intave" -> {
                        if (event.eventState == EventState.PRE) {
                            sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.UP))
                        }
                    }

                    // ---- BlocksMC for consumeMode (port from BlocksMCNoSlow.java) ----
                    "blocksmc" -> {
                        // 只在 PRE 阶段计时并发送包（与原 Java 类行为相匹配）
                        if (event.eventState == EventState.PRE) {
                            consumeTickCycle++
                            if (consumeTickCycle == 1) {
                                mc.netHandler.addToSendQueue(C09PacketHeldItemChange((mc.thePlayer.inventory.currentItem + 1) % 9))
                                mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                                sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 0, heldItem, 0f, 0f, 0f))
                            }
                        }
                    }
                }
            }
        } else {
            consumeTickCycle = 0
        }

        if (heldItem.item is ItemBow && (isUsingItem || shouldSwap)) {
            when (bowPacket.lowercase()) {
                "aac5" ->
                    sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, heldItem, 0f, 0f, 0f))

                "silencegrim" ->
                    if (event.eventState == EventState.PRE) {
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem + 1))
                        mc.netHandler.addToSendQueue(C17PacketCustomPayload("sbhyt", PacketBuffer(Unpooled.buffer())))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                    } else {
                        sendPacket(C08PacketPlayerBlockPlacement(heldItem))
                    }

                "switchitem" ->
                    if (event.eventState == EventState.PRE) {
                        updateSlot()
                    }

                "updatedncp" ->
                    if (event.eventState == EventState.PRE && shouldSwap) {
                        updateSlot()
                        sendPacket(C08PacketPlayerBlockPlacement(BlockPos.ORIGIN, 255, heldItem, 0f, 0f, 0f))
                        shouldSwap = false
                    }

                "invalidc08" -> {
                    if (event.eventState == EventState.PRE) {
                        if (InventoryUtils.hasSpaceInInventory()) {
                            if (player.ticksExisted % 3 == 0)
                                sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 1, null, 0f, 0f, 0f))
                        }
                    }
                }
            }
        }

        if (heldItem.item is ItemSword && isUsingItem && !Gapple.eating) {
            when (swordMode.lowercase()) {
                "postplace" ->
                    if (event.eventState == EventState.PRE) {
                        sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    } else {
                        repeat(5) {
                            sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, heldItem, 0f, 0f, 0f))
                        }
                    }

                "grimac" ->
                    if (event.eventState == EventState.PRE) {
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1))
                        mc.netHandler.addToSendQueue(C17PacketCustomPayload("许锦良", PacketBuffer(Unpooled.buffer())))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                    } else {
                        repeat(5) {
                            sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, heldItem, 0f, 0f, 0f))
                        }
                    }

                // SilenceFix 的 Grim：PRE 切槽 + C17 "sbhyt" 空 payload 再切回，POST 只补一个 C08
                "silencegrim" ->
                    if (event.eventState == EventState.PRE) {
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem + 1))
                        mc.netHandler.addToSendQueue(C17PacketCustomPayload("sbhyt", PacketBuffer(Unpooled.buffer())))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                    } else {
                        sendPacket(C08PacketPlayerBlockPlacement(heldItem))
                    }


                "ncp" ->
                    when (event.eventState) {
                        EventState.PRE -> sendPacket(
                            C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN)
                        )

                        EventState.POST -> sendPacket(
                            C08PacketPlayerBlockPlacement(
                                BlockPos(-1, -1, -1), 255, heldItem, 0f, 0f, 0f
                            )
                        )

                        else -> return@handler
                    }

                "updatedncp" ->
                    if (event.eventState == EventState.POST) {
                        sendPacket(C08PacketPlayerBlockPlacement(BlockPos.ORIGIN, 255, heldItem, 0f, 0f, 0f))
                    }

                "aac5" ->
                    if (event.eventState == EventState.POST) {
                        sendPacket(
                            C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, player.heldItem, 0f, 0f, 0f)
                        )
                    }

                "switchitem" ->
                    if (event.eventState == EventState.PRE) {
                        updateSlot()
                    }

                "invalidc08" -> {
                    if (event.eventState == EventState.PRE) {
                        if (InventoryUtils.hasSpaceInInventory()) {
                            if (player.ticksExisted % 3 == 0)
                                sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 1, null, 0f, 0f, 0f))
                        }
                    }
                }
                "BlocksMC" -> {
                    if (!sent) {
                        sent = true
                        if (player.onGround) {
                            player.jump()
                        }
                    }

                    val slot = player.inventory.currentItem
                    val item = player.inventory.getStackInSlot(slot)

                    if (item != null && (item.unlocalizedName.contains(
                            "apple",
                            true
                        ) || item.unlocalizedName.contains("bow", true) || item.unlocalizedName.contains(
                            "potion",
                            true
                        ))
                    ) {
                        randomFactor = if (bmcOldOffset) {
                            0.5f + (Math.random() * 0.44).toFloat()
                        } else {
                            (Math.random() * 0.96).toFloat()
                        }

                        val playerPosition = player.position
                        val adjustedY = if (playerPosition.y > 0) playerPosition.y - 255 else playerPosition.y + 255
                        val inter = Vec3(playerPosition.x.toDouble(), adjustedY.toDouble(), playerPosition.z.toDouble())

                        sendPacket(
                            C08PacketPlayerBlockPlacement(
                                BlockPos(
                                    inter.xCoord.toInt(),
                                    inter.yCoord.toInt(),
                                    inter.zCoord.toInt()
                                ), 0, item, 0f, randomFactor, 0f
                            )
                        )

                        if (player.ticksExisted % bmcTicks == 0) {
                            // Additional logic if needed
                        }
                    }
                    return@handler
                }
                "grimtick" -> {
                    val currentTick = player.ticksExisted % 32
                    if (event.eventState == EventState.PRE) {
                        if (currentTick % 4 < 2) {
                            sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                        }
                    }
                }
            }
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        val player = mc.thePlayer ?: return@handler

        // ---- SilenceFix NoSlow（Grim 食物分支）----
        if (consumeMode == "SilenceSW" || consumeMode == "SilenceBW") {
            if (event.eventType == EventState.SEND) {
                val held = player.heldItem
                if (held != null) {
                    if (consumeMode == "SilenceSW") {
                        if (held.item is ItemAppleGold && held.stackSize - 1 >= 2) {
                            if (packet is C08PacketPlayerBlockPlacement &&
                                packet.position.y == -1 && !silenceIsSlow) {
                                // 假丢一个物品：服务端认为你停止了使用，减速不施加
                                sendPacket(
                                    C07PacketPlayerDigging(DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN),
                                    triggerEvent = false
                                )
                                silenceEatTicks = 0
                                silenceTempAppleCount = packet.stack?.stackSize ?: 0
                                silenceIsSlow = true
                                silenceStartedEat = true
                            }

                            // 假吃进行中：吞掉松开右键的 RELEASE 包，吃不会被打断
                            if (packet is C07PacketPlayerDigging &&
                                packet.status == RELEASE_USE_ITEM && silenceStartedEat) {
                                event.cancelEvent()
                            }
                        } else {
                            if (!(held.item is ItemSword || held.item is ItemBow) &&
                                packet is C08PacketPlayerBlockPlacement &&
                                packet.position.y == -1 && !silenceIsSlow) {
                                silenceIsSlow = true
                            }
                        }

                        if (packet is C09PacketHeldItemChange) {
                            silenceEatTicks = 0
                            silenceStartedEat = false
                        }
                    } else { // SilenceBW
                        if ((held.item is ItemFood || held.item is ItemPotion) &&
                            packet is C08PacketPlayerBlockPlacement &&
                            packet.position.y == -1 && !silenceIsSlow) {
                            // 原版行为：用特效系统命令打开一个假窗口骗过 Grim
                            player.sendChatMessage("/lizi open")
                            silenceIsSlow = true
                        }
                    }
                }
            } else if (event.eventType == EventState.RECEIVE) {
                if (packet is S2DPacketOpenWindow) {
                    // SW：只吞「特效系统」那个窗口标题
                    if (silenceIsSlow && packet.windowTitle != null &&
                        packet.windowTitle.formattedText == "§c§l          [- 特效系统- ]§r") {
                        event.cancelEvent()
                    }
                    // BW：拿着药水/金苹果时吞掉任何开窗包
                    val held = player.heldItem
                    if (consumeMode == "SilenceBW" && held != null &&
                        (held.item is ItemPotion || held.item is ItemAppleGold)) {
                        event.cancelEvent()
                        silenceXinXin = true
                        silenceIsSlow = false
                    }
                }
            }
        }

        // ---- MoonLight GrimAC：吞掉假丢包与服务器的槽位重置 ----
        if (consumeMode == "MoonGrim") {
            val held = player.heldItem
            if (player.isUsingItem && held?.item is ItemAppleGold && mc.gameSettings.keyBindUseItem.isKeyDown) {
                if (packet is C07PacketPlayerDigging && packet.status == C07PacketPlayerDigging.Action.DROP_ITEM) {
                    event.cancelEvent()
                }
            }
            if (held?.item is ItemAppleGold && packet is S2FPacketSetSlot && moonAnti()) {
                event.cancelEvent()
            }
        }

        if (event.isCancelled || shouldSwap)
            return@handler

        // Credit: @ManInMyVan
        // TODO: Not sure how to fix random grim simulation flag. (Seem to only happen in Loyisa).
        if (consumeMode == "Drop") {
            if (player.heldItem?.item !is ItemFood || !player.isMoving) {
                shouldNoSlow = false
                return@handler
            }

            val isUsingItem = packet is C08PacketPlayerBlockPlacement && packet.placedBlockDirection == 255

            if (!player.isUsingItem) {
                shouldNoSlow = false
                hasDropped = false
            }

            if (isUsingItem && !hasDropped) {
                sendPacket(C07PacketPlayerDigging(DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                shouldNoSlow = false
                hasDropped = true
            } else if (packet is S2FPacketSetSlot && player.isUsingItem) {
                if (packet.func_149175_c() != 0 || packet.func_149173_d() != SilentHotbar.currentSlot + 36) return@handler

                event.cancelEvent()
                shouldNoSlow = true

                player.itemInUse = packet.func_149174_e()
                if (!player.isUsingItem) player.itemInUseCount = 0
                player.inventory.mainInventory[SilentHotbar.currentSlot] = packet.func_149174_e()
            }
        }

        if (swordMode == "Blink") {
            when (packet) {
                is C00Handshake, is C00PacketServerQuery, is C01PacketPing, is C01PacketChatMessage, is S01PacketPong -> return@handler

                is C07PacketPlayerDigging, is C02PacketUseEntity, is C12PacketUpdateSign, is C19PacketResourcePackStatus -> {
                    BlinkTimer.update()
                    if (shouldBlink && BlinkTimer.hasTimePassed(reblinkTicks) && (BlinkUtils.packetsReceived.isNotEmpty() || BlinkUtils.packets.isNotEmpty())) {
                        BlinkUtils.unblink()
                        BlinkTimer.reset()
                        shouldBlink = false
                    } else if (!BlinkTimer.hasTimePassed(reblinkTicks)) {
                        shouldBlink = true
                    }
                    return@handler
                }

                // Flush on kb
                is S12PacketEntityVelocity -> {
                    if (mc.thePlayer.entityId == packet.entityID) {
                        BlinkUtils.unblink()
                        return@handler
                    }
                }

                // Flush on explosion
                is S27PacketExplosion -> {
                    if (packet.field_149153_g != 0f || packet.field_149152_f != 0f || packet.field_149159_h != 0f) {
                        BlinkUtils.unblink()
                        return@handler
                    }
                }

                is C03PacketPlayer -> {
                    if (swordMode == "Blink") {
                        if (player.isMoving) {
                            if (player.heldItem?.item is ItemSword && usingItemFunc()) {
                                if (shouldBlink)
                                    BlinkUtils.blink(packet, event)
                            } else {
                                shouldBlink = true
                                BlinkUtils.unblink()
                            }
                        }
                    }
                }
            }
        }

        when (packet) {
            is C08PacketPlayerBlockPlacement -> {
                if (packet.stack?.item != null && player.heldItem?.item != null && packet.stack.item == mc.thePlayer.heldItem?.item) {
                    if ((consumeMode == "UpdatedNCP" && (
                                packet.stack.item is ItemFood ||
                                        packet.stack.item is ItemPotion ||
                                        packet.stack.item is ItemBucketMilk)) ||
                        (bowPacket == "UpdatedNCP" && packet.stack.item is ItemBow)
                    ) {
                        shouldSwap = true
                    }
                }
            }
        }
    }

    val onSlowDown = handler<SlowDownEvent> { event ->
        val heldItem = mc.thePlayer.heldItem?.item

        // MoonLight GrimAC：食物减速由 foodSpeed 门控（假吃期间全速，否则 0.2）
        if (consumeMode == "MoonGrim" && heldItem !is ItemSword && mc.thePlayer.isUsingItem && isMoonConsumable()) {
            event.forward = moonFoodSpeed
            event.strafe = moonFoodSpeed
            return@handler
        }

        // SilenceFix：假动作包还没发（!isSlow）→ 完全不吃减速；发过之后交给服务端侧绕过
        if ((consumeMode == "SilenceSW" || consumeMode == "SilenceBW") && heldItem !is ItemSword) {
            if (!silenceIsSlow) {
                event.forward = 1f
                event.strafe = 1f
            }
            if (mc.thePlayer.isUsingItem && mc.thePlayer.moveForward >= 0f) {
                mc.thePlayer.setSprinting(true)
            }
            return@handler
        }

        if (heldItem !is ItemSword) {
            if (!consumeFoodOnly && heldItem is ItemFood ||
                !consumeDrinkOnly && (heldItem is ItemPotion || heldItem is ItemBucketMilk)
            ) {
                return@handler
            }

            if (consumeMode == "Drop" && !shouldNoSlow)
                return@handler
        }

        event.forward = getMultiplier(heldItem, true)
        event.strafe = getMultiplier(heldItem, false)
    }

    private fun getMultiplier(item: Item?, isForward: Boolean) = when (item) {
        is ItemFood, is ItemPotion, is ItemBucketMilk -> if (isForward) consumeForwardMultiplier else consumeStrafeMultiplier

        is ItemSword -> if (isForward) blockForwardMultiplier else blockStrafeMultiplier

        is ItemBow -> if (isForward) bowForwardMultiplier else bowStrafeMultiplier

        else -> 0.2F
    }

    fun isUNCPBlocking() =
        swordMode == "UpdatedNCP" && mc.gameSettings.keyBindUseItem.isKeyDown && (mc.thePlayer.heldItem?.item is ItemSword)


    fun usingItemFunc() =
        mc.thePlayer?.heldItem != null && (mc.thePlayer.isUsingItem || (mc.thePlayer.heldItem?.item is ItemSword && KillAura.blockStatus) || isUNCPBlocking())

    private fun updateSlot() {
        SilentHotbar.selectSlotSilently(this, (SilentHotbar.currentSlot + 1) % 9, immediate = true)
        SilentHotbar.resetSlot(this, true)
    }

    // ==================== MoonLight GrimAC 金苹果假吃（移植） ====================

    /**
     * MoonLight NoSlowdown GrimAC 的金苹果状态机（原 onMotion 的 GrimAC 分支）。
     * 按下使用键开始吃金苹果时发一个假 C07 DROP_ITEM（不触发事件）+ C0F 确认，
     * 服务端认为你丢了一个苹果、停止了「使用」，这一段的减速不施加；
     * 之后的 RELEASE / S2F 槽位重置全被吞掉，吃照常吃完（假吃期间 foodSpeed=1）。
     */
    private fun updateMoonGapple(heldItem: ItemStack) {
        if (heldItem.item !is ItemAppleGold || Gapple.eating) return

        if (mc.gameSettings.keyBindUseItem.isKeyDown) {
            if (moonEat) {
                if (heldItem.stackSize <= 1) return
                moonLastFoodAmount = heldItem.stackSize
                if (moonAnti()) {
                    moonFoodSpeed = 1f
                    sendPacket(C07PacketPlayerDigging(DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN), false)
                    sendPacket(C0FPacketConfirmTransaction())
                } else {
                    moonFoodSpeed = 0.2f
                }
                moonEat = false
            }

            if (!moonEat && heldItem.stackSize != moonLastFoodAmount) {
                moonFoodSpeed = 0.2f
                moonEat = true
            }
        } else if (!moonEat) {
            moonEat = true
            moonLastFoodAmount = 0
        }

        // 原 onUpdate 的复位：空闲时保持 0.2
        if (moonEat) {
            moonFoodSpeed = 0.2f
        }
    }

    /** 指着可交互方块（箱子/门等）或只剩 1 个苹果时不做假丢，避免误操作。 */
    private fun moonAnti(): Boolean {
        val player = mc.thePlayer ?: return false
        val held = player.heldItem ?: return false
        if (held.stackSize <= 1) return false
        val mop = mc.objectMouseOver ?: return true
        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && held.item is ItemFood) {
            val block = mc.theWorld?.getBlockState(mop.blockPos)?.block ?: return true
            if (isInteractBlock(block)) return false
        }
        return true
    }

    private fun isMoonConsumable(): Boolean {
        val held = mc.thePlayer.heldItem ?: return false
        val item = held.item
        return item is ItemFood ||
                item is ItemPotion && !ItemPotion.isSplash(held.metadata) ||
                item is ItemBucketMilk
    }

    private fun isInteractBlock(block: Block): Boolean {
        return block is BlockFence || block is BlockFenceGate || block is BlockDoor ||
                block is BlockChest || block is BlockEnderChest || block is BlockEnchantmentTable ||
                block is BlockFurnace || block is BlockAnvil || block is BlockBed ||
                block is BlockWorkbench || block is BlockNote || block is BlockTrapDoor ||
                block is BlockHopper || block is BlockDispenser || block is BlockDaylightDetector ||
                block is BlockRedstoneRepeater || block is BlockRedstoneComparator ||
                block is BlockButton || block is BlockBeacon || block is BlockBrewingStand ||
                block is BlockSign
    }
}
