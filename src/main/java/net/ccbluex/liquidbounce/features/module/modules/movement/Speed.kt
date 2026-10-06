package net.ccbluex.liquidbounce.features.module.modules.movement

import PredictionTimer2
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.aac.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.hypixel.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.intave.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.matrix.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.ncp.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.spartan.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.spectre.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.verus.*
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.vulcan.*
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold2
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import kotlin.math.*

object Speed : Module("Speed", Category.MOVEMENT) {

    private val speedModes = arrayOf(
        NCPBHop, NCPFHop, SNCPBHop, NCPHop, NCPYPort, UNCPHop, UNCPHopNew,
        AACHop3313, AACHop350, AACHop4, AACHop5,
        SpartanYPort,
        SpectreLowHop, SpectreBHop, SpectreOnGround,
        VerusHop, VerusFHop, VerusLowHop, VerusLowHopNew,
        VulcanHop, VulcanLowHop, VulcanGround288,
        OldMatrixHop, MatrixHop, MatrixSlowHop,
        IntaveHop14,
        TeleportCubeCraft, HypixelHop, HypixelLowHop, BlocksMCHop,
        Boost, Frame, MiJump, OnGround, SlowHop, Legit, CustomSpeed,
        PredictionTimer, PredictionTimer2, FastLowHop,
        GrimACSpeed, Grim2Speed   // Grim 模式
    )

    private val deprecatedMode = arrayOf(
        TeleportCubeCraft, OldMatrixHop, VerusLowHop,
        SpectreLowHop, SpectreBHop, SpectreOnGround,
        AACHop3313, AACHop350, AACHop4,
        NCPBHop, NCPFHop, SNCPBHop, NCPHop, NCPYPort, MiJump, Frame
    )

    private val showDeprecated by boolean("DeprecatedMode", true).onChanged { value ->
        mode.changeValue(modesList.first { it !in deprecatedMode }.modeName)
        mode.updateValues(modesList.filter { value || it !in deprecatedMode }.map { it.modeName }.toTypedArray())
    }

    private var modesList = speedModes
    val mode = choices("Mode", modesList.map { it.modeName }.toTypedArray(), "NCPBHop")
    val notOnScaffold by boolean("NotOnScaffold", false)
    private var isOnScaffold = (Scaffold.state || Scaffold2.state)

    // ========== 静默朝敌移动选项 ==========
    private val silentAimMove by boolean("SilentAimMove", false)
    private val silentAimRange by float("SilentAimRange", 4f, 1f..6f) { silentAimMove }
    private val silentAimPlayersOnly by boolean("SilentAimPlayersOnly", true) { silentAimMove }

    private var targetYaw: Float? = null

    // Custom Speed 选项
    val customY by float("CustomY", 0.42f, 0f..4f) { mode.get() == "Custom" }
    val customGroundStrafe by float("CustomGroundStrafe", 1.6f, 0f..2f) { mode.get() == "Custom" }
    val customAirStrafe by float("CustomAirStrafe", 0f, 0f..2f) { mode.get() == "Custom" }
    val customGroundTimer by float("CustomGroundTimer", 1f, 0.1f..2f) { mode.get() == "Custom" }
    val customAirTimerTick by int("CustomAirTimerTick", 5, 1..20) { mode.get() == "Custom" }
    val customAirTimer by float("CustomAirTimer", 1f, 0.1f..2f) { mode.get() == "Custom" }

    // Extra options
    val resetXZ by boolean("ResetXZ", false) { mode.get() == "Custom" }
    val resetY by boolean("ResetY", false) { mode.get() == "Custom" }
    val notOnConsuming by boolean("NotOnConsuming", false) { mode.get() == "Custom" }
    val notOnFalling by boolean("NotOnFalling", false) { mode.get() == "Custom" }
    val notOnVoid by boolean("NotOnVoid", true) { mode.get() == "Custom" }

