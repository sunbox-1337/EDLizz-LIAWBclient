/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.exploit.Disabler
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isLookingOnEntities
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.isOnGround
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.speed
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils.runWithModifiedRaycastResult
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockSoulSand
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGameOver
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.*
import net.minecraft.util.*
import net.minecraft.util.EnumFacing.DOWN
import net.minecraft.world.WorldSettings
import javax.vecmath.Vector2d
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import net.minecraft.network.play.server.*
import kotlin.random.Random

object Velocity : Module("Velocity", Category.COMBAT) {

    /**
     * OPTIONS
     */
    private val mode by choices(
        "Mode", arrayOf(
            "Simple", "AAC", "AACPush", "AACZero", "AACv4",
            "Reverse", "SmoothReverse", "Jump", "Glitch", "Legit",
            "GhostBlock", "Vulcan", "S32Packet", "MatrixReduce",
            "IntaveReduce", "Delay", "GrimC03", "Hypixel", "HypixelAir",
            "Click", "BlocksMC", "GrimCombat", "Polar", "Buffer", "Grim",
            "NoSave"
        ), "Simple"
    )

    private val horizontal by float("Horizontal", 0F, -1F..1F) { mode in arrayOf("Simple", "AAC", "Legit") }
    private val vertical by float("Vertical", 0F, -1F..1F) { mode in arrayOf("Simple", "Legit") }

    // Reverse
    private val reverseStrength by float("ReverseStrength", 1F, 0.1F..1F) { mode == "Reverse" }
    private val reverse2Strength by float("SmoothReverseStrength", 0.05F, 0.02F..0.1F) { mode == "SmoothReverse" }

    private val onLook by boolean("onLook", false) { mode in arrayOf("Reverse", "SmoothReverse") }
    private val range by float("Range", 3.0F, 1F..5.0F) {
        onLook && mode in arrayOf("Reverse", "SmoothReverse")
    }
    private val maxAngleDifference by float("MaxAngleDifference", 45.0f, 5.0f..90f) {
        onLook && mode in arrayOf("Reverse", "SmoothReverse")
    }

    // AAC Push
    private val aacPushXZReducer by float("AACPushXZReducer", 2F, 1F..3F) { mode == "AACPush" }
    private val aacPushYReducer by boolean("AACPushYReducer", true) { mode == "AACPush" }

    // AAC v4
    private val aacv4MotionReducer by float("AACv4MotionReducer", 0.62F, 0F..1F) { mode == "AACv4" }

    // Legit
    private val legitDisableInAir by boolean("DisableInAir", true) { mode == "Legit" }

    // Chance
    private val chance by int("Chance", 100, 0..100) { mode == "Jump" || mode == "Legit" }

    // Jump
    private val jumpCooldownMode by choices("JumpCooldownMode", arrayOf("Ticks", "ReceivedHits"), "Ticks")
    { mode == "Jump" }
    private val ticksUntilJump by int("TicksUntilJump", 4, 0..20)
    { jumpCooldownMode == "Ticks" && mode == "Jump" }
    private val hitsUntilJump by int("ReceivedHitsUntilJump", 2, 0..5)
    { jumpCooldownMode == "ReceivedHits" && mode == "Jump" }

    // Ghost Block
    private val hurtTimeRange by intRange("HurtTime", 1..9, 1..10) {
        mode == "GhostBlock"
    }
    var polarHurtTime = Random.nextInt(8, 10)

    // Delay
    private val spoofDelay by int("SpoofDelay", 500, 0..5000) { mode == "Delay" }
    var delayMode = false

    // IntaveReduce
    private val reduceFactor by float("Factor", 0.6f, 0.6f..1f) { mode == "IntaveReduce" }
    private val hurtTime by int("HurtTime", 9, 1..10) { mode == "IntaveReduce" }

    // Buffer Mode
    private val bufferDelay by int("BufferDelay", 3, 1..10) { mode == "Buffer" }

    private val pauseOnExplosion by boolean("PauseOnExplosion", true)
    private val ticksToPause by int("TicksToPause", 20, 1..50) { pauseOnExplosion }

    // Limits
    private val limitMaxMotionValue = boolean("LimitMaxMotion", false) { mode == "Simple" }
    private val maxXZMotion by float("MaxXZMotion", 0.4f, 0f..1.9f) { limitMaxMotionValue.isActive() }
    private val maxYMotion by float("MaxYMotion", 0.36f, 0f..0.46f) { limitMaxMotionValue.isActive() }

