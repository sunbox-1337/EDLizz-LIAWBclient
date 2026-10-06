/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.config.ListValue
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.AutoArmor
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.block.BlockUtils.isFullBlock
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.extensions.shuffled
import net.ccbluex.liquidbounce.utils.inventory.*
import net.ccbluex.liquidbounce.utils.inventory.ArmorComparator.getBestArmorSet
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.canClickInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.hasScheduledInLastLoop
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.invCleanerCurrentSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.invCleanerLastSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.passedPostInventoryCloseDelay
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.isFirstInventoryClick
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.toHotbarIndex
import net.ccbluex.liquidbounce.utils.timing.TickedActions.awaitTicked
import net.ccbluex.liquidbounce.utils.timing.TickedActions.clickNextTick
import net.ccbluex.liquidbounce.utils.timing.TickedActions.isTicked
import net.minecraft.block.BlockContainer
import net.minecraft.block.BlockFalling
import net.minecraft.block.BlockWorkbench
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.*
import net.minecraft.potion.Potion

object InventoryCleaner : Module("InventoryCleaner", Category.PLAYER) {
    private val drop by boolean("Drop", true).subjective()
    val sort by boolean("Sort", true).subjective()

    private val delay by intRange("Delay", 50..50, 0..1000)

    private val minItemAge by int("MinItemAge", 0, 0..2000)

    private val limitStackCounts by boolean("LimitStackCounts", true).subjective()
    private val maxBlockStacks by int("MaxBlockStacks", 5, 0..36) { limitStackCounts }.subjective()
    private val maxFoodStacks by int("MaxFoodStacks", 5, 0..36) { limitStackCounts }.subjective()
    private val maxThrowableStacks by int(
        "MaxThrowableStacks",
        5,
        0..36,
    ) { limitStackCounts }.subjective()

    private val maxFishingRodStacks by int("MaxFishingRodStacks", 1, 1..10).subjective()

    // 新增选项：是否在背包中多保留一套备用护甲
    private val extraArmorSet by boolean("ExtraArmorSet", true).subjective()

    // 新增选项：额外保留的最佳剑数量（0~5）
    private val extraSwords by int("ExtraSwords", 1, 0..5).subjective()

    // 新增选项：额外保留的最差剑数量（0~5）
    private val worstSwords by int("WorstSwords", 0, 0..5).subjective()

    private val mergeStacks by boolean("MergeStacks", true).subjective()

    private val repairEquipment by boolean("RepairEquipment", true).subjective()

    // 新增：KillAura 有目标时暂停整理
    private val pauseOnKillAura by boolean("PauseOnKillAura", true).subjective()

    private val invOpen by +InventoryManager.invOpenValue
    private val simulateInventory by +InventoryManager.simulateInventoryValue

    private val postInventoryCloseDelay by +InventoryManager.postInventoryCloseDelayValue
    private val autoClose by +InventoryManager.autoCloseValue
    private val startDelay by +InventoryManager.startDelayValue
    private val closeDelay by +InventoryManager.closeDelayValue

    private val noMove by +InventoryManager.noMoveValue
    private val noMoveAir by +InventoryManager.noMoveAirValue
    private val noMoveGround by +InventoryManager.noMoveGroundValue

    private val randomSlot by boolean("RandomSlot", false)
    private val ignoreVehicles by boolean("IgnoreVehicles", false).subjective()

    private val onlyGoodPotions by boolean("OnlyGoodPotions", false).subjective()
    // 新增：不保留任何药水
    private val noPotions by boolean("NoPotions", false).subjective()

    val highlightSlot by +InventoryManager.highlightSlotValue
    val backgroundColor by +InventoryManager.borderColor

    val borderStrength by +InventoryManager.borderStrength
    val borderColor by +InventoryManager.borderColor

    val highlightUseful by boolean("HighlightUseful", true).subjective()

