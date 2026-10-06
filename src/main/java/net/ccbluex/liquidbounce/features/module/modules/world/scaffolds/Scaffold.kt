/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.world.scaffolds

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.event.async.loopSequence
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.attack.CPSCounter
import net.ccbluex.liquidbounce.utils.block.*
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.blocksAmount
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.hotBarSlot
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.speed
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.strafe
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.rotation.PlaceRotation
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettingsWithRotationModes
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.canUpdateRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.getFixedAngleDelta
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.getVectorForRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.rotationDifference
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.simulation.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.timing.*
import net.minecraft.block.BlockBush
import net.minecraft.client.settings.GameSettings
import net.minecraft.init.Blocks.air
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.util.*
import net.minecraft.world.WorldSettings
import net.minecraftforge.event.ForgeEventFactory
import org.lwjgl.input.Keyboard
import java.awt.Color
import kotlin.math.*

object Scaffold : Module("Scaffold", Category.WORLD, Keyboard.KEY_G) {

    /**
     * TOWER MODES & SETTINGS
     */
    private val towerMode by Tower.towerModeValues

    init {
        addValues(Tower.values)
    }

    /**
     * SCAFFOLD MODES & SETTINGS
     */
    val scaffoldMode by choices(
        "ScaffoldMode", arrayOf(
            "Normal", "Rewinside", "Expand", "Telly", "GodBridge",
            "EdgeSneak", "StraightEdgeSneak", "SDShiftBridge", "Legit"
        ), "Normal"
    )

    // Legit —— 反向瞄准(yaw-180) + 反向移动 + 只允许正前疾跑 + 边缘自动蹲 + 自动右键
    private val legitPitch by float("LegitPitch", 85f, -90f..90f) { scaffoldMode == "Legit" }
    private val legitReverseMovement by boolean("LegitReverseMovement", true) { scaffoldMode == "Legit" }
    private val auto45Strafe by boolean("Auto45Strafe", true) { scaffoldMode == "Legit" }
    private val legitYawOffset by int("LegitYawOffset", 180, 90..270) { scaffoldMode == "Legit" }
    private val legitAutoPlace by boolean("LegitAutoPlace", true) { scaffoldMode == "Legit" }

    // Expand
    private val omniDirectionalExpand by boolean("OmniDirectionalExpand", false) { scaffoldMode == "Expand" }
    private val expandLength by int("ExpandLength", 1, 1..6) { scaffoldMode == "Expand" }

    // Placeable delay
    private val placeDelayValue = boolean("PlaceDelay", true) { scaffoldMode != "GodBridge" }
    private val delay by intRange("Delay", 0..0, 0..1000) { placeDelayValue.isActive() }

    // Extra clicks
    private val extraClicks by boolean("DoExtraClicks", false)
    private val simulateDoubleClicking by boolean("SimulateDoubleClicking", false) { extraClicks }
    private val extraClickCPS by intRange("ExtraClickCPS", 3..7, 0..50) { extraClicks }
    private val placementAttempt by choices(
        "PlacementAttempt", arrayOf("Fail", "Independent"), "Fail"
    ) { extraClicks }

    // Autoblock
    private val autoBlock by choices("AutoBlock", arrayOf("Off", "Pick", "Spoof", "Switch"), "Spoof")
    private val sortByHighestAmount by boolean("SortByHighestAmount", false) { autoBlock != "Off" }
    private val earlySwitch by boolean("EarlySwitch", false) { autoBlock != "Off" && !sortByHighestAmount }
    private val amountBeforeSwitch by int(
        "SlotAmountBeforeSwitch", 3, 1..10
    ) { earlySwitch && !sortByHighestAmount }

    // Settings
    private val autoF5 by boolean("AutoF5", false).subjective()
    private val silentSwing by boolean("SilentSwing", false).subjective()
    private val matrixAutoJump by boolean("MatrixAutoJump", false)

    // Basic stuff
    val sprint by boolean("Sprint", false)
    private val swing by boolean("Swing", true).subjective()
    private val down by boolean("Down", true) { !sameY && scaffoldMode !in arrayOf("GodBridge", "Telly") }

    private val ticksUntilRotation by intRange("TicksUntilRotation", 3..3, 1..8) {
        scaffoldMode == "Telly"
    }

    // GodBridge mode sub-values
    private val waitForRots by boolean("WaitForRotations", false) { isGodBridgeEnabled }
    private val useOptimizedPitch by boolean("UseOptimizedPitch", false) { isGodBridgeEnabled }
    private val customGodPitch by float(
        "GodBridgePitch", 73.5f, 0f..90f
    ) { isGodBridgeEnabled && !useOptimizedPitch }

    private val autoJump by boolean("Autojump", false)
    val jumpAutomatically by boolean("JumpAutomatically", true) { scaffoldMode == "GodBridge" }
    private val blocksToJumpRange by intRange("BlocksToJumpRange", 4..4, 1..8) { scaffoldMode == "GodBridge" && !jumpAutomatically }

    // Telly mode sub-values
    private val startHorizontally by boolean("StartHorizontally", true) { scaffoldMode == "Telly" }
    private val horizontalPlacementsRange by intRange("HorizontalPlacementsRange", 1..1, 1..10) { scaffoldMode == "Telly" }
    private val verticalPlacementsRange by intRange("VerticalPlacementsRange", 1..1, 1..10) { scaffoldMode == "Telly" }

    private val jumpTicksRange by intRange("JumpTicksRange", 0..0, 0..10) { scaffoldMode == "Telly" }

    private val allowClutching by boolean("AllowClutching", true) { scaffoldMode !in arrayOf("Telly", "Expand") }
    private val horizontalClutchBlocks by int("HorizontalClutchBlocks", 3, 1..5) {
        allowClutching && scaffoldMode !in arrayOf("Telly", "Expand")
    }
    private val verticalClutchBlocks by int("VerticalClutchBlocks", 2, 1..3) {
        allowClutching && scaffoldMode !in arrayOf("Telly", "Expand")
    }
    private val blockSafe by boolean("BlockSafe", false) { !isGodBridgeEnabled }

