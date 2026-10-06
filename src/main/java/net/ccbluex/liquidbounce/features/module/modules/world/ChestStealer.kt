/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
@file:Suppress("unused")

package net.ccbluex.liquidbounce.features.module.modules.world

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.LiquidBounce.hud
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.AutoArmor
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner.canBeSortedTo
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner.isStackUseful
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.extensions.component1
import net.ccbluex.liquidbounce.utils.extensions.component2
import net.ccbluex.liquidbounce.utils.extensions.eyes
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.canClickInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.chestStealerCurrentSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.chestStealerLastSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.countSpaceInInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.hasSpaceInInventory
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.timing.TickedActions.awaitTicked
import net.ccbluex.liquidbounce.utils.timing.TickedActions.clickNextTick
import net.ccbluex.liquidbounce.utils.timing.TickedActions.isTicked
import net.ccbluex.liquidbounce.utils.timing.TickedActions.nextTick
import net.ccbluex.liquidbounce.utils.timing.TimeUtils.randomDelay
import net.minecraft.block.BlockChest
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.entity.RenderItem
import net.minecraft.entity.EntityLiving.getArmorPosition
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.inventory.ContainerChest
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraft.network.play.server.S2EPacketCloseWindow
import net.minecraft.network.play.server.S30PacketWindowItems
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.BlockPos
import net.minecraft.util.MovingObjectPosition
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.sqrt

object ChestStealer : Module("ChestStealer", Category.WORLD) {

    private val smartDelay by boolean("SmartDelay", false)
    private val multiplier by int("DelayMultiplier", 120, 0..500) { smartDelay }
    private val smartOrder by boolean("SmartOrder", true) { smartDelay }

    private val simulateShortStop by boolean("SimulateShortStop", false)

    private val delay by intRange("Delay", 50..50, 0..500) { !smartDelay }
    private val startDelay by intRange("StartDelay", 50..100, 0..500)
    private val closeDelay by intRange("CloseDelay", 50..100, 0..500)

    private val noMove by +InventoryManager.noMoveValue
    private val noMoveAir by +InventoryManager.noMoveAirValue
    private val noMoveGround by +InventoryManager.noMoveGroundValue
    private val chestTitle by boolean("ChestTitle", true)

    private val randomSlot by boolean("RandomSlot", true)

    private val progressBar by boolean("ProgressBar", true).subjective()

    val silentGUI by boolean("SilentGUI", false).subjective()
    val silentView by boolean("SilentView", false)
    private val slientViewType by choices("Gui-Types", arrayOf("Chest","Circle"),"Chest") { silentView }
    val roundedView by int("RoundedView", 0, 0..10) { silentView }

    // ========== SilentGUI 背景与阴影设置 ==========
    val silentBackgroundColor by color("SilentBackgroundColor", Color(0, 0, 0, 180)) { silentView }
    val silentShadow by boolean("SilentShadow", true) { silentView }
    val silentShadowStrength by float("SilentShadowStrength", 6f, 0f..20f) { silentView && silentShadow }

    // ========== 跟随箱子并缩放 ==========
    private val followChest by boolean("FollowChest", true) { silentView && slientViewType == "Chest" }
    private val maxScaleDistance by float("MaxScaleDistance", 10f, 2f..20f) { followChest }

    val highlightSlot by boolean("Highlight-Slot", false) { !silentGUI }.subjective()
    val backgroundColor =
        color("BackgroundColor", Color(128, 128, 128)) { highlightSlot && !silentGUI }.subjective()

    val borderStrength by int("Border-Strength", 3, 1..5) { highlightSlot && !silentGUI }.subjective()
    val borderColor = color("BorderColor", Color(128, 128, 128)) { highlightSlot && !silentGUI }.subjective()

    private val chestDebug by choices("Chest-Debug", arrayOf("Off", "Text", "Notification"), "Off").subjective()
    private val itemStolenDebug by boolean("ItemStolen-Debug", false) { chestDebug != "Off" }.subjective()

    // 新增：不拿取药水
    private val noPotions by boolean("NoPotions", false).subjective()

    private var progress: Float? = null
        set(value) {
            field = value?.coerceIn(0f, 1f)

            if (field == null)
                easingProgress = 0f
        }

    private var easingProgress = 0f

    private var receivedId: Int? = null

    private var stacks = emptyList<ItemStack?>()

    // 当前打开箱子的位置与距离
    private var openedChestPos: BlockPos? = null
    private var openedChestDistance: Double = 0.0