    private val slot1Value = sortChoice("Slot1", "Sword")
    private val slot2Value = sortChoice("Slot2", "Bow")
    private val slot3Value = sortChoice("Slot3", "Pickaxe")
    private val slot4Value = sortChoice("Slot4", "Axe")
    private val slot5Value = sortChoice("Slot5", "Shovel")
    private val slot6Value = sortChoice("Slot6", "Food")
    private val slot7Value = sortChoice("Slot7", "Throwable")
    private val slot8Value = sortChoice("Slot8", "FishingRod")
    private val slot9Value = sortChoice("Slot9", "Block")

    private val SORTING_VALUES: Array<ListValue> = arrayOf(
        slot1Value, slot2Value, slot3Value, slot4Value, slot5Value, slot6Value, slot7Value, slot8Value, slot9Value
    )

    private suspend fun shouldOperate(): Boolean {
        while (true) {
            if (!handleEvents())
                return false

            if (!passedPostInventoryCloseDelay)
                return false

            // 新增：KillAura 有目标时暂停整理
            if (pauseOnKillAura && KillAura.handleEvents() && KillAura.target != null) {
                delay(50)
                continue
            }

            if (mc.playerController?.currentGameType?.isSurvivalOrAdventure != true)
                return false

            if (mc.thePlayer?.openContainer?.windowId != 0)
                return false

            if (invOpen && mc.currentScreen !is GuiInventory)
                return false

            if (canClickInventory(closeWhenViolating = true))
                return true

            delay(50)
        }
    }

    suspend fun mergeStacks() {
        if (!mergeStacks || !shouldOperate())
            return

        val thePlayer = mc.thePlayer ?: return

        while (true) {
            if (!shouldOperate()) return

            val stacks = thePlayer.openContainer.inventory

            val indicesToDoubleClick = stacks.withIndex()
                .groupBy { it.value?.item }
                .mapNotNull { (item, groupedStacks) ->
                    item ?: return@mapNotNull null

                    val sortedStacks = groupedStacks
                        .filter {
                            it.value.hasItemAgePassed(minItemAge) &&
                                    it.value.stackSize != it.value.maxStackSize && isStackUseful(
                                it.value,
                                stacks,
                                noLimits = true
                            )
                        }
                        .sortedByDescending { it.index }
                        .sortedByDescending { canBeSortedTo(it.index, it.value?.item, stacks.size) }

                    sortedStacks.firstOrNull { (_, clickedStack) ->
                        sortedStacks.any { (_, stackToMerge) ->
                            clickedStack != stackToMerge
                                    && clickedStack.stackSize + stackToMerge.stackSize <= clickedStack.maxStackSize
                                    && clickedStack.isItemEqual(stackToMerge)
                                    && ItemStack.areItemStackTagsEqual(clickedStack, stackToMerge)
                        }
                    }?.index
                }

            for (index in indicesToDoubleClick) {
                if (!shouldOperate()) return
                if (isTicked(index)) continue

                click(index, 0, 0, coerceTo = 100)
                click(index, 0, 6, allowDuplicates = true, coerceTo = 100)
                click(index, 0, 0, allowDuplicates = true, coerceTo = 100)
            }

            if (indicesToDoubleClick.isEmpty())
                break

            awaitTicked()
        }
    }

    private data class RepairTriple(val index1: Int, val index2: Int, val durability: Int)

