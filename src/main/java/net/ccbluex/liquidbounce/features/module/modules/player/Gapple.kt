/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import de.florianmichael.vialoadingbase.ViaLoadingBase
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.features.module.modules.render.WaterMark
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extras.BlinkUtils
import net.ccbluex.liquidbounce.utils.extras.StuckUtils
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedGradientRectCorner
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.Items
import net.minecraft.network.play.client.*
import net.minecraft.util.BlockPos
import java.awt.Color
import net.ccbluex.liquidbounce.features.module.modules.render.InfoDisplay

object Gapple : Module("Gapple", Category.PLAYER) {
    // 基础设置
    private val heal by int("health", 20, 0..40)
    private val sendDelay by int("SendDelay", 10, 1..10)
    private val sendOnceTicks = 1
    private val stuck by boolean("Stuck", false)
    private val stopMove by boolean("StopMove", false)
    var noCancelC02 by boolean("NoCancelC02", false)
    var noC02 by boolean("NoC02", false)
    private val autoGapple by boolean("AutoGapple", false)

    // ---------- 智能延迟吃苹果 ----------
    private val smartGapple by boolean("SmartGapple", false) { autoGapple }
    private val maxSmartDelay by int("MaxSmartDelay", 55, 0..200) { smartGapple }
    private var smartDelayTicks = 15
    private var smartCooldown = 0
    private var lastHealth = 0f
    private var lastGappleTime = 0L

    // ---------- 通知与统计 ----------
    private val notifyEat by boolean("NotifyEat", false) { autoGapple }
    private var totalEaten = 0

    // ---------- HUD 外观设置 ----------
    // 容器背景色
    private val hudBackgroundColor by color("BackgroundColor", Color(110, 110, 110, 200))
    // 容器圆角
    private val hudCornerRadius by float("CornerRadius", 12f, 0f..30f)

    // 文字设置
    private val hudTextColor by color("TextColor", Color.WHITE)
    private val hudTextScale by float("TextScale", 1f, 0.5f..2f)

    // 进度条设置
    private val progressColor by color("ProgressColor", Color(138, 43, 226)) // 默认紫色
    private val cooldownColor by color("CooldownColor", Color(255, 160, 0))
    private val progressHeight by float("ProgressHeight", 3f, 1f..10f)
    private val progressWidthPadding by float("ProgressWidthPadding", 10f, 0f..30f) // 进度条距容器左右内边距

    // 阴影设置
    private val shadow by boolean("Shadow", true)
    private val shadowColor by color("ShadowColor", Color(0, 0, 0, 255)) { shadow }
    private val shadowStrength by float("ShadowStrength", 4f, 0f..10f) { shadow }
    private val shadowMask by boolean("ShadowMask", true) { shadow } // 是否裁切阴影

    // 背景模糊设置
    private val backgroundBlur by boolean("BackgroundBlur", false)

    // 位置自定义
    private val hudXOffset by int("XOffset", 0, -200..200)
    private val hudYOffset by int("YOffset", 0, -200..200)

    // 内部状态
    private var slot = -1
    private var c03s = 0
    private var c02s = 0
    private var canStart = false

    var eating: Boolean = false
    var pulsing: Boolean = false
    var target: EntityLivingBase? = null

    override fun onEnable() {
        c03s = 0
        slot = InventoryUtils.findItem(36, 45, Items.golden_apple) ?: -1
        if (slot != -1) slot -= 36
        totalEaten = 0
    }

    override fun onDisable() {
        eating = false
        if (canStart) {
            pulsing = false
            BlinkUtils.stopBlink()
        }
        if (stuck) StuckUtils.stopStuck()
    }

    /** 统计背包（9-44）中金苹果的总数量 */
    private fun countGoldenApples(): Int {
        val player = mc.thePlayer ?: return 0
        var count = 0
        for (i in 9..44) {
            val stack = player.inventory.getStackInSlot(i) ?: continue
            if (stack.item === Items.golden_apple) count += stack.stackSize
        }
        return count
    }