    private val clicks by intRange("Clicks", 3..5, 1..20) { mode == "Click" }
    private val hurtTimeToClick by int("HurtTimeToClick", 10, 0..10) { mode == "Click" }
    private val whenFacingEnemyOnly by boolean("WhenFacingEnemyOnly", true) { mode == "Click" }
    private val ignoreBlocking by boolean("IgnoreBlocking", false) { mode == "Click" }
    private val clickRange by float("ClickRange", 3f, 1f..6f) { mode == "Click" }
    private val swingMode by choices("SwingMode", arrayOf("Off", "Normal", "Packet"), "Normal") { mode == "Click" }

    private val grimrange by float("Range", 3.5f, 0f..6f) { mode == "GrimCombat" || mode == "NoSave" }
    private val attackCountValue by int("Attack Counts", 12, 1..16) { mode == "GrimCombat" || mode == "NoSave" }

    private val fireCheckValue by boolean("FireCheck", false) { mode == "GrimCombat" || mode == "NoSave" }
    private val waterCheckValue by boolean("WaterCheck", false) { mode == "GrimCombat" || mode == "NoSave" }
    private val fallCheckValue by boolean("FallCheck", false) { mode == "GrimCombat" || mode == "NoSave" }
    private val consumecheck by boolean("ConsumableCheck", false) { mode == "GrimCombat" || mode == "NoSave" }
    private val raycastValue by boolean("Ray cast", false) { mode == "GrimCombat" || mode == "NoSave" }
    private val debugMessageValue by boolean("Debug", true) { mode == "GrimCombat" }

    // NoSave 专用选项
    private val noSaveCancelCount by int("CancelCount", 3, 0..20) { mode == "NoSave" }
    private val noSaveGrimDuration by float("GrimDuration", 5f, 0f..30f, suffix = "Seconds") { mode == "NoSave" }

    /**
     * VALUES
     */
    private val velocityTimer = MSTimer()
    private var hasReceivedVelocity = false

    private var reverseHurt = false
    private var jump = false
    private var limitUntilJump = 0
    private var intaveTick = 0
    private var lastAttackTime = 0L
    private var intaveDamageTick = 0

    private val packets = LinkedHashMap<Packet<*>, Long>()

    private var timerTicks = 0
    private var transaction = false
    private var absorbedVelocity = false
    private var pauseTicks = 0

    private val bufferedPackets = mutableListOf<BufferedPacket>()

    var velocityInput: Boolean = false
    private var attacked = false
    private var reduceXZ = 1.00000
    var entity: Entity? = null
    var velX = 0
    var velY = 0
    var velZ = 0

    // NoSave 状态
    private var noSaveCanceled = 0
    private var noSaveGrimActive = false
    private var noSaveGrimStartTime = 0L

    override val tag
        get() = if (mode == "Simple" || mode == "Legit") {
            val horizontalPercentage = (horizontal * 100).toInt()
            val verticalPercentage = (vertical * 100).toInt()
            "$horizontalPercentage% $verticalPercentage%"
        } else mode

    override fun onDisable() {
        pauseTicks = 0
        mc.thePlayer?.speedInAir = 0.02F
        timerTicks = 0
        bufferedPackets.clear()
        noSaveCanceled = 0
        noSaveGrimActive = false
        reset()
    }

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (thePlayer.isInLiquid || thePlayer.isInWeb || thePlayer.isDead)
            return@handler