    // TeleportCubecraft Speed
    val cubecraftPortLength by float("CubeCraft-PortLength", 1f, 0.1f..2f) { mode.get() == "TeleportCubeCraft" }

    // IntaveHop14 Speed
    val boost by boolean("Boost", true) { mode.get() == "IntaveHop14" }
    val initialBoostMultiplier by float("InitialBoostMultiplier", 1f, 0.01f..10f) { boost && mode.get() == "IntaveHop14" }
    val intaveLowHop by boolean("LowHop", true) { mode.get() == "IntaveHop14" }
    val strafeStrength by float("StrafeStrength", 0.29f, 0.1f..0.29f) { mode.get() == "IntaveHop14" }
    val groundTimer by float("GroundTimer", 0.5f, 0.1f..5f) { mode.get() == "IntaveHop14" }
    val airTimer by float("AirTimer", 1.09f, 0.1f..5f) { mode.get() == "IntaveHop14" }

    // UNCPHopNew Speed
    private val pullDown by boolean("PullDown", true) { mode.get() == "UNCPHopNew" }
    val onTick by int("OnTick", 5, 5..9) { pullDown && mode.get() == "UNCPHopNew" }
    val onHurt by boolean("OnHurt", true) { pullDown && mode.get() == "UNCPHopNew" }
    val shouldBoost by boolean("ShouldBoost", true) { mode.get() == "UNCPHopNew" }
    val timerBoost by boolean("TimerBoost", true) { mode.get() == "UNCPHopNew" }
    val damageBoost by boolean("DamageBoost", true) { mode.get() == "UNCPHopNew" }
    val lowHop by boolean("LowHop", true) { mode.get() == "UNCPHopNew" }
    val airStrafe by boolean("AirStrafe", true) { mode.get() == "UNCPHopNew" }

    // MatrixHop Speed
    val matrixLowHop by boolean("LowHop", true) { mode.get() == "MatrixHop" || mode.get() == "MatrixSlowHop" }
    val extraGroundBoost by float("ExtraGroundBoost", 0.2f, 0f..0.5f) { mode.get() == "MatrixHop" || mode.get() == "MatrixSlowHop" }

    // HypixelLowHop Speed
    val glide by boolean("Glide", true) { mode.get() == "HypixelLowHop" }

    // BlocksMCHop Speed
    val fullStrafe by boolean("FullStrafe", true) { mode.get() == "BlocksMCHop" }
    val bmcLowHop by boolean("LowHop", true) { mode.get() == "BlocksMCHop" }
    val bmcDamageBoost by boolean("DamageBoost", true) { mode.get() == "BlocksMCHop" }
    val damageLowHop by boolean("DamageLowHop", false) { mode.get() == "BlocksMCHop" }
    val safeY by boolean("SafeY", true) { mode.get() == "BlocksMCHop" }

    // GrimAC Speed 选项（新版）
    val grimBoundingBoxSize by float("GrimBoundingBoxSize", 0.4f, 0f..1f) { mode.get() == "GrimAC" }
    val grimInPlayerSpeed by float("GrimInPlayerSpeed", 0.08f, 0f..0.08f) { mode.get() == "GrimAC" }
    val grimMoveFlyingIncrease by float("GrimMoveFlyingIncrease", 0.0001f, 0f..0.001f) { mode.get() == "GrimAC" }

    // Grim2 Speed 选项（移植自 Rise）
    val grim2HighPing by boolean("Grim2HighPing", false) { mode.get() == "Grim2" }
    val grim2Speed by float("Grim2Speed", 1f, 0f..1f) { mode.get() == "Grim2" }

    // PredictionTimer
    val predictionGroundTimer by float("PredictionGroundTimer", 1.5f, 1f..3.0f) { mode.get() == "PredictionTimer" }