    /**
     * 更新箱子位置：优先从容器获取，失败后射线检测
     */
    private fun updateOpenedChestInfo() {
        val screen = mc.currentScreen as? GuiChest ?: return
        val container = screen.inventorySlots
        if (container is ContainerChest) {
            val chest = container.lowerChestInventory
            if (chest is TileEntityChest) {
                openedChestPos = chest.pos
                updateDistanceToChest()
                return
            }
        }

        // 备用：射线检测
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return
        val eyes = player.eyes
        val look = player.lookVec
        val maxReach = mc.playerController.blockReachDistance.toDouble()
        val hit = world.rayTraceBlocks(
            eyes,
            eyes.addVector(look.xCoord * maxReach, look.yCoord * maxReach, look.zCoord * maxReach),
            false, true, false
        )
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            val block = world.getBlockState(hit.blockPos).block
            if (block is BlockChest) {
                openedChestPos = hit.blockPos
                updateDistanceToChest()
            }
        }
    }

    private fun updateDistanceToChest() {
        val pos = openedChestPos ?: return
        val player = mc.thePlayer ?: return
        openedChestDistance = sqrt(player.getDistanceSqToCenter(pos))
    }

    /**
     * 将世界坐标投影到屏幕坐标（使用渲染插值视角）
     */
    private fun projectToScreen(pos: BlockPos, partialTicks: Float): Pair<Float, Float>? {
        val renderView = mc.renderViewEntity ?: return null
        val renderManager = mc.renderManager

        val viewX = renderManager.viewerPosX
        val viewY = renderManager.viewerPosY
        val viewZ = renderManager.viewerPosZ

        val targetX = pos.x + 0.5 - viewX
        val targetY = pos.y + 1.0 - viewY
        val targetZ = pos.z + 0.5 - viewZ

        val yaw = Math.toRadians(-renderView.rotationYaw.toDouble())
        val pitch = Math.toRadians(-renderView.rotationPitch.toDouble())

        val cosYaw = Math.cos(yaw)
        val sinYaw = Math.sin(yaw)
        val cosPitch = Math.cos(pitch)
        val sinPitch = Math.sin(pitch)

        // 先绕 Y 轴旋转
        val x1 = targetX * cosYaw - targetZ * sinYaw
        val z1 = targetX * sinYaw + targetZ * cosYaw
        // 再绕 X 轴旋转
        val y1 = targetY * cosPitch - z1 * sinPitch
        val z2 = targetY * sinPitch + z1 * cosPitch

        if (z2 <= 0.1) return null

        val fov = mc.gameSettings.fovSetting
        val sr = ScaledResolution(mc)
        val halfW = sr.scaledWidth / 2.0
        val halfH = sr.scaledHeight / 2.0
        val tanFov = Math.tan(Math.toRadians(fov / 2.0))
        val screenX = halfW + (x1 / z2) * (halfH / tanFov) * (sr.scaledWidth.toDouble() / sr.scaledHeight.toDouble())
        val screenY = halfH - (y1 / z2) * (halfH / tanFov)

        return screenX.toFloat() to screenY.toFloat()
    }

    private suspend fun shouldOperate(): Boolean {
        while (true) {
            if (!handleEvents())
                return false

            if (mc.playerController?.currentGameType?.isSurvivalOrAdventure != true)
                return false

            if (mc.currentScreen !is GuiChest)
                return false

            if (mc.thePlayer?.openContainer?.windowId != receivedId)
                return false

            if (canClickInventory())
                return true

            delay(50)
        }
    }

    suspend fun stealFromChest() {
        if (!handleEvents())
            return

        val thePlayer = mc.thePlayer ?: return

        val screen = mc.currentScreen ?: return

        if (screen !is GuiChest || !shouldOperate())
            return

        if (chestTitle && Blocks.chest.localizedName !in (screen.lowerChestInventory ?: return).name)
            return

        updateOpenedChestInfo()

        progress = 0f

        delay(startDelay.random().toLong())

        debug("Stealing items..")

        while (true) {
            if (!shouldOperate())
                return

            if (!hasSpaceInInventory())
                return

            var hasTaken = false

            val itemsToSteal = getItemsToSteal()

            run scheduler@{
                itemsToSteal.forEachIndexed { index, (slot, stack, sortableTo) ->
                    if (!shouldOperate()) {
                        nextTick { SilentHotbar.resetSlot() }
                        chestStealerCurrentSlot = -1
                        chestStealerLastSlot = -1
                        return
                    }

                    if (!hasSpaceInInventory()) {
                        chestStealerCurrentSlot = -1
                        chestStealerLastSlot = -1
                        return@scheduler
                    }

                    hasTaken = true

                    chestStealerCurrentSlot = slot

                    val stealingDelay = if (smartDelay && index + 1 < itemsToSteal.size) {
                        val dist = squaredDistanceOfSlots(slot, itemsToSteal[index + 1].index)
                        val trueDelay = sqrt(dist.toDouble()) * multiplier
                        randomDelay(trueDelay.toInt(), trueDelay.toInt() + 20)
                    } else {
                        delay.random()
                    }

                    if (itemStolenDebug) debug("item: ${stack.displayName.lowercase()} | slot: $slot | delay: ${stealingDelay}ms")

                    clickNextTick(slot, sortableTo ?: 0, if (sortableTo != null) 2 else 1) {
                        progress = (index + 1) / itemsToSteal.size.toFloat()

                        if (!AutoArmor.canEquipFromChest())
                            return@clickNextTick

                        val item = stack.item

                        if (item !is ItemArmor || thePlayer.inventory.armorInventory[getArmorPosition(stack) - 1] != null)
                            return@clickNextTick

                        nextTick {
                            val hotbarStacks = thePlayer.inventory.mainInventory.take(9)
                            val newIndex = hotbarStacks.indexOfFirst { it?.getIsItemStackEqual(stack) == true }

                            if (newIndex != -1)
                                AutoArmor.equipFromHotbarInChest(newIndex, stack)
                        }
                    }

                    delay(stealingDelay.toLong())

                    if (simulateShortStop && Math.random() > 0.75) {
                        val minDelays = randomDelay(150, 300)
                        val maxDelays = randomDelay(minDelays, 500)
                        val randomDelay = randomDelay(minDelays, maxDelays).toLong()

                        delay(randomDelay)
                    }
                }
            }

            if (!hasTaken) {
                progress = 1f
                delay(closeDelay.random().toLong())

                nextTick { SilentHotbar.resetSlot() }
                break
            }

            awaitTicked()

            stacks = thePlayer.openContainer.inventory
        }

        nextTick {
            chestStealerCurrentSlot = -1
            chestStealerLastSlot = -1
            thePlayer.closeScreen()
            progress = null
            openedChestPos = null
            openedChestDistance = 0.0

            debug("Chest closed")
        }

        awaitTicked()
    }

    private fun squaredDistanceOfSlots(from: Int, to: Int): Int {
        fun getCoords(slot: Int): IntArray {
            val x = slot % 9
            val y = slot / 9
            return intArrayOf(x, y)
        }

        val (x1, y1) = getCoords(from)
        val (x2, y2) = getCoords(to)
        return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
    }

    private data class ItemTakeRecord(
        val index: Int,
        val stack: ItemStack,
        val sortableToSlot: Int?
    )

    private fun getItemsToSteal(): List<ItemTakeRecord> {
        val sortBlacklist = BooleanArray(9)
        var spaceInInventory = countSpaceInInventory()

        val itemsToSteal = stacks.dropLast(36)
            .mapIndexedNotNullTo(ArrayList(32)) { index, stack ->
                stack ?: return@mapIndexedNotNullTo null

                if (isTicked(index)) return@mapIndexedNotNullTo null

                // 新增：如果开启 NoPotions，跳过药水
                if (noPotions && stack.item is ItemPotion)
                    return@mapIndexedNotNullTo null

                val mergeableCount = mc.thePlayer.inventory.mainInventory.sumOf { otherStack ->
                    otherStack ?: return@sumOf 0

                    if (otherStack.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(stack, otherStack))
                        otherStack.maxStackSize - otherStack.stackSize
                    else 0
                }

                val canMerge = mergeableCount > 0
                val canFullyMerge = mergeableCount >= stack.stackSize

                if (!canMerge && spaceInInventory <= 0) return@mapIndexedNotNullTo null

                // 护甲始终拿取，不参与 InventoryCleaner 的有用性过滤
                if (stack.item !is ItemArmor) {
                    if (InventoryCleaner.handleEvents() && !isStackUseful(stack, stacks, noLimits = canFullyMerge))
                        return@mapIndexedNotNullTo null
                }

                var sortableTo: Int? = null

                if (!canMerge && InventoryCleaner.handleEvents() && InventoryCleaner.sort) {
                    for (hotbarIndex in 0..8) {
                        if (sortBlacklist[hotbarIndex])
                            continue

                        if (!canBeSortedTo(hotbarIndex, stack.item))
                            continue

                        val hotbarStack = stacks.getOrNull(stacks.size - 9 + hotbarIndex)

                        if (!canBeSortedTo(hotbarIndex, hotbarStack?.item) || !isStackUseful(
                                hotbarStack,
                                stacks,
                                strictlyBest = true
                            )
                        ) {
                            sortableTo = hotbarIndex
                            sortBlacklist[hotbarIndex] = true
                            break
                        }
                    }
                }

                if (!canFullyMerge) spaceInInventory--

                ItemTakeRecord(index, stack, sortableTo)
            }.also { it ->
                if (randomSlot)
                    it.shuffle()

                it.sortByDescending { it.stack.item is ItemArmor }
                it.sortByDescending { it.sortableToSlot != null }

                if (AutoArmor.canEquipFromChest())
                    it.sortByDescending { it.stack.item is ItemArmor }

                if (smartOrder) {
                    sortBasedOnOptimumPath(it)
                }
            }

        return itemsToSteal
    }

    private fun sortBasedOnOptimumPath(itemsToSteal: MutableList<ItemTakeRecord>) {
        for (i in itemsToSteal.indices) {
            var nextIndex = i
            var minDistance = Int.MAX_VALUE
            var next: ItemTakeRecord? = null
            for (j in i + 1 until itemsToSteal.size) {
                val distance = squaredDistanceOfSlots(itemsToSteal[i].index, itemsToSteal[j].index)
                if (distance < minDistance) {
                    minDistance = distance
                    next = itemsToSteal[j]
                    nextIndex = j
                }
            }
            if (next != null) {
                itemsToSteal[nextIndex] = itemsToSteal[i + 1]
                itemsToSteal[i + 1] = next
            }
        }
    }

    // Progress bar
    val onRender2D = handler<Render2DEvent> { event ->
        if (!progressBar || mc.currentScreen !is GuiChest)
            return@handler

        val progress = progress ?: return@handler

        val (scaledWidth, scaledHeight) = ScaledResolution(mc)

        val minX = scaledWidth * 0.3f
        val maxX = scaledWidth * 0.7f
        val minY = scaledHeight * 0.75f
        val maxY = minY + 10f

        easingProgress += (progress - easingProgress) / 6f * event.partialTicks

        drawRect(minX - 2, minY - 2, maxX + 2, maxY + 2, Color(200, 200, 200).rgb)
        drawRect(minX, minY, maxX, maxY, Color(50, 50, 50).rgb)
        drawRect(
            minX,
            minY,
            minX + (maxX - minX) * easingProgress,
            maxY,
            Color.HSBtoRGB(easingProgress / 5, 1f, 1f) or 0xFF0000
        )
    }

    val onPacket = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is C0DPacketCloseWindow, is S2DPacketOpenWindow, is S2EPacketCloseWindow -> {
                receivedId = null
                progress = null
                openedChestPos = null
                openedChestDistance = 0.0
            }

            is S30PacketWindowItems -> {
                val packetWindowId = packet.func_148911_c()

                if (packetWindowId == 0)
                    return@handler

                if (receivedId != packetWindowId) {
                    debug("Chest opened with ${stacks.size} items")
                }

                receivedId = packetWindowId
                stacks = packet.itemStacks.toList()

                updateOpenedChestInfo()
            }
        }
    }

    // SilentView 渲染
    val onRender2DSilentView = handler<Render2DEvent> { _ ->
        if (!silentView) return@handler

        if (mc.currentScreen !is GuiChest) return@handler
        val player = mc.thePlayer ?: return@handler
        val container = player.openContainer ?: return@handler

        val sr = ScaledResolution(mc)
        val centerX = sr.scaledWidth / 2f
        val centerY = sr.scaledHeight / 2f

        when (slientViewType) {
            "Chest" -> {
                val chestSlots = container.inventorySlots.filter { it.inventory != player.inventory }
                if (chestSlots.isEmpty()) return@handler

                val columns = 9
                val rows = (chestSlots.size + 8) / 9
                val slotSize = 18
                val pad = 5

                val baseBoxW = (columns * slotSize + pad * 2).toFloat()
                val baseBoxH = (rows * slotSize + pad * 2).toFloat()

                // 实时更新距离
                updateDistanceToChest()

                // 根据距离计算缩放因子
                var targetScale = 1.0f
                if (openedChestDistance > 0.0 && maxScaleDistance > 0.0) {
                    targetScale = (1.0f - (openedChestDistance / maxScaleDistance).toFloat()).coerceIn(0.3f, 1.0f)
                }
                val boxW = baseBoxW * targetScale
                val boxH = baseBoxH * targetScale

                // 默认居中
                var windowX = centerX - boxW / 2f
                var windowY = centerY - boxH / 2f

                // 跟随箱子
                if (followChest && openedChestPos != null) {
                    val screenPos = projectToScreen(openedChestPos!!, mc.timer.renderPartialTicks)
                    if (screenPos != null) {
                        windowX = screenPos.first - boxW / 2f
                        windowY = screenPos.second - boxH - 10f  // 向上偏移
                    }
                }

                // 阴影
                if (silentShadow) {
                    GlowUtils.drawGlow(
                        windowX,
                        windowY,
                        boxW,
                        boxH,
                        silentShadowStrength.toInt(),
                        Color(0, 0, 0, 120)
                    )
                }

                // 模糊背景
                if (BlurSettings.active && BlurSettings.chestStealer) {
                    BlurUtils.drawOffsetBlur(
                        windowX * sr.scaleFactor,
                        windowY * sr.scaleFactor,
                        boxW * sr.scaleFactor,
                        boxH * sr.scaleFactor,
                        BlurSettings.passes,
                        0f,
                        roundedView.toFloat() * sr.scaleFactor
                    )
                }

                // 绘制圆角背景
                drawRoundedRect(
                    windowX - 1,
                    windowY - 1,
                    windowX + boxW + 1,
                    windowY + boxH + 1,
                    silentBackgroundColor.rgb,
                    roundedView.toFloat()
                )

                // 渲染物品
                val itemRender: RenderItem = mc.renderItem
                RenderHelper.enableGUIStandardItemLighting()
                GlStateManager.disableDepth()
                itemRender.zLevel = 200.0f

                GL11.glPushMatrix()
                GL11.glTranslatef(windowX + pad * targetScale, windowY + pad * targetScale, 0f)
                GL11.glScalef(targetScale, targetScale, targetScale)

                chestSlots.forEach { slot ->
                    val stack = slot.stack ?: return@forEach
                    val col = slot.slotNumber % 9
                    val row = slot.slotNumber / 9
                    val ix = (col * slotSize).toInt()
                    val iy = (row * slotSize).toInt()
                    itemRender.renderItemAndEffectIntoGUI(stack, ix, iy)
                    itemRender.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, ix, iy, null)
                }

                GL11.glPopMatrix()

                itemRender.zLevel = 0.0f
                GlStateManager.enableDepth()
                RenderHelper.disableStandardItemLighting()
            }
            "Circle" -> {
                val time = System.currentTimeMillis() % 2000 / 2000.0f * (2 * Math.PI).toFloat()
                val radius = 12f
                val text = "Stealing"

                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
                GL11.glPushMatrix()

                try {
                    GL11.glEnable(GL11.GL_BLEND)
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
                    GL11.glEnable(GL11.GL_LINE_SMOOTH)
                    GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST)
                    GL11.glDisable(GL11.GL_TEXTURE_2D)

                    GL11.glLineWidth(3.0f)

                    GL11.glBegin(GL11.GL_LINE_STRIP)
                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.8f)

                    for (i in 0..180) {
                        val angle = time + Math.toRadians(i.toDouble()).toFloat()
                        val x = centerX + radius * kotlin.math.cos(angle)
                        val y = centerY + radius * kotlin.math.sin(angle)
                        GL11.glVertex2f(x, y)
                    }

                    GL11.glEnd()

                    GL11.glEnable(GL11.GL_TEXTURE_2D)
                    GL11.glDisable(GL11.GL_LINE_SMOOTH)

                    val textY = centerY + radius + 10f
                    val font = mc.fontRendererObj
                    val textWidth = Fonts.fontRegular35.getStringWidth(text)
                    val textX = centerX - textWidth / 2f

                    Fonts.fontRegular35.drawString(text, textX, textY, Color.WHITE.rgb)

                } finally {
                    GL11.glPopMatrix()
                    GL11.glPopAttrib()
                }
            }
        }
    }

    private fun debug(message: String) {
        if (chestDebug == "Off") return

        when (chestDebug.lowercase()) {
            "text" -> chat(message)
            "notification" -> hud.addNotification(Notification.informative(this, message, 500L))
        }
    }
}