        when (mode.lowercase()) {
            "glitch" -> {
                thePlayer.noClip = hasReceivedVelocity
                if (thePlayer.hurtTime == 7) thePlayer.motionY = 0.4
                hasReceivedVelocity = false
            }

            "reverse" -> {
                val nearbyEntity = getNearestEntityInRange()
                if (!hasReceivedVelocity) return@handler
                if (nearbyEntity != null) {
                    if (!thePlayer.onGround) {
                        if (onLook && !isLookingOnEntities(nearbyEntity, maxAngleDifference.toDouble())) return@handler
                        speed *= reverseStrength
                    } else if (velocityTimer.hasTimePassed(80)) hasReceivedVelocity = false
                }
            }

            "smoothreverse" -> {
                val nearbyEntity = getNearestEntityInRange()
                if (hasReceivedVelocity) {
                    if (nearbyEntity == null) {
                        thePlayer.speedInAir = 0.02F
                        reverseHurt = false
                    } else {
                        if (onLook && !isLookingOnEntities(nearbyEntity, maxAngleDifference.toDouble())) {
                            hasReceivedVelocity = false
                            thePlayer.speedInAir = 0.02F
                            reverseHurt = false
                        } else {
                            if (thePlayer.hurtTime > 0) reverseHurt = true
                            if (!thePlayer.onGround) {
                                thePlayer.speedInAir = if (reverseHurt) reverse2Strength else 0.02F
                            } else if (velocityTimer.hasTimePassed(80)) {
                                hasReceivedVelocity = false
                                thePlayer.speedInAir = 0.02F
                                reverseHurt = false
                            }
                        }
                    }
                }
            }

            "aac" -> if (hasReceivedVelocity && velocityTimer.hasTimePassed(80)) {
                thePlayer.motionX *= horizontal
                thePlayer.motionZ *= horizontal
                hasReceivedVelocity = false
            }

            "aacv4" ->
                if (thePlayer.hurtTime > 0 && !thePlayer.onGround) {
                    val reduce = aacv4MotionReducer
                    thePlayer.motionX *= reduce
                    thePlayer.motionZ *= reduce
                }

            "aacpush" -> {
                if (jump) {
                    if (thePlayer.onGround) jump = false
                } else {
                    if (thePlayer.hurtTime > 0 && thePlayer.motionX != 0.0 && thePlayer.motionZ != 0.0)
                        thePlayer.onGround = true
                    if (thePlayer.hurtResistantTime > 0 && aacPushYReducer && !Speed.handleEvents())
                        thePlayer.motionY -= 0.014999993
                }
                if (thePlayer.hurtResistantTime >= 19) {
                    val reduce = aacPushXZReducer
                    thePlayer.motionX /= reduce
                    thePlayer.motionZ /= reduce
                }
            }

            "aaczero" ->
                if (thePlayer.hurtTime > 0) {
                    if (!hasReceivedVelocity || thePlayer.onGround || thePlayer.fallDistance > 2F) return@handler
                    thePlayer.motionY -= 1.0
                    thePlayer.isAirBorne = true
                    thePlayer.onGround = true
                } else hasReceivedVelocity = false

            "legit" -> {
                if (legitDisableInAir && !isOnGround(0.5)) return@handler
                if (mc.thePlayer.maxHurtResistantTime != mc.thePlayer.hurtResistantTime || mc.thePlayer.maxHurtResistantTime == 0) return@handler
                if (nextInt(endExclusive = 100) < chance) {
                    val horizontal = horizontal / 100f
                    val vertical = vertical / 100f
                    thePlayer.motionX *= horizontal.toDouble()
                    thePlayer.motionZ *= horizontal.toDouble()
                    thePlayer.motionY *= vertical.toDouble()
                }
            }

            "intavereduce" -> {
                if (!hasReceivedVelocity) return@handler
                intaveTick++
                if (mc.thePlayer.hurtTime == 2) {
                    intaveDamageTick++
                    if (thePlayer.onGround && intaveTick % 2 == 0 && intaveDamageTick <= 10) {
                        thePlayer.tryJump()
                        intaveTick = 0
                    }
                    hasReceivedVelocity = false
                }
            }

            "hypixel" -> {
                if (hasReceivedVelocity && thePlayer.onGround) absorbedVelocity = false
            }

            "hypixelair" -> {
                if (hasReceivedVelocity) {
                    if (thePlayer.onGround) thePlayer.tryJump()
                    hasReceivedVelocity = false
                }
            }

            "grimcombat" -> {
                if (attacked) {
                    if (ViaLoadingBase.getInstance().getTargetVersion().version > 47) {
                        mc.thePlayer.motionX = velX * reduceXZ / 8000.0
                        mc.thePlayer.motionY = velY / 8000.0
                        mc.thePlayer.motionZ = velZ * reduceXZ / 8000.0
                        attacked = false
                        reduceXZ = 1.00000
                        if (mc.thePlayer.hurtTime === 0) velocityInput = false
                    } else {
                        if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.onGround) {
                            mc.thePlayer.addVelocity(-1.3E-10, -1.3E-10, -1.3E-10)
                            mc.thePlayer.isSprinting = false
                        }
                    }
                }
            }

            "polar" -> {
                if (thePlayer.hurtTime == polarHurtTime) {
                    thePlayer.tryJump()
                    polarHurtTime = nextInt(8, 10)
                }
            }