    suspend fun repairEquipment() {
        if (!repairEquipment || !shouldOperate())
            return

        val thePlayer = mc.thePlayer ?: return

        while (true) {
            if (!shouldOperate()) return

            val stacks = thePlayer.openContainer.inventory

            val pairsToRepair = stacks.withIndex()
                .filter { (_, stack) ->
                    stack.hasItemAgePassed(minItemAge) && shouldBeRepaired(stack)
                }
                .groupBy { it.value.item }.values
                .filter { stackGroup ->
                    stackGroup.any { isStackUseful(it.value, stacks, noLimits = true) && it.value.isItemDamaged }
                }
                .mapNotNull { groupStacks ->
                    groupStacks.withIndex().flatMap { (index, indexedStack) ->
                        groupStacks.drop(index + 1).map { indexedStack to it }
                    }.mapNotNull {
                        val (index1, stack1) = it.first
                        val (index2, stack2) = it.second

                        getCombinedDurabilityIfBeneficial(stack1, stack2)?.let { durability ->
                            RepairTriple(index1, index2, durability)
                        }
                    }.maxByOrNull { it.durability }?.takeIf { bestCombination ->
                        bestCombination.durability >= groupStacks.maxOf { it.value.totalDurability }
                    }
                }

            repair@ for ((index1, index2) in pairsToRepair) {
                if (!shouldOperate()) return
                if (isTicked(index1) || isTicked(index2)) continue

                click(index1, 0, 0)
                click(1, 0, 0)
                click(index2, 0, 0)
                click(2, 0, 0)

                val repairedStack = thePlayer.openContainer.getSlot(0).stack
                val repairedItem = repairedStack.item

                if (repairedItem is ItemArmor) {
                    val armorSlot = repairedItem.armorType + 5
                    var equipAfterCrafting = true

                    if (thePlayer.openContainer.getSlot(armorSlot).hasStack) {
                        when {
                            AutoArmor.handleEvents() && AutoArmor.smartSwap -> {
                                click(0, 0, 0)
                                click(armorSlot, 0, 0)
                                click(-999, 0, 0)
                                continue@repair
                            }
                            drop || AutoArmor.handleEvents() -> click(armorSlot, 0, 4)
                            else -> equipAfterCrafting = false
                        }
                    }

                    if (equipAfterCrafting) {
                        click(0, 0, 0)
                        click(repairedItem.armorType + 5, 0, 0)
                        continue@repair
                    }
                }

                if (sort) {
                    for (hotbarIndex in 0..8) {
                        if (!canBeSortedTo(hotbarIndex, repairedItem)) continue

                        val hotbarStack = stacks.getOrNull(stacks.size - 9 + hotbarIndex)

                        if (!canBeSortedTo(hotbarIndex, hotbarStack?.item)
                            || !isStackUseful(hotbarStack, stacks, strictlyBest = true)
                        ) {
                            click(0, hotbarIndex, 2)
                            continue@repair
                        }
                    }
                }

                click(0, 0, 1)
            }

            if (pairsToRepair.isEmpty()) break
            awaitTicked()
        }
    }

    suspend fun sortHotbar() {
        if (!sort || !shouldOperate()) return

        val thePlayer = mc.thePlayer ?: return

        hotbarLoop@ for ((hotbarIndex, value) in SORTING_VALUES.withIndex().shuffled(randomSlot)) {
            val isRightType = SORTING_TARGETS[value.get()] ?: continue

            if (!shouldOperate()) return

            val stacks = thePlayer.openContainer.inventory
            val index = hotbarIndex + 36
            val stack = stacks.getOrNull(index)
            val item = stack?.item

            suspend fun searchAndSort(strictlyBest: Boolean = false): Boolean {
                if (isRightType(item) && isStackUseful(stack, stacks, strictlyBest = strictlyBest))
                    return true

                for ((otherIndex, otherStack) in stacks.withIndex()) {
                    if (isTicked(otherIndex)) continue

                    val otherItem = otherStack?.item

                    if (isRightType(otherItem) && isStackUseful(
                            otherStack,
                            stacks,
                            strictlyBest = strictlyBest
                        ) && !canBeSortedTo(otherIndex, otherItem, stacks.size)
                    ) {
                        if (otherStack.hasItemAgePassed(minItemAge))
                            click(otherIndex, hotbarIndex, 2)
                        return true
                    }
                }
                return false
            }

            if (!searchAndSort(strictlyBest = true))
                searchAndSort()
        }

        awaitTicked()
    }

    suspend fun dropGarbage() {
        if (!drop || !shouldOperate()) return

        val thePlayer = mc.thePlayer ?: return

        for (index in thePlayer.openContainer.inventorySlots.indices.shuffled(randomSlot)) {
            if (!shouldOperate()) return
            if (isTicked(index)) continue

            val stacks = thePlayer.openContainer.inventory
            val stack = stacks.getOrNull(index) ?: continue

            if (!stack.hasItemAgePassed(minItemAge)) continue

            if (!isStackUseful(stack, stacks))
                click(index, 1, 4)
        }

        awaitTicked()
    }