    // Eagle
    private val eagleValue =
        choices("Eagle", arrayOf("Normal", "Silent", "Off"), "Normal") { scaffoldMode != "GodBridge" }
    val eagle by eagleValue
    private val eagleMode by choices("EagleMode", arrayOf("Both", "OnGround", "InAir"), "Both")
    { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val adjustedSneakSpeed by boolean("AdjustedSneakSpeed", true)
    { eagle == "Silent" && scaffoldMode != "GodBridge" }
    private val eagleSpeed by float("EagleSpeed", 0.3f, 0.3f..1.0f) { eagle != "Off" && scaffoldMode != "GodBridge" }
    val eagleSprint by boolean("EagleSprint", false) { eagle == "Normal" && scaffoldMode != "GodBridge" }
    private val blocksToEagle by intRange("BlocksToEagle", 0..0, 0..10) { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val edgeDistance by float("EagleEdgeDistance", 0f, 0f..0.5f)
    { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val useMaxSneakTime by boolean("UseMaxSneakTime", true) { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val maxSneakTicks by intRange("MaxSneakTicks", 3..3, 0..10) { useMaxSneakTime }
    private val blockSneakingAgainUntilOnGround by boolean("BlockSneakingAgainUntilOnGround", true)
    { useMaxSneakTime && eagleMode != "OnGround" }

    // Rotation Options
    private val modeList =
        choices("Rotations", arrayOf("Off", "Normal", "Stabilized", "ReverseYaw", "GodBridge", "Hypixel"), "Normal")
    private val hypixelMinPitch by float("HypixelMinPitch", 55f, 50f..90f) { modeList.get() == "Hypixel" }
    private val hypixelMaxPitch by float("HypixelMaxPitch", 75f, 50f..90f) { modeList.get() == "Hypixel" }
    private val options = RotationSettingsWithRotationModes(this, modeList).apply {
        strictValue.excludeWithState()
        resetTicksValue.setSupport { it && scaffoldMode != "Telly" }
    }

    // Search options
    val searchMode by choices("SearchMode", arrayOf("Area", "Center"), "Area") { scaffoldMode != "GodBridge" }
    private val minDist by float("MinDist", 0f, 0f..0.2f) { scaffoldMode !in arrayOf("GodBridge", "Telly") }

    // Zitter
    private val zitterMode by choices("Zitter", arrayOf("Off", "Teleport", "Smooth"), "Off")
    private val zitterSpeed by float("ZitterSpeed", 0.13f, 0.1f..0.3f) { zitterMode == "Teleport" }
    private val zitterStrength by float("ZitterStrength", 0.05f, 0f..0.2f) { zitterMode == "Teleport" }
    private val zitterTicks by intRange("ZitterTicks", 2..3, 0..6) { zitterMode == "Smooth" }

    private val useSneakMidAir by boolean("UseSneakMidAir", false) { zitterMode == "Smooth" }

    // Game
    val timer by float("Timer", 1f, 0.1f..10f)
    private val speedModifier by float("SpeedModifier", 1f, 0f..2f)
    private val speedLimiter by boolean("SpeedLimiter", false) { !slow }
    private val speedLimit by float("SpeedLimit", 0.11f, 0.01f..0.12f) { !slow && speedLimiter }
    private val slow by boolean("Slow", false)
    private val slowGround by boolean("SlowOnlyGround", false) { slow }
    private val slowSpeed by float("SlowSpeed", 0.6f, 0.2f..0.8f) { slow }

    // Jump Strafe
    private val jumpStrafe by boolean("JumpStrafe", false)
    private val jumpStraightStrafe by floatRange("JumpStraightStrafe", 0.4f..0.45f, 0.1f..1f) { jumpStrafe }
    private val jumpDiagonalStrafe by floatRange("JumpDiagonalStrafe", 0.4f..0.45f, 0.1f..1f) { jumpStrafe }

    // Safety
    private val sameY by boolean("SameY", false) { scaffoldMode != "GodBridge" }
    private val jumpOnUserInput by boolean("JumpOnUserInput", true) { sameY && scaffoldMode != "GodBridge" }

    private val safeWalkValue = boolean("SafeWalk", true) { scaffoldMode != "GodBridge" }
    private val airSafe by boolean("AirSafe", false) { safeWalkValue.isActive() }

    // Visuals
    private val mark by boolean("Mark", false).subjective()
    private val markColor by color("MarkColor", Color(68, 117, 255, 100)) { mark }
    private val trackCPS by boolean("TrackCPS", false).subjective()

    // StraightEdgeSneak release delay (ticks)
    private val straightSneakReleaseDelay by int("StraightSneakReleaseDelay", 3, 0..10) {
        scaffoldMode == "StraightEdgeSneak"
    }

    // Target placement
    var placeRotation: PlaceRotation? = null

    // Launch position
    private var launchY = -999

    val shouldJumpOnInput
        get() = !jumpOnUserInput || !mc.gameSettings.keyBindJump.isKeyDown && mc.thePlayer.posY >= launchY && !mc.thePlayer.onGround

    private val shouldKeepLaunchPosition
        get() = sameY && shouldJumpOnInput && scaffoldMode != "GodBridge"

    // Zitter
    private var zitterDirection = false

    // Delay
    private val delayTimer = object : DelayTimer(delay.first, delay.last, MSTimer()) {
        override fun hasTimePassed() = !placeDelayValue.isActive() || super.hasTimePassed()
    }

    private val zitterTickTimer = TickDelayTimer(zitterTicks.first, zitterTicks.last)

    // Eagle
    private var placedBlocksWithoutEagle = 0

    var eagleSneaking = false

    private var requestedStopSneak = false

    private val isEagleEnabled
        get() = eagle != "Off" && !shouldGoDown && scaffoldMode != "GodBridge" && scaffoldMode != "SDShiftBridge" && scaffoldMode != "StraightEdgeSneak"

    // Downwards
    val shouldGoDown
        get() = down && !sameY && GameSettings.isKeyDown(mc.gameSettings.keyBindSneak) && scaffoldMode !in arrayOf(
            "GodBridge", "Telly"
        ) && blocksAmount() > 1

    // Current rotation
    private val currRotation
        get() = RotationUtils.currentRotation ?: mc.thePlayer.rotation

    // Extra clicks
    private var extraClick = ExtraClickInfo(TimeUtils.randomClickDelay(extraClickCPS.first, extraClickCPS.last), 0L, 0)

    // GodBridge
    private var blocksPlacedUntilJump = 0

    private val isManualJumpOptionActive
        get() = scaffoldMode == "GodBridge" && !jumpAutomatically

    private var blocksToJump = blocksToJumpRange.random()

    private val isGodBridgeEnabled
        get() = scaffoldMode == "GodBridge" || scaffoldMode == "Normal" && options.rotationMode == "GodBridge"

    private var godBridgeTargetRotation: Rotation? = null

    private val isLookingDiagonally: Boolean
        get() {
            val player = mc.thePlayer ?: return false
            val directionDegree = MovementUtils.direction.toDegreesF()
            val yaw = round(abs(MathHelper.wrapAngleTo180_float(directionDegree)) / 45f) * 45f
            val isYawDiagonal = yaw % 90 != 0f
            val isMovingDiagonal = player.movementInput.moveForward != 0f && player.movementInput.moveStrafe == 0f
            val isStrafing = mc.gameSettings.keyBindRight.isKeyDown || mc.gameSettings.keyBindLeft.isKeyDown
            return isYawDiagonal && (isMovingDiagonal || isStrafing)
        }

    // Telly
    private var ticksUntilJump = 0
    private var blocksUntilAxisChange = 0
    private var jumpTicks = jumpTicksRange.random()
    private var horizontalPlacements = horizontalPlacementsRange.random()
    private var verticalPlacements = verticalPlacementsRange.random()
    private val shouldPlaceHorizontally
        get() = scaffoldMode == "Telly" && mc.thePlayer.isMoving && (startHorizontally && blocksUntilAxisChange <= horizontalPlacements || !startHorizontally && blocksUntilAxisChange > verticalPlacements)

    // Shared state for StraightEdgeSneak & SDShiftBridge
    private var shiftBridgeSneaking = false

    private fun isOnEdgeForShiftBridge(): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false
        val pos = BlockPos(player).down()
        for (facing in EnumFacing.HORIZONTALS) {
            val neighbor = pos.offset(facing)
            if (world.isAirBlock(neighbor)) {
                val diff = when (facing.axis) {
                    EnumFacing.Axis.X -> abs(player.posX - (pos.x + 0.5 + facing.directionVec.x * 0.5))
                    EnumFacing.Axis.Z -> abs(player.posZ - (pos.z + 0.5 + facing.directionVec.z * 0.5))
                    else -> 0.0
                }
                if (diff > 0.3) return true
            }
        }
        return false
    }

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        launchY = player.posY.roundToInt()
        blocksUntilAxisChange = 0
        shiftBridgeSneaking = false
    }

    // Events
    val onUpdate = loopSequence {
        val player = mc.thePlayer ?: return@loopSequence

        if (mc.playerController.currentGameType == WorldSettings.GameType.SPECTATOR) return@loopSequence

        mc.timer.timerSpeed = timer

        if (player.onGround) ticksUntilJump++

        if (shouldGoDown) {
            mc.gameSettings.keyBindSneak.pressed = false
        }

        if (slow) {
            if (!slowGround || slowGround && player.onGround) {
                player.motionX *= slowSpeed
                player.motionZ *= slowSpeed
            }
        }

        // Eagle
        if (isEagleEnabled) {
            var dif = 0.5
            val blockPos = BlockPos(player).down()

            for (side in EnumFacing.entries) {
                if (side.axis == EnumFacing.Axis.Y) continue
                val neighbor = blockPos.offset(side)
                if (neighbor.isReplaceable) {
                    val calcDif = (if (side.axis == EnumFacing.Axis.Z) {
                        abs(neighbor.z + 0.5 - player.posZ)
                    } else {
                        abs(neighbor.x + 0.5 - player.posX)
                    }) - 0.5
                    if (calcDif < dif) dif = calcDif
                }
            }

            val blockSneaking = WaitTickUtils.hasScheduled("block")
            val alreadySneaking = WaitTickUtils.hasScheduled("sneak")
            val options = mc.gameSettings

            run {
                if (placedBlocksWithoutEagle < blocksToEagle.random() && !alreadySneaking && !blockSneaking && !eagleSneaking && !requestedStopSneak) {
                    return@run
                }

                val eagleCondition = when (eagleMode) {
                    "OnGround" -> player.onGround
                    "InAir" -> !player.onGround
                    else -> true
                }

                val pressedOnKeyboard = Keyboard.isKeyDown(options.keyBindSneak.keyCode)

                var shouldEagle =
                    eagleCondition && (blockPos.isReplaceable || dif < edgeDistance) || pressedOnKeyboard

                val shouldSchedule = !requestedStopSneak

                if (requestedStopSneak) {
                    requestedStopSneak = false
                    if (!player.onGround) shouldEagle = pressedOnKeyboard
                } else if (blockSneaking || alreadySneaking) {
                    return@run
                }

                if (eagle == "Silent") {
                    if (eagleSneaking != shouldEagle) {
                        sendPacket(
                            C0BPacketEntityAction(
                                player, if (shouldEagle) {
                                    C0BPacketEntityAction.Action.START_SNEAKING
                                } else {
                                    C0BPacketEntityAction.Action.STOP_SNEAKING
                                }
                            )
                        )
                        if (adjustedSneakSpeed && shouldEagle) {
                            player.motionX *= eagleSpeed
                            player.motionZ *= eagleSpeed
                        }
                    }
                    eagleSneaking = shouldEagle
                } else {
                    options.keyBindSneak.pressed = shouldEagle
                    eagleSneaking = shouldEagle
                }

                if (eagleSneaking && shouldSchedule) {
                    if (useMaxSneakTime) {
                        WaitTickUtils.conditionalSchedule("sneak") { elapsed ->
                            (elapsed >= maxSneakTicks.random() + 1).also { requestedStopSneak = it }
                        }
                    }
                    if (blockSneakingAgainUntilOnGround && !player.onGround) {
                        WaitTickUtils.conditionalSchedule("block") {
                            mc.thePlayer?.onGround.also { if (it != false) requestedStopSneak = true } ?: true
                        }
                    }
                }
                placedBlocksWithoutEagle = 0
            }
        }

        // Edge sneak for StraightEdgeSneak & SDShiftBridge
        if (scaffoldMode in arrayOf("StraightEdgeSneak", "SDShiftBridge")) {
            if (!shiftBridgeSneaking && player.onGround && isOnEdgeForShiftBridge()) {
                mc.gameSettings.keyBindSneak.pressed = true
                shiftBridgeSneaking = true
            }
        }

        if (player.onGround) {
            if (scaffoldMode == "Rewinside") {
                MovementUtils.strafe(0.2F)
                player.motionY = 0.0
            }
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler

        if (scaffoldMode == "Telly" && player.onGround && player.isMoving && currRotation == player.rotation && ticksUntilJump >= jumpTicks) {
            player.tryJump()
            ticksUntilJump = 0
            jumpTicks = jumpTicksRange.random()
            return@handler
        }

        if (matrixAutoJump && player.onGround && player.isMoving) {
            if (player.isInWater || player.isInLava || player.isOnLadder || player.isInWeb) return@handler
            if (Speed.matrixLowHop) {
                try { player.jumpMovementFactor = 0.026f } catch (_: Throwable) {}
            }
            player.tryJump()
            val lowHopAdjust = if (Speed.matrixLowHop) 0.00348 else 0.0
            try { player.motionY = 0.42 - lowHopAdjust } catch (_: Throwable) {}
            try {
                val groundSpeed = if (!handleEvents()) speed + Speed.extraGroundBoost else speed
                strafe(groundSpeed)
            } catch (_: Throwable) {}
            try {
                player.speedInAir = if (player.fallDistance <= 0.4 && player.moveStrafing == 0f) 0.02035f else 0.02f
            } catch (_: Throwable) {}
            return@handler
        }

        if (autoJump && player.onGround && player.isMoving) {
            player.tryJump()
        }
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        val player = mc.thePlayer ?: return@handler

        if (player.ticksExisted == 1) launchY = player.posY.roundToInt()

        val rotation = RotationUtils.currentRotation
        update()

        val ticks = if (options.keepRotation) {
            if (scaffoldMode == "Telly") 1 else options.resetTicks
        } else {
            if (isGodBridgeEnabled) options.resetTicks else RotationUtils.resetTicks
        }

        if (scaffoldMode == "Legit") {
            if (options.rotationsActive) {
                var yawOffset = legitYawOffset
                var pitch = legitPitch
                if (auto45Strafe) {
                    // 玩家朝向：正轴(0/±90/180) → offset 135 / pitch 80；斜向(±45/±135) → offset 180 / pitch 77
                    val normalized = kotlin.math.abs(MathHelper.wrapAngleTo180_float(player.rotationYaw)) % 90f
                    val straight = normalized < 22.5f || normalized > 67.5f
                    yawOffset = if (straight) 135 else 180
                    pitch = if (straight) 80f else 77f
                }
                setRotation(Rotation(player.rotationYaw - yawOffset, pitch), ticks)
            }
            return@handler
        }

        if (!Tower.isTowering && isGodBridgeEnabled && options.rotationsActive) {
            generateGodBridgeRotations(ticks)
            return@handler
        }
        if (modeList.get() == "Hypixel" && options.rotationsActive) {
            val targetBlock = placeRotation?.placeInfo?.blockPos ?: return@handler
            val hypixelRotation = calculateHypixelRotation(targetBlock)
            setTargetRotation(hypixelRotation, options, ticks)
            return@handler
        }

        if (options.rotationsActive && rotation != null) {
            val placeRotation = this.placeRotation?.rotation ?: rotation
            if (RotationUtils.resetTicks != 0 || options.keepRotation) {
                setRotation(placeRotation, ticks)
            }
        }
    }

    private fun calculateHypixelRotation(targetBlock: BlockPos): Rotation {
        val player = mc.thePlayer ?: return currRotation
        val eyes = player.eyes
        val blockVec = Vec3(targetBlock.x + 0.5, targetBlock.y + 0.5, targetBlock.z + 0.5)
        val rawYaw = MovementUtils.direction.toDegreesF()
        var adjustedYaw = rawYaw
        val isMovingStraight = player.movementInput.moveForward != 0f && player.movementInput.moveStrafe == 0f
        val baseRotation = toRotation(blockVec, false)

        adjustedYaw = when {
            isMovingStraight -> {
                val yawOption1 = rawYaw + 118f
                val yawOption2 = rawYaw - 118f
                val diff1 = MathHelper.wrapAngleTo180_float(yawOption1 - baseRotation.yaw)
                val diff2 = MathHelper.wrapAngleTo180_float(yawOption2 - baseRotation.yaw)
                if (abs(diff1) < abs(diff2)) yawOption1 else yawOption2
            }
            else -> rawYaw + 132f
        }
        val placeFace = placeRotation?.placeInfo?.enumFacing ?: EnumFacing.UP
        val faceVec = placeFace.directionVec
        val (minX, maxX) = when (faceVec.x) { 1 -> 1.0f to 1.0f; -1 -> 0.0f to 0.0f; else -> 0.1f to 0.9f }
        val (minY, maxY) = when (faceVec.y) { 1 -> 1.0f to 1.0f; -1 -> 0.0f to 0.0f; else -> 0.1f to 0.9f }
        val (minZ, maxZ) = when (faceVec.z) { 1 -> 1.0f to 1.0f; -1 -> 0.0f to 0.0f; else -> 0.1f to 0.9f }

        val step = 0.1f
        var bestPitch = currRotation.pitch
        var minDiff = Float.MAX_VALUE

        val stepsX = ((maxX - minX) * 10).toInt()
        val stepsY = ((maxY - minY) * 10).toInt()
        val stepsZ = ((maxZ - minZ) * 10).toInt()

        for (i in 0..stepsX) {
            val x = minX + i * step
            for (j in 0..stepsY) {
                val y = minY + j * step
                for (k in 0..stepsZ) {
                    val z = minZ + k * step
                    val candidate = Vec3(targetBlock.x + x.toDouble(), targetBlock.y + y.toDouble(), targetBlock.z + z.toDouble())
                    val candidateRotation = toRotation(candidate, false)
                    val diff = rotationDifference(candidateRotation, currRotation)
                    if (diff < minDiff) { minDiff = diff; bestPitch = candidateRotation.pitch }
                }
            }
        }
        return Rotation(adjustedYaw, bestPitch.coerceIn(hypixelMinPitch, hypixelMaxPitch)).fixedSensitivity()
    }

    val onTick = handler<GameTickEvent> {
        val target = placeRotation?.placeInfo
        val raycastProperly = !(scaffoldMode == "Expand" && expandLength > 1 || shouldGoDown) && options.rotationsActive
        val raycast = performBlockRaytrace(currRotation, mc.playerController.blockReachDistance)

        var alreadyPlaced = false
        if (extraClicks) {
            val doubleClick = if (simulateDoubleClicking) RandomUtils.nextInt(-1, 1) else 0
            val clicks = extraClick.clicks + doubleClick
            repeat(clicks) {
                extraClick.clicks--
                doPlaceAttempt(raycast, it + 1 == clicks) { alreadyPlaced = true }
            }
        }

        if (target == null) {
            if (placeDelayValue.isActive()) delayTimer.reset()
            return@handler
        }

        if (alreadyPlaced || SilentHotbar.modifiedThisTick) return@handler

        raycast.let {
            if (!options.rotationsActive || it != null && it.blockPos == target.blockPos && (!raycastProperly || it.sideHit == target.enumFacing)) {
                val result = if (raycastProperly && it != null) PlaceInfo(it.blockPos, it.sideHit, it.hitVec) else target
                place(result)
            }
        }
    }

    val onSneakSlowDown = handler<SneakSlowDownEvent> { event ->
        if (!isEagleEnabled || eagle != "Normal") return@handler
        event.forward *= eagleSpeed / 0.3f
        event.strafe *= eagleSpeed / 0.3f
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        // SDShiftBridge: auto S+D movement
        if (scaffoldMode == "SDShiftBridge") {
            val yaw = Math.toRadians(player.rotationYaw.toDouble())
            val forwardX = -sin(yaw)
            val forwardZ = cos(yaw)
            val strafeX = cos(yaw)
            val strafeZ = sin(yaw)

            // S(back) + D(right)
            val moveX = -forwardX + strafeX
            val moveZ = -forwardZ + strafeZ
            val len = sqrt(moveX * moveX + moveZ * moveZ)
            if (len > 0) {
                event.originalInput.moveForward = ((-forwardX * moveX + -forwardZ * moveZ) / len).toFloat().coerceIn(-1f, 1f)
                event.originalInput.moveStrafe = ((strafeX * moveX + strafeZ * moveZ) / len).toFloat().coerceIn(-1f, 1f)
            } else {
                event.originalInput.moveForward = -1f
                event.originalInput.moveStrafe = 1f
            }
            event.originalInput.sneak = shiftBridgeSneaking || mc.gameSettings.keyBindSneak.isKeyDown
            return@handler
        }

        // StraightEdgeSneak mode: sneak directly from state
        if (scaffoldMode == "StraightEdgeSneak") {
            event.originalInput.sneak = shiftBridgeSneaking
            return@handler
        }

        if (!isGodBridgeEnabled || !player.onGround) return@handler
        if (waitForRots) {
            godBridgeTargetRotation?.run {
                event.originalInput.sneak = event.originalInput.sneak || rotationDifference(this, currRotation) > getFixedAngleDelta()
            }
        }
        val simPlayer = SimulatedPlayer.fromClientPlayer(RotationUtils.modifiedInput)
        simPlayer.rotationYaw = currRotation.yaw
        simPlayer.tick()
        if (!simPlayer.onGround && !isManualJumpOptionActive || blocksPlacedUntilJump > blocksToJump) {
            event.originalInput.jump = true
            blocksPlacedUntilJump = 0
            blocksToJump = blocksToJumpRange.random()
        }
    }

    fun update() {
        val player = mc.thePlayer ?: return
        val holdingItem = player.heldItem?.item is ItemBlock
        if (!holdingItem && (autoBlock == "Off" || InventoryUtils.findBlockInHotbar() == null)) return
        findBlock(scaffoldMode == "Expand" && expandLength > 1, searchMode == "Area")
    }

    private fun setRotation(rotation: Rotation, ticks: Int) {
        val player = mc.thePlayer ?: return
        if (scaffoldMode == "Telly" && player.isMoving) {
            if (player.airTicks < ticksUntilRotation.random() && ticksUntilJump >= jumpTicks) return
        }
        setTargetRotation(rotation, options, ticks)
    }

    private fun findBlock(expand: Boolean, area: Boolean) {
        val player = mc.thePlayer ?: return
        if (!shouldKeepLaunchPosition) launchY = player.posY.roundToInt()

        val blockPosition = if (shouldGoDown) {
            if (player.posY == player.posY.roundToInt() + 0.5) BlockPos(player.posX, player.posY - 0.6, player.posZ)
            else BlockPos(player.posX, player.posY - 0.6, player.posZ).down()
        } else if (shouldKeepLaunchPosition && launchY <= player.posY) {
            BlockPos(player.posX, launchY - 1.0, player.posZ)
        } else if (player.posY == player.posY.roundToInt() + 0.5) {
            BlockPos(player)
        } else {
            BlockPos(player).down()
        }

        if (!expand && (!blockPosition.isReplaceable || search(blockPosition, !shouldGoDown, area, shouldPlaceHorizontally))) return

        if (expand) {
            val yaw = player.rotationYaw.toRadiansD()
            val x = if (omniDirectionalExpand) -sin(yaw).roundToInt() else player.horizontalFacing.directionVec.x
            val z = if (omniDirectionalExpand) cos(yaw).roundToInt() else player.horizontalFacing.directionVec.z
            repeat(expandLength) { if (search(blockPosition.add(x * it, 0, z * it), false, area)) return }
            return
        }

        val (horizontal, vertical) = if (scaffoldMode == "Telly") 5 to 3
        else if (allowClutching || scaffoldMode in arrayOf("SDShiftBridge", "StraightEdgeSneak")) horizontalClutchBlocks to verticalClutchBlocks
        else 1 to 1

        BlockPos.getAllInBox(
            blockPosition.add(-horizontal, 0, -horizontal), blockPosition.add(horizontal, -vertical, horizontal)
        ).sortedBy { BlockUtils.getCenterDistance(it) }.forEach {
            if (it.canBeClicked() || search(it, !shouldGoDown, area, shouldPlaceHorizontally)) return
        }
    }

    private fun place(placeInfo: PlaceInfo) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (!delayTimer.hasTimePassed() || shouldKeepLaunchPosition && launchY - 1 != placeInfo.vec3.yCoord.toInt() && scaffoldMode != "Expand") return

        val currentSlot = SilentHotbar.currentSlot
        var stack = player.hotBarSlot(currentSlot).stack

        if (stack == null || stack.item !is ItemBlock || (stack.item as ItemBlock).block is BlockBush || stack.stackSize <= 0 || sortByHighestAmount || earlySwitch) {
            val blockSlot = if (sortByHighestAmount) InventoryUtils.findLargestBlockStackInHotbar() ?: return
            else if (earlySwitch) InventoryUtils.findBlockStackInHotbarGreaterThan(amountBeforeSwitch) ?: InventoryUtils.findBlockInHotbar() ?: return
            else InventoryUtils.findBlockInHotbar() ?: return

            stack = player.hotBarSlot(blockSlot).stack
            if ((stack.item as? ItemBlock)?.canPlaceBlockOnSide(world, placeInfo.blockPos, placeInfo.enumFacing, player, stack) == false) return
            if (autoBlock != "Off") SilentHotbar.selectSlotSilently(this, blockSlot, render = autoBlock == "Pick", resetManually = true)
        }

        // Release sneak based on mode
        when {
            scaffoldMode == "StraightEdgeSneak" && shiftBridgeSneaking -> {
                // Fixed: WaitTickUtils.schedule(delay, name, action)
                WaitTickUtils.schedule(straightSneakReleaseDelay, "straightRelease") {
                    if (state) {
                        mc.gameSettings.keyBindSneak.pressed = false
                        shiftBridgeSneaking = false
                    }
                }
            }
            scaffoldMode == "SDShiftBridge" && shiftBridgeSneaking -> {
                mc.gameSettings.keyBindSneak.pressed = false
                shiftBridgeSneaking = false
            }
        }

        tryToPlaceBlock(stack, placeInfo.blockPos, placeInfo.enumFacing, placeInfo.vec3)

        if (autoBlock == "Switch") SilentHotbar.resetSlot(this, true)
        findBlockToSwitchNextTick(stack)
        if (trackCPS) CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
    }

    private fun doPlaceAttempt(raytrace: MovingObjectPosition?, lastClick: Boolean, onSuccess: () -> Unit = { }) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        val stack = player.hotBarSlot(SilentHotbar.currentSlot).stack ?: return
        if (stack.item !is ItemBlock || InventoryUtils.BLOCK_BLACKLIST.contains((stack.item as ItemBlock).block)) return
        raytrace ?: return

        val block = stack.item as ItemBlock
        val canPlaceOnUpperFace = block.canPlaceBlockOnSide(world, raytrace.blockPos, EnumFacing.UP, player, stack)

        val shouldPlace = if (placementAttempt == "Fail") !block.canPlaceBlockOnSide(world, raytrace.blockPos, raytrace.sideHit, player, stack)
        else {
            if (shouldKeepLaunchPosition) raytrace.blockPos.y == launchY - 1 && !canPlaceOnUpperFace
            else if (shouldPlaceHorizontally) !canPlaceOnUpperFace
            else raytrace.blockPos.y <= player.posY.toInt() - 1 && !(raytrace.blockPos.y == player.posY.toInt() - 1 && canPlaceOnUpperFace && raytrace.sideHit == EnumFacing.UP)
        }

        if (!raytrace.typeOfHit.isBlock || !shouldPlace) return

        when {
            scaffoldMode == "StraightEdgeSneak" && shiftBridgeSneaking -> {
                // Fixed: WaitTickUtils.schedule(delay, name, action)
                WaitTickUtils.schedule(straightSneakReleaseDelay, "straightRelease") {
                    if (state) {
                        mc.gameSettings.keyBindSneak.pressed = false
                        shiftBridgeSneaking = false
                    }
                }
            }
            scaffoldMode == "SDShiftBridge" && shiftBridgeSneaking -> {
                mc.gameSettings.keyBindSneak.pressed = false
                shiftBridgeSneaking = false
            }
        }

        tryToPlaceBlock(stack, raytrace.blockPos, raytrace.sideHit, raytrace.hitVec, attempt = true) { onSuccess() }
        if (lastClick) findBlockToSwitchNextTick(stack)
        if (trackCPS) CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
    }

    // Legit：只放行「实际正前方」的疾跑包，左右/后退一律取消（疾跑会被检测）
    val onSprintPacket = handler<PacketEvent> { event ->
        if (scaffoldMode != "Legit") return@handler
        val packet = event.packet
        if (packet !is C0BPacketEntityAction) return@handler
        if (packet.action != C0BPacketEntityAction.Action.START_SPRINTING &&
            packet.action != C0BPacketEntityAction.Action.STOP_SPRINTING) return@handler
        val input = mc.thePlayer?.movementInput ?: return@handler
        // moveForward/moveStrafe 此刻已是「反向后的实际方向」
        if (input.moveForward <= 0f || input.moveStrafe != 0f) event.cancelEvent()
    }

    override fun onDisable() {
        val player = mc.thePlayer ?: return
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            mc.gameSettings.keyBindSneak.pressed = false
            if (eagleSneaking && player.isSneaking) player.isSneaking = false
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindRight)) mc.gameSettings.keyBindRight.pressed = false
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)) mc.gameSettings.keyBindLeft.pressed = false
        if (autoF5) mc.gameSettings.thirdPersonView = 0

        placeRotation = null
        mc.timer.timerSpeed = 1f
        shiftBridgeSneaking = false
        SilentHotbar.resetSlot(this)
        // 别让自动右键卡住（否则关掉模块后仍会一直高频点右键，直到手动点一次右键才重置）
        mc.gameSettings.keyBindUseItem.pressed = false
        options.instant = false
    }

    val onMove = handler<MoveEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        if (!safeWalkValue.isActive() || shouldGoDown) return@handler
        if (airSafe || player.onGround) event.isSafeWalk = true
    }

    val jumpHandler = handler<JumpEvent> { event ->
        if (!jumpStrafe) return@handler
        if (event.eventState == EventState.POST) {
            MovementUtils.strafe((if (!isLookingDiagonally) jumpStraightStrafe else jumpDiagonalStrafe).random())
        }
    }

    val onRender3D = handler<Render3DEvent> {
        val player = mc.thePlayer ?: return@handler

        val shouldBother = !(shouldGoDown || scaffoldMode == "Expand" && expandLength > 1) && extraClicks && (player.isMoving || MovementUtils.speed > 0.03)
        if (shouldBother) {
            currRotation.let {
                performBlockRaytrace(it, mc.playerController.blockReachDistance)?.let { raytrace ->
                    val timePassed = System.currentTimeMillis() - extraClick.lastClick >= extraClick.delay
                    if (raytrace.typeOfHit.isBlock && timePassed) {
                        extraClick = ExtraClickInfo(TimeUtils.randomClickDelay(extraClickCPS.first, extraClickCPS.last), System.currentTimeMillis(), extraClick.clicks + 1)
                    }
                }
            }
        }

        if (!mark) return@handler
        repeat(if (scaffoldMode == "Expand") expandLength + 1 else 2) {
            val yaw = player.rotationYaw.toRadiansD()
            val x = if (omniDirectionalExpand) -sin(yaw).roundToInt() else player.horizontalFacing.directionVec.x
            val z = if (omniDirectionalExpand) cos(yaw).roundToInt() else player.horizontalFacing.directionVec.z
            val blockPos = BlockPos(
                player.posX + x * it,
                if (shouldKeepLaunchPosition && launchY <= player.posY) launchY - 1.0 else player.posY - (if (player.posY == player.posY + 0.5) 0.0 else 1.0) - if (shouldGoDown) 1.0 else 0.0,
                player.posZ + z * it
            )
            val placeInfo = PlaceInfo.get(blockPos)
            if (blockPos.isReplaceable && placeInfo != null) {
                RenderUtils.drawBlockBox(blockPos, markColor, false)
                return@handler
            }
        }
    }

    fun search(
        blockPosition: BlockPos, raycast: Boolean, area: Boolean, horizontalOnly: Boolean = false,
    ): Boolean {
        val player = mc.thePlayer ?: return false
        options.instant = false

        if (!blockPosition.isReplaceable) {
            if (autoF5) mc.gameSettings.thirdPersonView = 0
            return false
        } else {
            if (autoF5 && mc.gameSettings.thirdPersonView != 1) mc.gameSettings.thirdPersonView = 1
        }

        val maxReach = mc.playerController.blockReachDistance
        val eyes = player.eyes
        var placeRotation: PlaceRotation? = null
        var currPlaceRotation: PlaceRotation?

        for (side in EnumFacing.entries) {
            if (horizontalOnly && side.axis == EnumFacing.Axis.Y) continue
            val neighbor = blockPosition.offset(side)
            if (!neighbor.canBeClicked()) continue

            if (!area || isGodBridgeEnabled) {
                currPlaceRotation = findTargetPlace(blockPosition, neighbor, Vec3(0.5, 0.5, 0.5), side, eyes, maxReach, raycast) ?: continue
                placeRotation = compareDifferences(currPlaceRotation, placeRotation)
            } else {
                for (x in 0.1..0.9) {
                    for (y in 0.1..0.9) {
                        for (z in 0.1..0.9) {
                            currPlaceRotation = findTargetPlace(blockPosition, neighbor, Vec3(x, y, z), side, eyes, maxReach, raycast) ?: continue
                            placeRotation = compareDifferences(currPlaceRotation, placeRotation)
                        }
                    }
                }
            }
        }

        placeRotation ?: return false

        if (options.rotationsActive && !isGodBridgeEnabled) {
            val rotationDifference = rotationDifference(placeRotation.rotation, currRotation)
            val rotationDifference2 = rotationDifference(placeRotation.rotation / 90F, currRotation / 90F)
            val simPlayer = SimulatedPlayer.fromClientPlayer(player.movementInput)
            simPlayer.tick()
            options.instant = blockSafe && simPlayer.fallDistance > player.fallDistance + 0.05 && rotationDifference > rotationDifference2 / 2
            setRotation(placeRotation.rotation, if (scaffoldMode == "Telly") 1 else options.resetTicks)
        }

        this.placeRotation = placeRotation
        return true
    }

    private fun modifyVec(original: Vec3, direction: EnumFacing, pos: Vec3, shouldModify: Boolean): Vec3 {
        if (!shouldModify) return original
        val side = direction.opposite
        return when (side.axis ?: return original) {
            EnumFacing.Axis.Y -> Vec3(original.xCoord, pos.yCoord + side.directionVec.y.coerceAtLeast(0), original.zCoord)
            EnumFacing.Axis.X -> Vec3(pos.xCoord + side.directionVec.x.coerceAtLeast(0), original.yCoord, original.zCoord)
            EnumFacing.Axis.Z -> Vec3(original.xCoord, original.yCoord, pos.zCoord + side.directionVec.z.coerceAtLeast(0))
        }
    }

    private fun findTargetPlace(
        pos: BlockPos, offsetPos: BlockPos, vec3: Vec3, side: EnumFacing, eyes: Vec3, maxReach: Float, raycast: Boolean,
    ): PlaceRotation? {
        val world = mc.theWorld ?: return null
        val vec = (Vec3(pos) + vec3).addVector(side.directionVec.x * vec3.xCoord, side.directionVec.y * vec3.yCoord, side.directionVec.z * vec3.zCoord)
        val distance = eyes.distanceTo(vec)
        if (raycast && (distance > maxReach || world.rayTraceBlocks(eyes, vec, false, true, false) != null)) return null
        val diff = vec - eyes
        if (side.axis != EnumFacing.Axis.Y) {
            val dist = abs(if (side.axis == EnumFacing.Axis.Z) diff.zCoord else diff.xCoord)
            if (dist < minDist && scaffoldMode != "Telly") return null
        }

        var rotation = toRotation(vec, false)
        val roundYaw90 = round(rotation.yaw / 90f) * 90f
        val roundYaw45 = round(rotation.yaw / 45f) * 45f
        rotation = when (options.rotationMode) {
            "Stabilized" -> Rotation(roundYaw45, rotation.pitch)
            "ReverseYaw" -> Rotation(if (!isLookingDiagonally) roundYaw90 else roundYaw45, rotation.pitch)
            else -> rotation
        }.fixedSensitivity()

        performBlockRaytrace(currRotation, maxReach)?.let { raytrace ->
            if (raytrace.blockPos == offsetPos && (!raycast || raytrace.sideHit == side.opposite)) {
                return PlaceRotation(PlaceInfo(raytrace.blockPos, side.opposite, modifyVec(raytrace.hitVec, side, Vec3(offsetPos), !raycast)), currRotation)
            }
        }

        val raytrace = performBlockRaytrace(rotation, maxReach) ?: return null
        val multiplier = if (options.legitimize) 3 else 1
        if (raytrace.blockPos == offsetPos && (!raycast || raytrace.sideHit == side.opposite) && canUpdateRotation(currRotation, rotation, multiplier)) {
            return PlaceRotation(PlaceInfo(raytrace.blockPos, side.opposite, modifyVec(raytrace.hitVec, side, Vec3(offsetPos), !raycast)), rotation)
        }
        return null
    }

    private fun performBlockRaytrace(rotation: Rotation, maxReach: Float): MovingObjectPosition? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null
        val eyes = player.eyes
        val rotationVec = getVectorForRotation(rotation)
        val reach = eyes + (rotationVec * maxReach.toDouble())
        return world.rayTraceBlocks(eyes, reach, false, false, true)
    }

    private fun compareDifferences(new: PlaceRotation, old: PlaceRotation?, rotation: Rotation = currRotation): PlaceRotation {
        return if (old == null || rotationDifference(new.rotation, rotation) < rotationDifference(old.rotation, rotation)) new else old
    }

    private fun findBlockToSwitchNextTick(stack: ItemStack) {
        if (autoBlock in arrayOf("Off", "Switch")) return
        val switchAmount = if (earlySwitch) amountBeforeSwitch else 0
        if (stack.stackSize > switchAmount) return
        val switchSlot = if (earlySwitch) InventoryUtils.findBlockStackInHotbarGreaterThan(amountBeforeSwitch) ?: InventoryUtils.findBlockInHotbar() ?: return
        else InventoryUtils.findBlockInHotbar() ?: return
        SilentHotbar.selectSlotSilently(this, switchSlot, render = autoBlock == "Pick", resetManually = true)
    }

    private fun updatePlacedBlocksForTelly() {
        if (blocksUntilAxisChange > horizontalPlacements + verticalPlacements) {
            blocksUntilAxisChange = 0
            horizontalPlacements = horizontalPlacementsRange.random()
            verticalPlacements = verticalPlacementsRange.random()
            return
        }
        blocksUntilAxisChange++
    }

    private fun tryToPlaceBlock(
        stack: ItemStack, clickPos: BlockPos, side: EnumFacing, hitVec: Vec3, attempt: Boolean = false,
        onSuccess: () -> Unit = { }
    ): Boolean {
        val thePlayer = mc.thePlayer ?: return false
        val prevSize = stack.stackSize
        val clickedSuccessfully = thePlayer.onPlayerRightClick(clickPos, side, hitVec, stack)
        if (clickedSuccessfully) {
            if (!attempt) {
                delayTimer.reset()
                if (thePlayer.onGround) {
                    thePlayer.motionX *= speedModifier
                    thePlayer.motionZ *= speedModifier
                }
            }
            if (!silentSwing) {
                if (swing) thePlayer.swingItem() else sendPacket(C0APacketAnimation())
            }
            if (isManualJumpOptionActive) blocksPlacedUntilJump++
            updatePlacedBlocksForTelly()
            if (stack.stackSize <= 0) {
                thePlayer.inventory.mainInventory[SilentHotbar.currentSlot] = null
                ForgeEventFactory.onPlayerDestroyItem(thePlayer, stack)
            } else if (stack.stackSize != prevSize || mc.playerController.isInCreativeMode) mc.entityRenderer.itemRenderer.resetEquippedProgress()
            placeRotation = null
            placedBlocksWithoutEagle++
            onSuccess()
        } else {
            if (thePlayer.sendUseItem(stack)) mc.entityRenderer.itemRenderer.resetEquippedProgress2()
        }
        return clickedSuccessfully
    }

    fun handleMovementOptions(input: MovementInput) {
        val player = mc.thePlayer ?: return
        if (!state) return

        if (scaffoldMode == "Legit") {
            // 反向移动：W↔S、A↔D（用 LegitReverseMovement 控制；
            // 如果感觉正好反了，多半是 Scaffold 自带的反向也在生效，把它关掉即可）
            if (legitReverseMovement) {
                input.moveForward = -input.moveForward
                input.moveStrafe = -input.moveStrafe
            }
            // 只有「实际正前方」才允许疾跑；左右/后退一律禁跑（疾跑会被检测）
            if (input.moveForward <= 0f || input.moveStrafe != 0f) {
                player.isSprinting = false
                mc.gameSettings.keyBindSprint.pressed = false
            }
            // 边缘自动蹲（eagle）
            input.sneak = eagleSneaking || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
            // 本地硬锁方块栏：手上必须是方块才自动右键，并且真的切到方块槽，
            // 防止静默切换还没生效就右键、把原来的物品（食物/药水等）用掉。
            val blockSlot = InventoryUtils.findBlockInHotbar()
            if (legitAutoPlace && blockSlot != null && blockSlot in 0..8) {
                if (player.inventory.currentItem != blockSlot) player.inventory.currentItem = blockSlot
                mc.gameSettings.keyBindUseItem.pressed = true
            } else {
                mc.gameSettings.keyBindUseItem.pressed = false
            }
            return
        }

        if (scaffoldMode in arrayOf("SDShiftBridge", "StraightEdgeSneak")) {
            input.sneak = shiftBridgeSneaking || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
            return
        }

        if (!slow && speedLimiter && MovementUtils.speed > speedLimit) {
            input.moveStrafe = 0f
            input.moveForward = 0f
            return
        }

        when (zitterMode.lowercase()) {
            "off" -> return
            "smooth" -> {
                val notOnGround = !player.onGround || !player.isCollidedVertically
                if (player.onGround) input.sneak = eagleSneaking || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
                if (input.jump || mc.gameSettings.keyBindJump.isKeyDown || notOnGround) {
                    zitterTickTimer.reset()
                    if (useSneakMidAir) input.sneak = true
                    if (!notOnGround && !input.jump) input.moveStrafe = if (zitterDirection) 1f else -1f
                    else input.moveStrafe = 0f
                    zitterDirection = !zitterDirection
                    if (mc.gameSettings.keyBindLeft.isKeyDown) input.moveStrafe++
                    if (mc.gameSettings.keyBindRight.isKeyDown) input.moveStrafe--
                    return
                }
                if (zitterTickTimer.hasTimePassed()) {
                    zitterDirection = !zitterDirection
                    zitterTickTimer.reset()
                } else zitterTickTimer.update()
                if (zitterDirection) input.moveStrafe = -1f else input.moveStrafe = 1f
            }
            "teleport" -> {
                MovementUtils.strafe(zitterSpeed)
                val yaw = (player.rotationYaw + if (zitterDirection) 90.0 else -90.0).toRadians()
                player.motionX -= sin(yaw) * zitterStrength
                player.motionZ += cos(yaw) * zitterStrength
                zitterDirection = !zitterDirection
            }
        }
    }

    private var isOnRightSide = false

    private fun generateGodBridgeRotations(ticks: Int) {
        val player = mc.thePlayer ?: return
        val direction = if (options.applyServerSide) MovementUtils.direction.toDegreesF() + 180f else MathHelper.wrapAngleTo180_float(player.rotationYaw)
        val movingYaw = round(direction / 45) * 45
        val steps45 = arrayListOf(-135f, -45f, 45f, 135f)
        val isMovingStraight = if (options.applyServerSide) movingYaw % 90 == 0f else movingYaw in steps45 && player.movementInput.isSideways
        if (!player.isNearEdge(2.5f)) return
        if (!player.isMoving) {
            placeRotation?.run {
                val axisMovement = floor(this.rotation.yaw / 90) * 90
                val yaw = axisMovement + 45f
                val pitch = 75f
                setRotation(Rotation(yaw, pitch), ticks)
                return
            }
            if (!options.keepRotation) return
        }
        val rotation = if (isMovingStraight) {
            if (player.onGround) {
                isOnRightSide = floor(player.posX + cos(movingYaw.toRadians()) * 0.5) != floor(player.posX) || floor(player.posZ + sin(movingYaw.toRadians()) * 0.5) != floor(player.posZ)
                val posInDirection = BlockPos(player.positionVector.offset(EnumFacing.fromAngle(movingYaw.toDouble()), 0.6))
                val isLeaningOffBlock = player.position.down().block == air
                val nextBlockIsAir = posInDirection.down().block == air
                if (isLeaningOffBlock && nextBlockIsAir) isOnRightSide = !isOnRightSide
            }
            val side = if (options.applyServerSide) if (isOnRightSide) 45f else -45f else 0f
            Rotation(movingYaw + side, if (useOptimizedPitch) 73.5f else customGodPitch)
        } else {
            Rotation(movingYaw, 75.6f)
        }.fixedSensitivity()
        godBridgeTargetRotation = rotation
        setRotation(rotation, ticks)
    }

    override val tag
        get() = if (towerMode != "None") ("$scaffoldMode | $towerMode") else scaffoldMode

    data class ExtraClickInfo(val delay: Int, val lastClick: Long, var clicks: Int)
}