            "grim" -> {
                if (hasReceivedVelocity) {
                    if (!thePlayer.isJumping && thePlayer.isSprinting && thePlayer.onGround && thePlayer.hurtTime == 9) {
                        thePlayer.tryJump()
                        limitUntilJump = 0
                    }
                    hasReceivedVelocity = false
                }
            }
        }

        // NoSave 模式也执行 GrimCombat 的减速逻辑
        if (mode == "NoSave" && attacked) {
            if (ViaLoadingBase.getInstance().getTargetVersion().version > 47) {
                mc.thePlayer.motionX = velX * reduceXZ / 8000.0
                mc.thePlayer.motionY = velY / 8000.0
                mc.thePlayer.motionZ = velZ * reduceXZ / 8000.0
                attacked = false
                reduceXZ = 1.00000
                if (mc.thePlayer.hurtTime === 0) velocityInput = false
            } else {
                if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.onGround) {
                    mc.thePlayer.addVelocity(-1.3E-10, -1.3E-10, -1.3E-10)
                    mc.thePlayer.isSprinting = false
                }
            }
        }
    }

    val onGameTick = handler<GameTickEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        // NoSave 模式：检查是否要重置 GrimCombat 状态
        if (mode == "NoSave" && noSaveGrimActive && System.currentTimeMillis() - noSaveGrimStartTime >= noSaveGrimDuration * 1000L) {
            noSaveGrimActive = false
            noSaveCanceled = 0
        }

        // Buffer 模式处理
        if (mode == "Buffer") {
            val iterator = bufferedPackets.iterator()
            while (iterator.hasNext()) {
                val bufferedPacket = iterator.next()
                bufferedPacket.remainingTicks--
                if (bufferedPacket.remainingTicks <= 0) {
                    when (bufferedPacket.packet) {
                        is S12PacketEntityVelocity -> {
                            val packet = bufferedPacket.packet
                            thePlayer.motionX = packet.motionX / 8000.0
                            thePlayer.motionY = packet.motionY / 8000.0
                            thePlayer.motionZ = packet.motionZ / 8000.0
                        }
                        is S27PacketExplosion -> {
                            val packet = bufferedPacket.packet
                            thePlayer.motionX += packet.func_149149_c().toDouble()
                            thePlayer.motionY += packet.func_149144_d().toDouble()
                            thePlayer.motionZ += packet.func_149147_e().toDouble()
                        }
                    }
                    iterator.remove()
                }
            }
        }

        if (mode != "Click" && mode != "Grim") return@handler
        mc.theWorld ?: return@handler

        val grimHurtTimeToClick = 10
        val grimWhenFacingEnemyOnly = true
        val grimClickRange = 3.0f
        val grimClicks = 3..4
        val grimSwingMode = "Normal"

        val currentHurtTimeToClick = if (mode == "Grim") grimHurtTimeToClick else hurtTimeToClick
        val currentWhenFacingEnemyOnly = if (mode == "Grim") grimWhenFacingEnemyOnly else whenFacingEnemyOnly
        val currentClickRange = if (mode == "Grim") grimClickRange else clickRange
        val currentClicks = if (mode == "Grim") grimClicks else clicks
        val currentSwingMode = if (mode == "Grim") grimSwingMode else swingMode

        if (thePlayer.hurtTime != currentHurtTimeToClick || ignoreBlocking && (thePlayer.isBlocking || KillAura.blockStatus)) return@handler

        var target = mc.objectMouseOver?.entityHit
        if (target == null) {
            if (currentWhenFacingEnemyOnly) {
                var result: Entity? = null
                runWithModifiedRaycastResult(
                    currentRotation ?: thePlayer.rotation,
                    currentClickRange.toDouble(),
                    0.0
                ) { result = it.entityHit?.takeIf { isSelected(it, true) } }
                target = result
            } else getNearestEntityInRange(currentClickRange)?.takeIf { isSelected(it, true) }
        }
        target ?: return@handler

        val swingHand = {
            when (currentSwingMode.lowercase()) {
                "normal" -> thePlayer.swingItem()
                "packet" -> sendPacket(C0APacketAnimation())
            }
        }
        repeat(currentClicks.random()) {
            thePlayer.attackEntityWithModifiedSprint(target, true) { swingHand() }
        }
    }

    val onAttack = handler<AttackEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        when (mode.lowercase()) {
            "intavereduce" -> {
                if (player.hurtTime == hurtTime && System.currentTimeMillis() - lastAttackTime <= 8000) {
                    player.motionX *= reduceFactor
                    player.motionZ *= reduceFactor
                }
                lastAttackTime = System.currentTimeMillis()
            }
            "grimcombat" -> {
                if (attacked) {
                    if (ViaLoadingBase.getInstance().targetVersion.olderThanOrEqualTo(ProtocolVersion.v1_8)) {
                        mc.netHandler.networkManager.sendPacket(C0APacketAnimation())
                        mc.netHandler.networkManager.sendPacket(
                            C02PacketUseEntity(event.targetEntity, C02PacketUseEntity.Action.ATTACK)
                        )
                    } else {
                        mc.netHandler.networkManager.sendPacket(
                            C02PacketUseEntity(event.targetEntity, C02PacketUseEntity.Action.ATTACK)
                        )
                        mc.netHandler.networkManager.sendPacket(C0APacketAnimation())
                    }
                }
            }
        }
    }

    private fun checkAir(blockPos: BlockPos): Boolean {
        val world = mc.theWorld ?: return false
        if (!world.isAirBlock(blockPos)) return false
        timerTicks = 20
        sendPackets(
            C03PacketPlayer(true),
            C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, DOWN)
        )
        world.setBlockToAir(blockPos)
        return true
    }

    private fun getDirection(): Double {
        var moveYaw = mc.thePlayer.rotationYaw
        when {
            mc.thePlayer.moveForward != 0f && mc.thePlayer.moveStrafing == 0f -> {
                moveYaw += if (mc.thePlayer.moveForward > 0) 0 else 180
            }
            mc.thePlayer.moveForward != 0f && mc.thePlayer.moveStrafing != 0f -> {
                if (mc.thePlayer.moveForward > 0) moveYaw += if (mc.thePlayer.moveStrafing > 0) -45 else 45 else moveYaw -= if (mc.thePlayer.moveStrafing > 0) -45 else 45
                moveYaw += if (mc.thePlayer.moveForward > 0) 0 else 180
            }
            mc.thePlayer.moveStrafing != 0f && mc.thePlayer.moveForward == 0f -> {
                moveYaw += if (mc.thePlayer.moveStrafing > 0) -90 else 90
            }
        }
        return Math.floorMod(moveYaw.toInt(), 360).toDouble()
    }

    val onPacket = handler<PacketEvent>(priority = 1) { event ->
        val thePlayer = mc.thePlayer ?: return@handler
        val packet = event.packet

        if (!handleEvents()) return@handler

        if (pauseTicks > 0) {
            pauseTicks--
            return@handler
        }

        if (event.isCancelled) return@handler

        // ===== NoSave 模式：智能取消 / GrimCombat 处理 =====
        if (mode == "NoSave") {
            if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                if (!noSaveGrimActive) {
                    // 取消阶段
                    if (noSaveCanceled < noSaveCancelCount) {
                        event.cancelEvent()
                        noSaveCanceled++
                        return@handler
                    } else {
                        // 达到次数上限，进入 GrimCombat 阶段
                        noSaveGrimActive = true
                        noSaveGrimStartTime = System.currentTimeMillis()
                        // 直接使用 GrimCombat 处理这个包（不取消，而是交给 grim 逻辑）
                        handleGrimCombatVelocity(event, packet)
                        return@handler
                    }
                } else {
                    // 已在 GrimCombat 阶段，直接处理
                    handleGrimCombatVelocity(event, packet)
                    return@handler
                }
            }
            if (packet is S27PacketExplosion) {
                event.cancelEvent()
                return@handler
            }
        }

        if ((packet is S12PacketEntityVelocity && thePlayer.entityId == packet.entityID)
            || (packet is S27PacketExplosion && (thePlayer.motionY + packet.field_149153_g) > 0.0)
        ) {
            val s12 = (event.packet as S12PacketEntityVelocity)
            velocityTimer.reset()

            if (pauseOnExplosion && packet is S27PacketExplosion && (thePlayer.motionY + packet.field_149153_g) > 0.0
                && ((thePlayer.motionX + packet.field_149152_f) != 0.0 || (thePlayer.motionZ + packet.field_149159_h) != 0.0)
            ) {
                pauseTicks = ticksToPause
            }

            when (mode.lowercase()) {
                "grimcombat" -> handleGrimCombatVelocity(event, packet as S12PacketEntityVelocity)

                "simple" -> handleVelocity(event)

                "aac", "reverse", "smoothreverse", "aaczero", "ghostblock", "intavereduce", "grim" -> hasReceivedVelocity = true

                "jump" -> {
                    var packetDirection = 0.0
                    when (packet) {
                        is S12PacketEntityVelocity -> {
                            if (packet.entityID != thePlayer.entityId) return@handler
                            val motionX = packet.motionX.toDouble()
                            val motionZ = packet.motionZ.toDouble()
                            packetDirection = atan2(motionX, motionZ)
                        }
                        is S27PacketExplosion -> {
                            val motionX = thePlayer.motionX + packet.field_149152_f
                            val motionZ = thePlayer.motionZ + packet.field_149159_h
                            packetDirection = atan2(motionX, motionZ)
                        }
                    }
                    val degreePlayer = getDirection()
                    val degreePacket = Math.floorMod(packetDirection.toDegrees().toInt(), 360).toDouble()
                    var angle = abs(degreePacket + degreePlayer)
                    val threshold = 120.0
                    angle = Math.floorMod(angle.toInt(), 360).toDouble()
                    val inRange = angle in 180 - threshold / 2..180 + threshold / 2
                    if (inRange) hasReceivedVelocity = true
                }

                "glitch" -> {
                    if (!thePlayer.onGround) return@handler
                    hasReceivedVelocity = true
                    event.cancelEvent()
                }

                "matrixreduce" -> {
                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        packet.motionX = (packet.getMotionX() * 0.33).toInt()
                        packet.motionZ = (packet.getMotionZ() * 0.33).toInt()
                        if (thePlayer.onGround) {
                            packet.motionX = (packet.getMotionX() * 0.86).toInt()
                            packet.motionZ = (packet.getMotionZ() * 0.86).toInt()
                        }
                    }
                }

                "blocksmc" -> {
                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        hasReceivedVelocity = true
                        event.cancelEvent()
                        sendPacket(C0BPacketEntityAction(thePlayer, START_SNEAKING))
                        sendPacket(C0BPacketEntityAction(thePlayer, STOP_SNEAKING))
                    }
                }

                "grimc03" -> {
                    if (thePlayer.isMoving) {
                        hasReceivedVelocity = true
                        event.cancelEvent()
                    }
                }

                "hypixel" -> {
                    hasReceivedVelocity = true
                    if (!thePlayer.onGround) {
                        if (!absorbedVelocity) {
                            event.cancelEvent()
                            absorbedVelocity = true
                            return@handler
                        }
                    }
                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        packet.motionX = (thePlayer.motionX * 8000).toInt()
                        packet.motionZ = (thePlayer.motionZ * 8000).toInt()
                    }
                }

                "hypixelair" -> {
                    hasReceivedVelocity = true
                    event.cancelEvent()
                }

                "vulcan" -> event.cancelEvent()

                "s32packet" -> {
                    hasReceivedVelocity = true
                    event.cancelEvent()
                }

                "buffer" -> {
                    event.cancelEvent()
                    bufferedPackets.add(BufferedPacket(packet, bufferDelay))
                }
            }
        }

        if (mode == "BlocksMC" && hasReceivedVelocity) {
            if (packet is C0BPacketEntityAction) {
                hasReceivedVelocity = false
                event.cancelEvent()
            }
        }

        if (mode == "Vulcan") {
            if (Disabler.handleEvents() && Disabler.verusCombat && (!Disabler.onlyCombat || Disabler.isOnCombat)) return@handler
            if (packet is S32PacketConfirmTransaction) {
                event.cancelEvent()
                sendPacket(
                    C0FPacketConfirmTransaction(
                        if (transaction) 1 else -1,
                        if (transaction) -1 else 1,
                        transaction
                    ), false
                )
                transaction = !transaction
            }
        }

        if (mode == "S32Packet" && packet is S32PacketConfirmTransaction) {
            if (!hasReceivedVelocity) return@handler
            event.cancelEvent()
            hasReceivedVelocity = false
        }
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        if (mode == "GrimC03") {
            if (timerTicks > 0 && mc.timer.timerSpeed <= 1) {
                val timerSpeed = 0.8f + (0.2f * (20 - timerTicks) / 20)
                mc.timer.timerSpeed = timerSpeed.coerceAtMost(1f)
                --timerTicks
            } else if (mc.timer.timerSpeed <= 1) {
                mc.timer.timerSpeed = 1f
            }
            if (hasReceivedVelocity) {
                val pos = BlockPos(player.posX, player.posY, player.posZ)
                if (checkAir(pos)) hasReceivedVelocity = false
            }
        }
    }

    val onDelayPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (event.isCancelled) return@handler
        if (mode == "Delay") {
            if (packet is S32PacketConfirmTransaction || packet is S12PacketEntityVelocity) {
                event.cancelEvent()
                synchronized(packets) { packets[packet] = System.currentTimeMillis() }
            }
            delayMode = true
        } else delayMode = false
    }

    val onWorld = handler<WorldEvent> {
        packets.clear()
        bufferedPackets.clear()
        noSaveCanceled = 0
        noSaveGrimActive = false
    }

    val onGameLoop = handler<GameLoopEvent> {
        if (mode == "Delay") sendPacketsByOrder(false)
    }

    private fun sendPacketsByOrder(velocity: Boolean) {
        synchronized(packets) {
            packets.entries.removeAll { (packet, timestamp) ->
                if (velocity || timestamp <= System.currentTimeMillis() - spoofDelay) {
                    PacketUtils.schedulePacketProcess(packet)
                    true
                } else false
            }
        }
    }

    private fun reset() {
        sendPacketsByOrder(true)
        packets.clear()
        bufferedPackets.clear()
        noSaveCanceled = 0
        noSaveGrimActive = false
        velocityInput = false
        attacked = false
    }

    val onJump = handler<JumpEvent> { event ->
        val thePlayer = mc.thePlayer
        if (thePlayer == null || thePlayer.isInLiquid || thePlayer.isInWeb) return@handler
        when (mode.lowercase()) {
            "aacpush" -> {
                jump = true
                if (!thePlayer.isCollidedVertically) event.cancelEvent()
            }
            "aaczero" -> if (thePlayer.hurtTime > 0) event.cancelEvent()
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler
        if (mode == "Jump" && hasReceivedVelocity) {
            if (!player.isJumping && nextInt(endExclusive = 100) < chance && shouldJump() && player.isSprinting && player.onGround && player.hurtTime == 9) {
                player.tryJump()
                limitUntilJump = 0
            }
            hasReceivedVelocity = false
            return@handler
        }
        if (mode == "Grim" && hasReceivedVelocity) {
            if (!player.isJumping && player.isSprinting && player.onGround && player.hurtTime == 9) {
                player.tryJump()
                limitUntilJump = 0
            }
            hasReceivedVelocity = false
            return@handler
        }
        when (jumpCooldownMode.lowercase()) {
            "ticks" -> limitUntilJump++
            "receivedhits" -> if (player.hurtTime == 9) limitUntilJump++
        }
    }

    val onBlockBB = handler<BlockBBEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        if (mode == "GhostBlock" && hasReceivedVelocity) {
            if (player.hurtTime in hurtTimeRange) {
                if (event.block is BlockAir && event.y == mc.thePlayer.posY.toInt() + 1) {
                    event.boundingBox = AxisAlignedBB(
                        event.x.toDouble(), event.y.toDouble(), event.z.toDouble(),
                        event.x + 1.0, event.y + 1.0, event.z + 1.0
                    )
                }
            } else if (player.hurtTime == 0) hasReceivedVelocity = false
        }
    }

    private fun shouldJump() = when (jumpCooldownMode.lowercase()) {
        "ticks" -> limitUntilJump >= ticksUntilJump
        "receivedhits" -> limitUntilJump >= hitsUntilJump
        else -> false
    }

    private fun handleVelocity(event: PacketEvent) {
        val packet = event.packet
        if (packet is S12PacketEntityVelocity) {
            event.cancelEvent()
            if (horizontal == 0f && vertical == 0f) return
            if (horizontal != 0f) {
                var motionX = packet.realMotionX
                var motionZ = packet.realMotionZ
                if (limitMaxMotionValue.get()) {
                    val distXZ = sqrt(motionX * motionX + motionZ * motionZ)
                    if (distXZ > maxXZMotion) {
                        val ratioXZ = maxXZMotion / distXZ
                        motionX *= ratioXZ
                        motionZ *= ratioXZ
                    }
                }
                mc.thePlayer.motionX = motionX * horizontal
                mc.thePlayer.motionZ = motionZ * horizontal
            }
            if (vertical != 0f) {
                var motionY = packet.realMotionY
                if (limitMaxMotionValue.get()) motionY = motionY.coerceAtMost(maxYMotion + 0.00075)
                mc.thePlayer.motionY = motionY * vertical
            }
        } else if (packet is S27PacketExplosion) {
            if (horizontal != 0f && vertical != 0f) {
                packet.field_149152_f = 0f
                packet.field_149153_g = 0f
                packet.field_149159_h = 0f
                return
            }
            packet.field_149152_f *= horizontal
            packet.field_149153_g *= vertical
            packet.field_149159_h *= horizontal
            if (limitMaxMotionValue.get()) {
                val distXZ = sqrt(packet.field_149152_f * packet.field_149152_f + packet.field_149159_h * packet.field_149159_h)
                val distY = packet.field_149153_g
                val maxYMotion = maxYMotion + 0.00075f
                if (distXZ > maxXZMotion) {
                    val ratioXZ = maxXZMotion / distXZ
                    packet.field_149152_f *= ratioXZ
                    packet.field_149159_h *= ratioXZ
                }
                if (distY > maxYMotion) packet.field_149153_g *= maxYMotion / distY
            }
        }
    }

    private fun getNearestEntityInRange(range: Float = this.range): Entity? {
        val player = mc.thePlayer ?: return null
        return mc.theWorld.loadedEntityList.filter {
            isSelected(it, true) && player.getDistanceToEntityBox(it) <= range
        }.minByOrNull { player.getDistanceToEntityBox(it) }
    }

    fun soulSandCheck(): Boolean {
        val par1AxisAlignedBB = Minecraft.getMinecraft().thePlayer.entityBoundingBox.contract(0.001, 0.001, 0.001)
        val var4 = MathHelper.floor_double(par1AxisAlignedBB.minX)
        val var5 = MathHelper.floor_double(par1AxisAlignedBB.maxX + 1.0)
        val var6 = MathHelper.floor_double(par1AxisAlignedBB.minY)
        val var7 = MathHelper.floor_double(par1AxisAlignedBB.maxY + 1.0)
        val var8 = MathHelper.floor_double(par1AxisAlignedBB.minZ)
        val var9 = MathHelper.floor_double(par1AxisAlignedBB.maxZ + 1.0)
        for (var11 in var4 until var5) {
            for (var12 in var6 until var7) {
                for (var13 in var8 until var9) {
                    val pos = BlockPos(var11, var12, var13)
                    val var14 = Minecraft.getMinecraft().theWorld.getBlockState(pos).block
                    if (var14 is BlockSoulSand) return true
                }
            }
        }
        return false
    }

    private fun getAttackingEntity(): Entity? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null
        return world.loadedEntityList
            .filter { it is EntityLivingBase && it != player && it.canEntityBeSeen(player) }
            .minByOrNull { player.getDistanceToEntity(it) }
    }

    /**
     * GrimCombat 击退处理逻辑，供 NoSave 和 GrimCombat 模式共用
     */
    private fun handleGrimCombatVelocity(event: PacketEvent, packet: S12PacketEntityVelocity) {
        val thePlayer = mc.thePlayer ?: return
        if (thePlayer.isDead) return
        if (mc.currentScreen is GuiGameOver) return
        if (mc.playerController.currentGameType == WorldSettings.GameType.SPECTATOR) return
        if (thePlayer.isOnLadder) return
        if (thePlayer.isBurning && fireCheckValue) return
        if (thePlayer.isInWater && waterCheckValue) return
        if (thePlayer.fallDistance > 1.5 && fallCheckValue) return
        if (thePlayer.isEating && consumecheck) return
        if (soulSandCheck()) return

        val horizontalStrength = Vector2d(packet.getMotionX().toDouble(), packet.getMotionZ().toDouble()).length()
        if (horizontalStrength <= 1000) return

        val mouse = mc.objectMouseOver
        velocityInput = true
        var target: Entity? = null
        reduceXZ = 1.0

        if (mouse.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mouse.entityHit is EntityLivingBase
            && thePlayer.getDistanceToEntityBox(mouse.entityHit) <= KillAura.range) {
            target = mouse.entityHit
        }
        if (target == null && !raycastValue) {
            val auraTarget = KillAura.target
            if (auraTarget != null && thePlayer.getDistanceToEntityBox(auraTarget) <= grimrange) {
                target = auraTarget
            }
        }

        val state = thePlayer.serverSprintState
        if (target != null) {
            if (!state) sendPackets(C0BPacketEntityAction(thePlayer, START_SPRINTING))
            val count = attackCountValue
            for (i in 1..count) {
                if (ViaLoadingBase.getInstance().targetVersion.olderThanOrEqualTo(ProtocolVersion.v1_8)) {
                    mc.netHandler.networkManager.sendPacket(C0APacketAnimation())
                    mc.netHandler.networkManager.sendPacket(C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK))
                } else {
                    mc.netHandler.networkManager.sendPacket(C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK))
                    mc.netHandler.networkManager.sendPacket(C0APacketAnimation())
                    reduceXZ *= 0.6
                }
            }
            if (!state) sendPackets(C0BPacketEntityAction(thePlayer, STOP_SPRINTING))
            velX = packet.motionX
            velY = packet.motionY
            velZ = packet.motionZ
            attacked = true
            event.cancelEvent()  // GrimCombat 模式下必须取消原包
        }
    }

    data class BufferedPacket(val packet: Packet<*>, var remainingTicks: Int)
}