    private suspend fun click(
        slot: Int, button: Int, mode: Int, allowDuplicates: Boolean = false,
        coerceTo: Int = Int.MAX_VALUE,
    ) {
        if (!shouldOperate()) {
            invCleanerCurrentSlot = -1
            invCleanerLastSlot = -1
            return
        }

        invCleanerCurrentSlot = slot

        if (simulateInventory || invOpen)
            serverOpenInventory = true

        if (isFirstInventoryClick) {
            isFirstInventoryClick = false
            delay(startDelay.toLong())
        }

        clickNextTick(slot, button, mode, allowDuplicates)
        hasScheduledInLastLoop = true
        delay(delay.random().coerceAtMost(coerceTo).toLong())
    }

    fun canBeSortedTo(index: Int, item: Item?, stacksSize: Int? = null): Boolean {
        if (!sort) return false

        val index =
            if (stacksSize != null) index.toHotbarIndex(stacksSize) ?: return false
            else index

        return SORTING_TARGETS[SORTING_VALUES.getOrNull(index)?.get()]?.invoke(item) == true
    }

    fun isStackUseful(
        stack: ItemStack?, stacks: List<ItemStack?>, entityStacksMap: Map<ItemStack, EntityItem>? = null,
        noLimits: Boolean = false, strictlyBest: Boolean = false,
    ): Boolean {
        val item = stack?.item ?: return false

        return when (item) {
            in ITEMS_WHITELIST -> true
            is ItemEnderPearl, is ItemEnchantedBook, is ItemBed -> true
            is ItemFood -> isUsefulFood(stack, stacks, entityStacksMap, noLimits, strictlyBest)
            is ItemBlock -> isUsefulBlock(stack, stacks, entityStacksMap, noLimits, strictlyBest)
            is ItemArmor, is ItemTool, is ItemSword, is ItemBow, is ItemFishingRod, is ItemShears -> isUsefulEquipment(
                stack, stacks, entityStacksMap
            )
            is ItemBoat, is ItemMinecart -> !ignoreVehicles
            is ItemPotion -> isUsefulPotion(stack)
            is ItemBucket -> isUsefulBucket(stack, stacks, entityStacksMap)
            is ItemFlintAndSteel -> isUsefulLighter(stack, stacks, entityStacksMap)
            in THROWABLE_ITEMS -> isUsefulThrowable(stack, stacks, entityStacksMap, noLimits, strictlyBest)
            else -> false
        }
    }

    private fun isUsefulEquipment(
        stack: ItemStack?, stacks: List<ItemStack?>,
        entityStacksMap: Map<ItemStack, EntityItem>? = null,
    ): Boolean {
        val item = stack?.item ?: return false

        return when (item) {
            is ItemArmor -> {
                if (extraArmorSet) {
                    val bestSet = getBestArmorSet(stacks, entityStacksMap)
                    if (stack in bestSet) return true

                    val remainingStacks = stacks.map { s ->
                        if (s != null && s in bestSet) null else s
                    }
                    val remainingGround = entityStacksMap?.filterKeys { it !in bestSet }
                    val secondBestSet = getBestArmorSet(remainingStacks, remainingGround)

                    stack in secondBestSet
                } else {
                    stack in getBestArmorSet(stacks, entityStacksMap)
                }
            }

            is ItemTool -> {
                val blockType = when (item) {
                    is ItemAxe -> Blocks.log
                    is ItemPickaxe -> Blocks.stone
                    else -> Blocks.dirt
                }
                hasBestParameters(stack, stacks, entityStacksMap) {
                    it.item.getStrVsBlock(it, blockType) * it.durability
                }
            }

            is ItemFishingRod -> {
                val fishingRod = stacks.count { it?.item is ItemFishingRod }
                if (fishingRod <= maxFishingRodStacks) return true
                hasBestParameters(stack, stacks, entityStacksMap) { it.durability.toFloat() }
            }

            is ItemShears ->
                hasBestParameters(stack, stacks, entityStacksMap) {
                    it.durability.toFloat() * it.getEnchantmentLevel(Enchantment.efficiency)
                }

            is ItemSword -> isAmongBestSwords(stack, stacks, entityStacksMap)

            is ItemBow ->
                hasBestParameters(stack, stacks, entityStacksMap) { it.getEnchantmentLevel(Enchantment.power).toFloat() }

            else -> false
        }
    }