    val onTick = handler<PreTickEvent> {
        // 20 秒无吃苹果自动重置智能延迟变量
        if (smartGapple && lastGappleTime != 0L &&
            System.currentTimeMillis() - lastGappleTime > 20000
        ) {
            smartDelayTicks = 15
            lastHealth = 0f
            smartCooldown = 0
            lastGappleTime = 0L
        }

        if (mc.thePlayer.health < heal) {
            if (!eating && smartGapple && smartCooldown > 0) {
                smartCooldown--
                return@handler
            }

            if (!eating) {
                if (autoGapple) {
                    try {
                        val remaining = countGoldenApples()
                        if (remaining < 3) {
                            if (WaterMark.state) WaterMark.showWarning("Gapple", "Only $remaining golden apple(s) left!")
                        }
                    } catch (_: Exception) {
                    }
                }

                if (smartGapple) {
                    lastHealth = mc.thePlayer.health
                }
                target = KillAura.target
                c03s = 0
                slot = InventoryUtils.findItem(36, 45, Items.golden_apple) ?: -1
                if (slot != -1) slot -= 36
            }

            if (MinecraftInstance.mc.thePlayer == null || MinecraftInstance.mc.thePlayer.isDead) {
                BlinkUtils.stopBlink()
                state = false
                return@handler
            }
            if (slot == -1) {
                state = false
                return@handler
            }

            if (eating) {
                if (stuck) StuckUtils.stuck()
                if (!BlinkUtils.blinking) {
                    BlinkUtils.blink(
                        C09PacketHeldItemChange::class.java,
                        C0EPacketClickWindow::class.java,
                        C0DPacketCloseWindow::class.java
                    )
                    BlinkUtils.setCancelReturnPredicate(C07PacketPlayerDigging::class.java) { it -> (it as C07PacketPlayerDigging).status == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM }
                    BlinkUtils.setCancelReturnPredicate(C08PacketPlayerBlockPlacement::class.java) { it -> (it as C08PacketPlayerBlockPlacement).position.y == -1 }
                    BlinkUtils.setCancelReturnPredicate(C02PacketUseEntity::class.java) { it -> noCancelC02 }
                    BlinkUtils.setCancelReturnPredicate(C0APacketAnimation::class.java) { it -> noCancelC02 }
                    BlinkUtils.setCancelAction(C03PacketPlayer::class.java) { packet -> c03s++ }
                    BlinkUtils.setReleaseAction(C03PacketPlayer::class.java) { packet -> c03s-- }
                    BlinkUtils.setReleaseReturnPredicateMap(C02PacketUseEntity::class.java) { packet -> !eating && noC02 }
                    BlinkUtils.setCancelAction(C02PacketUseEntity::class.java) { packet -> c02s++ }
                    BlinkUtils.setReleaseAction(C02PacketUseEntity::class.java) { packet -> c02s-- }
                    canStart = true
                    InfoDisplay.showLog("Gapple startAte", 1000)
                }
            } else {
                eating = true
                InfoDisplay.showLog("eatting", 1000)
            }

            if (c03s >= 32) {
                InfoDisplay.showLog("wait 32 c03s", 1000)
                eating = false
                pulsing = true
                BlinkUtils.resetBlackList()
                sendPacket(C09PacketHeldItemChange(slot), false)
                sendPacket(C08PacketPlayerBlockPlacement(MinecraftInstance.mc.thePlayer.inventoryContainer.getSlot(slot + 36).stack), false)
                if (ViaLoadingBase.getInstance().targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_12_2)) {
                    sendPacket(C08PacketPlayerBlockPlacement(BlockPos(-1, -2, -1), 255, null, 0.0f, 0.0f, 0.0f), false)
                }
                BlinkUtils.stopBlink()
                sendPacket(C09PacketHeldItemChange(MinecraftInstance.mc.thePlayer.inventory.currentItem), false)
                pulsing = false

                lastGappleTime = System.currentTimeMillis()

                totalEaten++
                if (notifyEat) {
                    try {
                        if (WaterMark.state) WaterMark.showSuccess("Gapple", "Eat golden apple ($totalEaten total)")
                    } catch (_: Exception) {
                    }
                }

                if (autoGapple) {
                    if (smartGapple) {
                        val currentHealth = mc.thePlayer.health
                        if (lastHealth > 0f) {
                            if (currentHealth > lastHealth) smartDelayTicks += 5
                            else smartDelayTicks -= 5
                            smartDelayTicks = smartDelayTicks.coerceIn(0, maxSmartDelay)
                        }
                        lastHealth = currentHealth
                        smartCooldown = smartDelayTicks
                    } else {
                        c03s = 0
                        slot = InventoryUtils.findItem(36, 45, Items.golden_apple) ?: -1
                        if (slot != -1) slot -= 36
                    }
                } else {
                    state = false
                }
                return@handler
            }

            if ((MinecraftInstance.mc.thePlayer.ticksExisted % sendDelay) == 0) {
                for (i in 0 until sendOnceTicks) {
                    BlinkUtils.releasePacket(true)
                }
            }
        } else {
            eating = false
            if (canStart) {
                pulsing = false
                BlinkUtils.stopBlink()
            }
            if (stuck) StuckUtils.stopStuck()
            smartCooldown = 0
            lastHealth = 0f
        }
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        if (eating && stopMove) {
            event.originalInput.moveStrafe = 0F
            event.originalInput.moveForward = 0F
        }
    }

    val onRender2D = handler<Render2DEvent> {
        drawProgressHUD()
    }

    private fun drawProgressHUD() {
        // 只在自动吃苹果开启时显示，且处于吃苹果或冷却状态
        if (!autoGapple || (!eating && smartCooldown <= 0)) return

        val sr = ScaledResolution(mc)
        val screenWidth = sr.scaledWidth.toFloat()
        val screenHeight = sr.scaledHeight.toFloat()

        // 计算容器尺寸
        val containerWidth = 140f
        val containerHeight = 48f // 可容纳文字与进度条
        // 默认位置：屏幕中央偏下，加上偏移
        val baseX = screenWidth / 2f - containerWidth / 2f + hudXOffset
        val baseY = screenHeight * 0.7f + hudYOffset

        // 1. 绘制背景模糊（如果启用）
        if (backgroundBlur && BlurSettings.active && BlurSettings.gapple) {
            val scale = sr.scaleFactor.toFloat()
            BlurUtils.drawOffsetBlur(
                baseX * scale,
                baseY * scale,
                containerWidth * scale,
                containerHeight * scale,
                samples = BlurSettings.passes,
                strength = 0f,
                radius = hudCornerRadius * scale
            )
        }

        // 2. 绘制阴影（在模糊层之上）
        if (shadow) {
            val blurRadius = (shadowStrength * 2f).toInt()
            if (blurRadius > 0) {
                GlowUtils.drawGlow(
                    x = baseX,
                    y = baseY,
                    width = containerWidth,
                    height = containerHeight,
                    blurRadius = blurRadius,
                    color = shadowColor,
                    cornerRadius = hudCornerRadius,
                    onlyBorder = false,
                    mask = shadowMask
                )
            }
        }

        // 3. 绘制灰色圆角容器背景
        drawRoundedRect(
            baseX,
            baseY,
            baseX + containerWidth,
            baseY + containerHeight,
            hudBackgroundColor.rgb,
            hudCornerRadius
        )

        // 4. 绘制“Gapple”文字（居中，位于容器上部）
        val text = "Gapple"
        val textScale = hudTextScale
        val textWidth = Fonts.fontGoogleSans35.getStringWidth(text) * textScale
        val textX = baseX + containerWidth / 2f - textWidth / 2f
        val textY = baseY + 8f // 距离顶部间距
        Fonts.fontGoogleSans35.drawString(
            text,
            textX,
            textY,
            hudTextColor.rgb,
            true
        )

        // 5. 绘制进度条（圆头，位于文字下方，左右内缩）
        val progressY = baseY + containerHeight - 12f // 距底部约12像素
        val progressAvailableWidth = containerWidth - progressWidthPadding * 2
        val progressX = baseX + progressWidthPadding
        val progressMaxWidth = progressAvailableWidth

        // 根据状态确定进度条颜色和进度值
        val (progressRatio, barColor) = if (eating) {
            (c03s.toFloat() / 32f).coerceIn(0f, 1f) to progressColor
        } else {
            // 冷却状态
            (smartCooldown.toFloat() / smartDelayTicks.coerceAtLeast(1)).coerceIn(0f, 1f) to cooldownColor
        }

        val progressWidth = progressMaxWidth * progressRatio
        val progressHeight = progressHeight
        val progressRadius = progressHeight / 2f

        // 绘制进度条背景（可选，如果希望有底色，可以添加，但根据设计可能不需要）
        // 这里为了简洁，只绘制有进度的部分
        if (progressWidth > 0f) {
            // 使用圆角矩形绘制，圆角为高度一半实现圆头
            drawRoundedGradientRectCorner(
                progressX,
                progressY,
                progressX + progressWidth,
                progressY + progressHeight,
                progressRadius,
                barColor.rgb,
                barColor.rgb // 单色，使用相同颜色
            )
        }
    }
}