    // PredictionTimer2
    val prediction2TimerSpeed by float("Prediction2TimerSpeed", 1.5f, 1f..3.0f) { mode.get() == "PredictionTimer2" }
    val prediction2CycleLength by int("Prediction2CycleLength", 8, 5..60) { mode.get() == "PredictionTimer2" }
    val prediction2BoostDuration by int("Prediction2BoostDuration", 2, 1..5) { mode.get() == "PredictionTimer2" }

    // FastLowHop 选项
    val fastLowHopGroundSpeed by float("FastLowHopGroundSpeed", 1.6f, 1f..2.5f) { mode.get() == "FastLowHop" }
    val fastLowHopTimer by float("FastLowHopTimer", 1.2f, 1f..1.5f) { mode.get() == "FastLowHop" }

    /**
     * 更新静默瞄准目标的角度
     */
    private fun updateSilentAimTarget() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return
        if (!silentAimMove) return

        val target = world.loadedEntityList
            .filter { it is EntityLivingBase && it != player && it.isEntityAlive }
            .filter { !silentAimPlayersOnly || it is EntityPlayer }
            .minByOrNull { player.getDistanceToEntityBox(it) }
            ?.let { it as EntityLivingBase }

        if (target != null && player.getDistanceToEntityBox(target) <= silentAimRange) {
            val diffX = target.posX - player.posX
            val diffZ = target.posZ - player.posZ
            targetYaw = Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90f
        } else {
            targetYaw = null
        }
    }

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        isOnScaffold = (Scaffold.state || Scaffold2.state)

        if (thePlayer.isSneaking)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        if (thePlayer.isMoving && !sprintManually)
            thePlayer.isSprinting = true

        // 同步 FastLowHop 参数
        if (mode.get() == "FastLowHop") {
            FastLowHop.groundSpeed = fastLowHopGroundSpeed
            FastLowHop.timerBoost = fastLowHopTimer
        }

        updateSilentAimTarget()

        modeModule.onUpdate()
    }

    val onMotion = handler<MotionEvent> { event ->
        val thePlayer = mc.thePlayer ?: return@handler

        if (thePlayer.isSneaking || event.eventState != EventState.PRE)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        if (thePlayer.isMoving && !sprintManually)
            thePlayer.isSprinting = true

        // 调用无参的 onMotion
        modeModule.onMotion()
    }

    val onPostMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.POST)
            return@handler

        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onPostMotion()
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onMoveInput(event)
    }

    val onMove = handler<MoveEvent> { event ->
        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onMove(event)

        // 静默朝敌移动：覆盖移动方向至目标角度
        if (silentAimMove && targetYaw != null && mc.thePlayer?.isMoving == true) {
            val speed = sqrt(event.x * event.x + event.z * event.z)
            val yawRad = Math.toRadians(targetYaw!!.toDouble())
            event.x = -sin(yawRad) * speed
            event.z = cos(yawRad) * speed
        }
    }

    val tickHandler = handler<GameTickEvent> {
        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onTick()
    }

    val onPlayerTick = handler<PlayerTickEvent> {
        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onPlayerTick()
    }

    val onStrafe = handler<StrafeEvent> {
        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onStrafe()
    }

    val onJump = handler<JumpEvent> { event ->
        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onJump(event)
    }

    val onPacket = handler<PacketEvent> { event ->
        if (mc.thePlayer?.isSneaking == true)
            return@handler

        if (notOnScaffold && isOnScaffold)
            return@handler

        modeModule.onPacket(event)
    }

    override fun onEnable() {
        if (mc.thePlayer == null)
            return

        mc.timer.timerSpeed = 1f

        modeModule.onEnable()
    }

    override fun onDisable() {
        if (mc.thePlayer == null)
            return

        mc.timer.timerSpeed = 1f
        mc.thePlayer.speedInAir = 0.02f

        targetYaw = null

        modeModule.onDisable()
    }

    override val tag
        get() = mode.get()

    private val modeModule
        get() = speedModes.find { it.modeName == mode.get() }!!

    private val sprintManually
        get() = modeModule in arrayOf(Legit)
}