    private fun isAmongBestSwords(
        stack: ItemStack?, stacks: List<ItemStack?>,
        entityStacksMap: Map<ItemStack, EntityItem>?,
    ): Boolean {
        val item = stack?.item ?: return false
        if (item !is ItemSword) return false

        val allSwords = mutableListOf<ItemStack>()
        allSwords.addAll(stacks.filterNotNull().filter { it.item is ItemSword })
        if (!entityStacksMap.isNullOrEmpty()) {
            allSwords.addAll(entityStacksMap.keys.filter { it.item is ItemSword })
        }

        if (allSwords.isEmpty()) return false

        val sortedSwords = allSwords.sortedWith(
            compareByDescending<ItemStack> { it.attackDamage }
                .thenByDescending { it.enchantmentSum }
                .thenByDescending { it.totalDurability }
                .thenBy { sword ->
                    val idx = stacks.indexOf(sword)
                    if (idx >= 0 && canBeSortedTo(idx, sword.item, stacks.size)) 0 else 1
                }
                .thenBy { sword -> stacks.indexOf(sword) }
        )

        val bestCount = 1 + extraSwords
        val bestSwords = sortedSwords.take(bestCount)

        if (stack in bestSwords) return true

        if (worstSwords > 0) {
            val remainingSwords = sortedSwords.drop(bestCount)
            val worstSwordsList = remainingSwords.takeLast(worstSwords)
            if (stack in worstSwordsList) return true
        }

        return false
    }

    private fun isUsefulPotion(stack: ItemStack?): Boolean {
        if (noPotions) return false  // 新增：不保留任何药水
        val item = stack?.item ?: return false
        if (item !is ItemPotion) return false

        val isSplash = stack.isSplashPotion()
        val isHarmful = item.getEffects(stack)?.any { it.potionID in NEGATIVE_EFFECT_IDS } ?: return false
        return !isHarmful || (!onlyGoodPotions && isSplash)
    }

    private fun isUsefulLighter(
        stack: ItemStack?, stacks: List<ItemStack?>,
        entityStacksMap: Map<ItemStack, EntityItem>? = null,
    ): Boolean {
        val item = stack?.item ?: return false
        if (item !is ItemFlintAndSteel) return false

        val index = stacks.indexOf(stack)
        val isSorted = canBeSortedTo(index, item, stacks.size)
        if (isSorted) return true

        val stacksToIterate = stacks.toMutableList()
        var distanceSqToItem = .0

        if (!entityStacksMap.isNullOrEmpty()) {
            distanceSqToItem = mc.thePlayer.getDistanceSqToEntity(entityStacksMap[stack] ?: return false)
            stacksToIterate += entityStacksMap.keys
        }

        return stacksToIterate.withIndex().none { (otherIndex, otherStack) ->
            if (otherStack == stack) return@none false
            val otherItem = otherStack?.item ?: return@none false
            if (otherItem !is ItemFlintAndSteel) return@none false

            val otherIndex = if (otherIndex > stacks.lastIndex) -1 else otherIndex

            if (index == otherIndex) {
                val otherEntityItem = entityStacksMap?.get(otherStack) ?: return@none false
                distanceSqToItem > mc.thePlayer.getDistanceSqToEntity(otherEntityItem)
            } else {
                canBeSortedTo(otherIndex, otherItem, stacks.size)
                        || otherStack.totalDurability > stack.totalDurability || otherIndex > index
            }
        }
    }

    private fun isUsefulFood(
        stack: ItemStack?, stacks: List<ItemStack?>, entityStacksMap: Map<ItemStack, EntityItem>?,
        ignoreLimits: Boolean, strictlyBest: Boolean,
    ): Boolean {
        val item = stack?.item ?: return false
        if (item !is ItemFood) return false

        if (ignoreLimits || !limitStackCounts) {
            if (!strictlyBest) return true
        } else if (maxFoodStacks == 0) return false

        val stackSaturation = item.getSaturationModifier(stack) * stack.stackSize
        val index = stacks.indexOf(stack)
        val isSorted = canBeSortedTo(index, item, stacks.size)

        val stacksToIterate = stacks.toMutableList()
        var distanceSqToItem = .0

        if (!entityStacksMap.isNullOrEmpty()) {
            distanceSqToItem = mc.thePlayer.getDistanceSqToEntity(entityStacksMap[stack] ?: return false)
            stacksToIterate += entityStacksMap.keys
        }

        val betterCount = stacksToIterate.withIndex().count { (otherIndex, otherStack) ->
            if (stack == otherStack) return@count false
            val otherItem = otherStack?.item ?: return@count false
            if (otherItem !is ItemFood) return@count false

            val otherIndex = if (otherIndex > stacks.lastIndex) -1 else otherIndex
            val otherStackSaturation = otherItem.getSaturationModifier(otherStack) * otherStack.stackSize

            when (otherStackSaturation.compareTo(stackSaturation)) {
                1 -> true
                0 -> {
                    if (index == otherIndex) {
                        val otherEntityItem = entityStacksMap?.get(otherStack) ?: return@count false
                        distanceSqToItem > mc.thePlayer.getDistanceSqToEntity(otherEntityItem)
                    } else {
                        val isOtherSorted = canBeSortedTo(otherIndex, otherItem, stacks.size)
                        !isSorted && (isOtherSorted || otherIndex > index)
                    }
                }
                else -> false
            }
        }

        return if (strictlyBest) betterCount == 0 else betterCount < maxFoodStacks
    }

    private fun isUsefulBlock(
        stack: ItemStack?, stacks: List<ItemStack?>, entityStacksMap: Map<ItemStack, EntityItem>?,
        ignoreLimits: Boolean, strictlyBest: Boolean,
    ): Boolean {
        if (!isSuitableBlock(stack)) return false

        if (ignoreLimits || !limitStackCounts) {
            if (!strictlyBest) return true
        } else if (maxBlockStacks == 0) return false

        val index = stacks.indexOf(stack)
        val isSorted = canBeSortedTo(index, stack!!.item, stacks.size)

        val stacksToIterate = stacks.toMutableList()
        var distanceSqToItem = .0

        if (!entityStacksMap.isNullOrEmpty()) {
            distanceSqToItem = mc.thePlayer.getDistanceSqToEntity(entityStacksMap[stack] ?: return false)
            stacksToIterate += entityStacksMap.keys
        }

        val betterCount = stacksToIterate.withIndex().count { (otherIndex, otherStack) ->
            if (otherStack == stack || !isSuitableBlock(otherStack)) return@count false
            val otherIndex = if (otherIndex > stacks.lastIndex) -1 else otherIndex

            when (otherStack!!.stackSize.compareTo(stack.stackSize)) {
                1 -> true
                0 -> {
                    if (index == otherIndex) {
                        val otherEntityItem = entityStacksMap?.get(otherStack) ?: return@count false
                        distanceSqToItem > mc.thePlayer.getDistanceSqToEntity(otherEntityItem)
                    } else {
                        val isOtherSorted = canBeSortedTo(otherIndex, otherStack.item, stacks.size)
                        !isSorted && (isOtherSorted || otherIndex > index)
                    }
                }
                else -> false
            }
        }

        return if (strictlyBest) betterCount == 0 else betterCount < maxBlockStacks
    }

    private fun isUsefulThrowable(
        stack: ItemStack?, stacks: List<ItemStack?>,
        entityStacksMap: Map<ItemStack, EntityItem>?, ignoreLimits: Boolean, strictlyBest: Boolean,
    ): Boolean {
        val item = stack?.item ?: return false
        if (item !in THROWABLE_ITEMS) return false

        if (ignoreLimits || !limitStackCounts) {
            if (!strictlyBest) return true
        } else if (maxBlockStacks == 0) return false

        val index = stacks.indexOf(stack)
        val isSorted = canBeSortedTo(index, item, stacks.size)

        val stacksToIterate = stacks.toMutableList()
        var distanceSqToItem = .0

        if (!entityStacksMap.isNullOrEmpty()) {
            distanceSqToItem = mc.thePlayer.getDistanceSqToEntity(entityStacksMap[stack] ?: return false)
            stacksToIterate += entityStacksMap.keys
        }

        val betterCount = stacksToIterate.withIndex().count { (otherIndex, otherStack) ->
            if (otherStack == stack) return@count false
            val otherItem = otherStack?.item ?: return@count false
            if (otherItem !in THROWABLE_ITEMS) return@count false

            val otherIndex = if (otherIndex > stacks.lastIndex) -1 else otherIndex

            when (otherStack.stackSize.compareTo(stack.stackSize)) {
                1 -> true
                0 -> {
                    if (index == otherIndex) {
                        val otherEntityItem = entityStacksMap?.get(otherStack) ?: return@count false
                        distanceSqToItem > mc.thePlayer.getDistanceSqToEntity(otherEntityItem)
                    } else {
                        val isOtherSorted = canBeSortedTo(otherIndex, otherStack.item, stacks.size)
                        !isSorted && (isOtherSorted || otherIndex > index)
                    }
                }
                else -> false
            }
        }

        return if (strictlyBest) betterCount == 0 else betterCount < maxThrowableStacks
    }

    private fun isUsefulBucket(
        stack: ItemStack?, stacks: List<ItemStack?>,
        entityStacksMap: Map<ItemStack, EntityItem>?,
    ): Boolean {
        val item = stack?.item ?: return false
        if (item !is ItemBucket) return false

        val index = stacks.indexOf(stack)
        val isSorted = canBeSortedTo(index, item, stacks.size)
        if (isSorted) return true

        val stacksToIterate = stacks.toMutableList()
        var distanceSqToItem = .0

        if (!entityStacksMap.isNullOrEmpty()) {
            distanceSqToItem = mc.thePlayer.getDistanceSqToEntity(entityStacksMap[stack] ?: return false)
            stacksToIterate += entityStacksMap.keys
        }

        return stacksToIterate.withIndex().none { (otherIndex, otherStack) ->
            if (otherStack == stack) return@none false
            val otherItem = otherStack?.item ?: return@none false
            if (otherItem != item) return@none false

            val otherIndex = if (otherIndex > stacks.lastIndex) -1 else otherIndex

            if (index == otherIndex) {
                val otherEntityItem = entityStacksMap?.get(otherStack) ?: return@none false
                distanceSqToItem > mc.thePlayer.getDistanceSqToEntity(otherEntityItem)
            } else {
                canBeSortedTo(otherIndex, otherItem, stacks.size) || otherIndex > index
            }
        }
    }

    private fun hasBestParameters(
        stack: ItemStack?, stacks: List<ItemStack?>,
        entityStacksMap: Map<ItemStack, EntityItem>? = null, parameters: (ItemStack) -> Float,
    ): Boolean {
        val item = stack?.item ?: return false

        val index = stacks.indexOf(stack)
        val currentStats = parameters(stack)
        val isSorted = canBeSortedTo(index, item, stacks.size)

        val stacksToIterate = stacks.toMutableList()
        var distanceSqToItem = .0

        if (!entityStacksMap.isNullOrEmpty()) {
            distanceSqToItem = mc.thePlayer.getDistanceSqToEntity(entityStacksMap[stack] ?: return false)
            stacksToIterate += entityStacksMap.keys
        }

        stacksToIterate.forEachIndexed { otherIndex, otherStack ->
            val otherItem = otherStack?.item ?: return@forEachIndexed
            if (stack == otherStack || item.javaClass != otherItem.javaClass) return@forEachIndexed

            val otherIndex = if (otherIndex > stacks.lastIndex) -1 else otherIndex
            val otherStats = parameters(otherStack)
            val isOtherSorted = canBeSortedTo(otherIndex, otherItem, stacks.size)

            when (otherStats.compareTo(currentStats)) {
                1 -> return false
                0 -> when (otherStack.enchantmentSum.compareTo(stack.enchantmentSum)) {
                    1 -> return false
                    0 -> when (otherStack.totalDurability.compareTo(stack.totalDurability)) {
                        1 -> return false
                        0 -> {
                            if (index == otherIndex) {
                                val otherEntityItem = entityStacksMap?.get(otherStack) ?: return@forEachIndexed
                                if (distanceSqToItem > mc.thePlayer.getDistanceSqToEntity(otherEntityItem)) return false
                            } else if (!isSorted && (isOtherSorted || otherIndex > index)) return false
                        }
                    }
                }
            }
        }

        return true
    }

    @Suppress("DEPRECATION")
    private fun isSuitableBlock(stack: ItemStack?): Boolean {
        val item = stack?.item ?: return false
        if (item is ItemBlock) {
            val block = item.block
            return isFullBlock(block) && !block.hasTileEntity()
                    && block !is BlockWorkbench && block !is BlockContainer && block !is BlockFalling
        }
        return false
    }

    private fun getCombinedDurabilityIfBeneficial(stack1: ItemStack, stack2: ItemStack): Int? {
        val combinedDurability =
            (stack1.durability + stack2.durability + stack1.maxDamage * 5 / 100)
                .coerceAtMost(stack1.maxDamage)

        return if (stack1.totalDurability >= combinedDurability || stack2.totalDurability >= combinedDurability) null
        else combinedDurability
    }

    private fun shouldBeRepaired(stack: ItemStack?) =
        !stack.isEmpty() && stack.item.isRepairable && (
                !stack.isItemEnchanted || (stack.enchantmentCount == 1 && Enchantment.unbreaking in stack.enchantments)
                )

    fun canBeRepairedWithOther(stack: ItemStack?, stacks: List<ItemStack?>): Boolean {
        if (!handleEvents() || !repairEquipment) return false
        val item = stack?.item ?: return false
        if (!shouldBeRepaired(stack)) return false

        return stacks.any { otherStack ->
            if (otherStack.isEmpty() || otherStack == stack) return@any false
            if (otherStack.item != item) return@any false
            getCombinedDurabilityIfBeneficial(stack, otherStack) != null
        }
    }

    private fun sortChoice(name: String, value: String) = choices(name, SORTING_KEYS, value) {
        sort
    }.onChange { old, new ->
        if (new in SINGLE_KEYS) {
            SORTING_VALUES.find { it.get() == new }?.let { another ->
                another.set(old)
                another.openList = true
                chat("§8[§9§lInventoryCleaner§8] §3Value §a${another.name}§3 was changed to §a$old§3 to prevent conflicts.")
            }
        }
        new
    }.subjective() as ListValue
}

private val ITEMS_WHITELIST = arrayOf(
    Items.arrow, Items.diamond, Items.iron_ingot, Items.gold_ingot, Items.stick
)

private val THROWABLE_ITEMS = arrayOf(Items.egg, Items.snowball)

val NEGATIVE_EFFECT_IDS = intArrayOf(
    Potion.moveSlowdown.id, Potion.digSlowdown.id, Potion.harm.id, Potion.confusion.id, Potion.blindness.id,
    Potion.hunger.id, Potion.weakness.id, Potion.poison.id, Potion.wither.id,
)

private val SORTING_TARGETS: Map<String, ((Item?) -> Boolean)?> = mapOf(
    "Sword" to { it is ItemSword },
    "Bow" to { it is ItemBow },
    "Pickaxe" to { it is ItemPickaxe },
    "Axe" to { it is ItemAxe },
    "Shovel" to { it is ItemSpade },
    "Food" to { it is ItemFood },
    "Block" to { it is ItemBlock },
    "Water" to { it == Items.water_bucket || it == Items.bucket },
    "Fire" to { it is ItemFlintAndSteel || it == Items.lava_bucket || it == Items.bucket },
    "Gapple" to { it is ItemAppleGold },
    "Pearl" to { it is ItemEnderPearl },
    "Potion" to { it is ItemPotion },
    "Throwable" to { it is ItemEgg || it is ItemSnowball },
    "FishingRod" to { it is ItemFishingRod },
    "TNT" to { it == Item.getItemFromBlock(Blocks.tnt) },
    "Shears" to { it is ItemShears },
    "Ignore" to null
)

private val SORTING_KEYS = SORTING_TARGETS.keys.toTypedArray()
private val SINGLE_KEYS = SORTING_KEYS.copyOfRange(0, 5)