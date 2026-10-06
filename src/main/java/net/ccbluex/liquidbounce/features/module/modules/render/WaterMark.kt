package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.LiquidBounce.clientVersionText
import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Text.Companion
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.client.ServerUtils
import net.ccbluex.liquidbounce.utils.extensions.getPing
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawImage
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.animation.AnimationUtil
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting
import net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting
import net.minecraft.item.ItemBlock
import net.minecraft.util.ResourceLocation
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.pow

object WaterMark : Module("WaterMark", Category.RENDER) {
    // 文字与样式
    private val ClientName by text("ClientName", "LIAWB")
    private val animationSpeed by float("AnimationSpeed", 0.45F, 0.05F..1F)
    private val styles by choices("Styles", arrayOf("None","Normal","Opal","Lizz Morden"),"Normal")
    private val customip by boolean("customIP",false)
    private val ip by text("IP", "hidden.ip") { customip}

    // 颜色
    private val ColorA_ by int("Red",255,0..255)
    private val ColorB_ by int("Green",255,0..255)
    private val ColorC_ by int("Blue",255,0..255)

    // 阴影
    private val ShadowCheck by boolean("Shadow",false)
    private val shadowStrengh by int("ShadowStrength", 1, 1..2)
    private val ShadowMask by boolean("ShadowMask", true) { ShadowCheck }

    // 背景
    private val BackgroundAlpha by int("BackGroundAlpha",160,0..255)
    private val backgroundColor by color("BackgroundColor", Color(0, 0, 0, 255))

    // 统一圆角大小调节
    private val cornerRadius by float("CornerRadius", 12f, 0f..30f)

    // Opal 版本名
    private val versionNameUp by text("VersionName","development") {styles=="Opal"}

    // 通知按钮颜色
    private val ButtonColor by color("Button-Color",Color(20,150,180,255))
    private val ModuleNotify by boolean("Notification",true)

    // Scaffold 相关
    private val isScaffold by boolean("Scaffold",true)
    private val ScaffoldTheme by color("ScaffoldTheme",Color(255,255,255))
    private val maxBlocks by int("maxBlocks",576,64..576)

    // Chest 相关
    private val ChestTheme by boolean("Chest",true)
    private val ChestRounded by float("ChestRounded",4F,0.0F..8.0F)

    private val versionNameDown = clientVersionText

    // 状态枚举
    enum class State { Normal, Normal2, Normal3, Scaffold, Notify, Chest, None }

    // 内部状态
    private val progressLen = 120F
    private var ProgressBarAnimationWidth = progressLen
    @Suppress("unused")
    private val DECIMAL_FORMAT = DecimalFormat("0.00")
    private var scaledScreen = ScaledResolution(mc)
    private var width = scaledScreen.scaledWidth
    private var height = scaledScreen.scaledHeight
    private var island_State = State.Normal
    private var start_y = (height/20).toFloat()
    private var AnimStartX = (width/2).toFloat()
    private var AnimEndX = AnimStartX+100F
    private val NOTIFICATION_HEIGHT = 37f
    private var AnimModuleEndY = NOTIFICATION_HEIGHT
    private val notifications = CopyOnWriteArrayList<Notification>()

    // 组合背景色
    private fun getBgColor(): Color =
        Color(backgroundColor.red, backgroundColor.green, backgroundColor.blue, BackgroundAlpha)

    private fun applyBlur(x: Float, y: Float, w: Float, h: Float, sr: ScaledResolution, radius: Float = 0f) {
        if (!BlurSettings.active || !BlurSettings.watermark) return
        val scale = sr.scaleFactor.toFloat()
        BlurUtils.drawOffsetBlur(
            x * scale, y * scale,
            w * scale, h * scale,
            samples = BlurSettings.passes,
            strength = 0f,
            radius = radius * scale
        )
    }

    val onRender2D = handler<Render2DEvent>{
        updateNotifications()
        scaledScreen = ScaledResolution(mc); width = scaledScreen.scaledWidth; height = scaledScreen.scaledHeight
        island_State = State.Normal; start_y = (height/20).toFloat()
        if (moduleManager["Scaffold"]?.state == true && isScaffold) island_State = State.Scaffold
        else {
            when (styles) {
                "Normal" -> island_State = State.Normal
                "Opal" -> island_State = State.Normal2
                "None" -> island_State = State.None
                "Lizz Morden" -> island_State = State.Normal3
            }
            if (notifications.isNotEmpty() && ModuleNotify) island_State = State.Notify
        }
        if (ChestTheme && mc.currentScreen is GuiChest) island_State = State.Chest

        when (island_State) {
            State.Normal -> drawNormal()
            State.Normal2 -> drawNormal2()
            State.Normal3 -> drawNormal3()
            State.Scaffold -> drawScaffold()
            State.Chest -> drawChest()
            State.Notify -> drawNotificationsUI(scaledScreen, start_y)
            else -> {}
        }
    }

    // ========= 各样式绘制函数 ==========
    private fun drawNormal() {
        val username = mc.session.username; val fps = Minecraft.getDebugFPS(); val pings = mc.thePlayer.getPing()
        val colorRGB = Color(ColorA_, ColorB_, ColorC_,255)
        val drawTextWidth = " | ${username} | ${fps}fps | ${pings}ms"; val drawMainText = ClientName
        val onlyMainTextWidth = Fonts.fontGoogleSans40.getStringWidth(drawMainText); val allTextWidth = Fonts.fontGoogleSans40.getStringWidth(drawTextWidth)+onlyMainTextWidth
        val textUIHeight = 9F; val imageWidthHeights = 18F; val containerToUiDistance = 5F
        val borderHeight = containerToUiDistance*2+imageWidthHeights
        val allUILen = containerToUiDistance+imageWidthHeights+containerToUiDistance+allTextWidth+containerToUiDistance
        val startUIX = (width-allUILen)/2; val startYCalc = start_y+borderHeight/2
        val borderStartY =startYCalc-borderHeight/2; val startTextYCalc = startYCalc-textUIHeight/2+1F; val startImageYCalc = startYCalc-imageWidthHeights/2
        AnimStartX = AnimationUtil.base(AnimStartX.toDouble(),startUIX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)
        AnimEndX = AnimationUtil.base(AnimEndX.toDouble(),allUILen+startUIX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)
        applyBlur(AnimStartX, borderStartY, AnimEndX - AnimStartX, borderHeight, scaledScreen, cornerRadius)
        drawRoundedBorderRect(AnimStartX,borderStartY, AnimEndX,borderStartY+borderHeight,0.5F,
            Color(10,10,10, BackgroundAlpha).rgb, getBgColor().rgb, cornerRadius)
        ShowShadow(AnimStartX,borderStartY, AnimEndX-AnimStartX, borderHeight)
        drawImage(ResourceLocation("${CLIENT_NAME.lowercase()}/logo_icon.png"),AnimStartX+containerToUiDistance,startImageYCalc,imageWidthHeights.toInt(),imageWidthHeights.toInt(),colorRGB)
        Fonts.fontGoogleSans40.drawString(drawMainText,AnimStartX+containerToUiDistance+imageWidthHeights+containerToUiDistance,startTextYCalc,colorRGB.rgb)
        Fonts.fontGoogleSans40.drawString(drawTextWidth,AnimStartX+containerToUiDistance+imageWidthHeights+containerToUiDistance+onlyMainTextWidth,startTextYCalc,Color(255,255,255,255).rgb)
    }

    private fun drawNormal2() {
        val serverip = ServerUtils.remoteIp
        val playerPing = "${mc.thePlayer.getPing()}ms"
        val textWidth = Fonts.fontSemibold40.getStringWidth(ClientName)
        val colorAL = Color(ColorA_, ColorB_, ColorC_,255)
        val imageLen = 21F; val containerToUiDistance = 2F; val uiToUIDistance = 4F
        val textBar2 = max(Fonts.fontSemibold40.getStringWidth(versionNameUp),Fonts.fontSemibold35.getStringWidth(versionNameDown))
        val textBar3 = max(Fonts.fontSemibold40.getStringWidth(serverip),Fonts.fontSemibold35.getStringWidth(playerPing))
        val lineWidth = 2F
        val fastLen1 = containerToUiDistance+imageLen+uiToUIDistance
        val allLen = fastLen1+textWidth+uiToUIDistance+lineWidth+uiToUIDistance+textBar2+uiToUIDistance+lineWidth+uiToUIDistance+textBar3+containerToUiDistance+3F
        val startX = (width-allLen)/2
        AnimStartX = AnimationUtil.base(AnimStartX.toDouble(),startX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)
        AnimEndX = AnimationUtil.base(AnimEndX.toDouble(),allLen+startX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)

        applyBlur(AnimStartX, start_y, AnimEndX - AnimStartX, 27F, scaledScreen, cornerRadius)
        drawRoundedRect(AnimStartX, start_y, AnimEndX , start_y +27F, getBgColor().rgb, cornerRadius)
        ShowShadow(AnimStartX, start_y, AnimEndX-AnimStartX, 27F)

        drawImage(ResourceLocation("${CLIENT_NAME.lowercase()}/logo_icon.png"), startX+containerToUiDistance+2F, start_y +4F, 19, 19,colorAL)
        Fonts.fontSemibold40.drawString(ClientName,startX+fastLen1, start_y +9F,colorAL.rgb,false)
        Fonts.fontSemibold40.drawString("|",startX+fastLen1+textWidth+uiToUIDistance-1F, start_y +9F,Color(120,120,120,250).rgb,false)
        Fonts.fontSemibold40.drawString(versionNameUp,startX+fastLen1+textWidth+uiToUIDistance+lineWidth+uiToUIDistance, start_y +4.5F,Color(255,255,255,255).rgb,false)
        Fonts.fontSemibold35.drawString(versionNameDown,startX+fastLen1+textWidth+uiToUIDistance+lineWidth+uiToUIDistance, start_y +14F,Color(255,255,255,110).rgb,false)
        Fonts.fontSemibold40.drawString("|",startX+fastLen1+textWidth+uiToUIDistance+lineWidth+uiToUIDistance+textBar2+uiToUIDistance-1F, start_y +9F,Color(120,120,120,250).rgb,false)
        Fonts.fontSemibold40.drawString(serverip,startX+fastLen1+textWidth+uiToUIDistance+lineWidth+uiToUIDistance+textBar2+uiToUIDistance+lineWidth+uiToUIDistance, start_y +4.5F,Color(255,255,255,255).rgb,false)
        Fonts.fontSemibold35.drawString(playerPing,startX+fastLen1+textWidth+uiToUIDistance+lineWidth+uiToUIDistance+textBar2+uiToUIDistance+lineWidth+uiToUIDistance, start_y +14F,Color(255,255,255,110).rgb,false)
    }

    private fun drawNormal3() {
        val username = mc.session?.username ?: "Unknown"
        val fps = Minecraft.getDebugFPS()
        val pings = mc.thePlayer?.getPing() ?: 0
        val serverip = if (customip) ip else ServerUtils.remoteIp ?: "SinglePlayer"

        val colorRGB = Color(ColorA_, ColorB_, ColorC_, 255)
        val greenColor = Color(0, 255, 0, 255)
        val whiteColor = Color(255, 255, 255, 255)

        val iconSize = 13F; val padding = 8F; val elementSpacing = 6F; val dotSpacing = 12F
        val clientNameWidth = Fonts.fontGoogleSans40.getStringWidth(ClientName)
        val usernameWidth = Fonts.fontGoogleSans40.getStringWidth(username)-1f
        val pingTextWidth = Fonts.fontGoogleSans40.getStringWidth("${pings}ms")
        val toTextWidth = Fonts.fontGoogleSans40.getStringWidth(" to ")
        val serverIpWidth = Fonts.fontGoogleSans40.getStringWidth(serverip)
        val fpsTextWidth = Fonts.fontGoogleSans40.getStringWidth("${fps}fps")

        val totalWidth = padding + iconSize + elementSpacing + clientNameWidth + dotSpacing +
                iconSize + elementSpacing + usernameWidth + dotSpacing +
                iconSize + elementSpacing + pingTextWidth + toTextWidth + serverIpWidth + dotSpacing +
                iconSize + elementSpacing + fpsTextWidth + padding+10f
        val containerHeight = 28F
        val targetStartX = (width - totalWidth) / 2
        val targetEndX = targetStartX + totalWidth

        AnimStartX = AnimationUtil.base(AnimStartX.toDouble(), targetStartX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)
        AnimEndX = AnimationUtil.base(AnimEndX.toDouble(), targetEndX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)

        applyBlur(AnimStartX, start_y, AnimEndX - AnimStartX, containerHeight, scaledScreen, cornerRadius)
        drawRoundedBorderRect(AnimStartX, start_y, AnimEndX, start_y + containerHeight, 0.1f, getBgColor().rgb, getBgColor().rgb, cornerRadius)
        ShowShadow(AnimStartX, start_y, AnimEndX-AnimStartX, containerHeight)

        val textBaseY = start_y + (containerHeight - Fonts.fontGoogleSans40.FONT_HEIGHT) / 2 + Fonts.fontGoogleSans40.FONT_HEIGHT - 8
        val iconY = start_y + (containerHeight - iconSize) / 2
        var currentX = AnimStartX + padding

        drawImage(ResourceLocation("${CLIENT_NAME.lowercase()}/logo_icon.png"), currentX, iconY, iconSize.toInt(), iconSize.toInt(), colorRGB); currentX += iconSize + elementSpacing
        Fonts.fontGoogleSans40.drawString(ClientName, currentX, textBaseY, colorRGB.rgb); currentX += clientNameWidth + dotSpacing
        drawCenteredDot(currentX, textBaseY); currentX += 4
        drawImage(ResourceLocation("${CLIENT_NAME.lowercase()}/watermark_images/user.png"), currentX, iconY, iconSize.toInt(), iconSize.toInt(), whiteColor); currentX += iconSize + elementSpacing
        Fonts.fontGoogleSans40.drawString(username, currentX-1f, textBaseY, whiteColor.rgb); currentX += usernameWidth + dotSpacing
        drawCenteredDot(currentX, textBaseY); currentX += 4
        drawImage(ResourceLocation("${CLIENT_NAME.lowercase()}/watermark_images/ms.png"), currentX, iconY, iconSize.toInt(), iconSize.toInt(), greenColor); currentX += iconSize + elementSpacing
        Fonts.fontGoogleSans40.drawString("${pings}ms", currentX, textBaseY, greenColor.rgb); currentX += pingTextWidth
        Fonts.fontGoogleSans40.drawString(" to ", currentX, textBaseY, whiteColor.rgb); currentX += toTextWidth
        Fonts.fontGoogleSans40.drawString(serverip, currentX, textBaseY, whiteColor.rgb); currentX += serverIpWidth + dotSpacing
        drawCenteredDot(currentX-1, textBaseY); currentX += 3
        drawImage(ResourceLocation("${CLIENT_NAME.lowercase()}/watermark_images/fps.png"), currentX, iconY, iconSize.toInt(), iconSize.toInt(), whiteColor); currentX += iconSize + elementSpacing
        Fonts.fontGoogleSans40.drawString("${fps}fps", currentX, textBaseY, whiteColor.rgb)
    }

    private fun drawCenteredDot(x: Float, textBaseY: Float) {
        val dotY = textBaseY - Fonts.fontGoogleSans40.FONT_HEIGHT / 2 +3F
        Fonts.fontGoogleSans40.drawString("·", x-3f, dotY, Color(180, 180, 180, 255).rgb)
    }

    private fun drawScaffold() {
        val stack = mc.thePlayer?.inventory?.getStackInSlot(SilentHotbar.currentSlot)
        val shouldRender = stack?.item is ItemBlock
        val progressLenHeight = 3F; val imageLen = 23F; val offsetLen = 2F
        val blockAmount = InventoryUtils.blocksAmount()
        val pitch = Companion.DECIMAL_FORMAT.format(mc.thePlayer.rotationPitch)
        val countWidth = Fonts.fontSemibold40.getStringWidth("$blockAmount blocks")
        val percentProLen = progressLen/maxBlocks
        val fastCalc = offsetLen+imageLen+offsetLen
        val allLen = fastCalc+progressLen+offsetLen+4F+countWidth+offsetLen
        val startXScaffold = (width-allLen)/2

        AnimStartX = AnimationUtil.base(AnimStartX.toDouble(),startXScaffold.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)
        AnimEndX = AnimationUtil.base(AnimEndX.toDouble(),allLen+startXScaffold+1.0, animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)

        var progressLenReal2 = fastCalc+percentProLen*blockAmount
        if (blockAmount>maxBlocks) progressLenReal2 = fastCalc+progressLen

        ProgressBarAnimationWidth = AnimationUtil.base(ProgressBarAnimationWidth.toDouble(),progressLenReal2.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)

        applyBlur(AnimStartX-1F, start_y, AnimEndX - AnimStartX + 1F, 27F, scaledScreen, cornerRadius)
        drawRoundedRect(AnimStartX-1F,start_y, AnimEndX, start_y+27F, getBgColor().rgb, cornerRadius)
        ShowShadow(AnimStartX,start_y, AnimEndX-AnimStartX, 27F)

        drawRoundedRect(startXScaffold+fastCalc, start_y+13.5F-progressLenHeight/2,startXScaffold+fastCalc+progressLen,start_y+27F/2+progressLenHeight/2,
            Color(safeColor(ScaffoldTheme.red-170),safeColor(ScaffoldTheme.green-170),safeColor(ScaffoldTheme.blue-170),255).rgb,1.5F)
        drawRoundedRect(startXScaffold+fastCalc, start_y+13.5F-progressLenHeight/2,startXScaffold+ProgressBarAnimationWidth,start_y+27F/2+progressLenHeight/2,
            Color(ScaffoldTheme.red,ScaffoldTheme.green,ScaffoldTheme.blue,255).rgb,1.5F)
        Fonts.fontSemibold40.drawString("$blockAmount blocks",startXScaffold+fastCalc+progressLen+offsetLen+3F,start_y+5F,Color.WHITE.rgb)
        Fonts.fontSemibold35.drawString("$pitch a",startXScaffold+fastCalc+progressLen+offsetLen+3F,start_y+15F,Color(140,140,140,255).rgb)

        glPushMatrix()
        enableGUIStandardItemLighting()
        if (mc.currentScreen is GuiHudDesigner) glDisable(GL_DEPTH_TEST)
        if (shouldRender) mc.renderItem.renderItemAndEffectIntoGUI(stack, (startXScaffold+offsetLen+4).toInt(), (offsetLen+start_y+4).toInt())
        disableStandardItemLighting(); enableAlpha(); disableBlend(); disableLighting()
        if (mc.currentScreen is GuiHudDesigner) glEnable(GL_DEPTH_TEST)
        glPopMatrix()
    }

    private fun drawChest() {
        val chest = mc.currentScreen as? GuiChest ?: return
        val container = chest.inventorySlots ?: return
        val chestSlots = container.inventorySlots.filter { it.inventory != mc.thePlayer?.inventory }
        if (chestSlots.isEmpty()) return
        val columns = 9
        val rows = (chestSlots.size + 8) / 9
        val slotSize = 16; val padding = 8
        val containerWidth = columns * slotSize + padding * 2
        val containerHeight = rows * slotSize + padding * 2
        val targetStartX = (width - containerWidth) / 2
        val targetEndX = targetStartX + containerWidth

        AnimStartX = AnimationUtil.base(AnimStartX.toDouble(), targetStartX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)
        AnimEndX = AnimationUtil.base(AnimEndX.toDouble(), targetEndX.toDouble(), animationSpeed.toDouble()).toFloat().coerceAtLeast(0f)
        val clampedStartX = AnimStartX.coerceIn(5f, width - containerWidth - 5f)
        val clampedStartY = start_y.coerceIn(5f, height - containerHeight - 5f)

        applyBlur(clampedStartX, clampedStartY, containerWidth.toFloat(), containerHeight.toFloat(), scaledScreen, ChestRounded)
        drawRoundedRect(clampedStartX, clampedStartY, clampedStartX + containerWidth, clampedStartY + containerHeight, getBgColor().rgb, ChestRounded)
        ShowShadow(clampedStartX, clampedStartY, containerWidth.toFloat(), containerHeight.toFloat())
        drawChestItems(chestSlots, clampedStartX + padding, clampedStartY + padding, slotSize)
    }

    private fun drawChestItems(chestSlots: List<net.minecraft.inventory.Slot>, startX: Float, startY: Float, slotSize: Int) {
        enableGUIStandardItemLighting()
        try {
            chestSlots.forEach { slot ->
                val stack = slot.stack ?: return@forEach
                val col = slot.slotNumber % 9; val row = slot.slotNumber / 9
                val x = (startX + col * slotSize).toInt(); val y = (startY + row * slotSize).toInt()
                if (mc.currentScreen is GuiHudDesigner) glDisable(GL_DEPTH_TEST)
                mc.renderItem.renderItemAndEffectIntoGUI(stack, x, y)
                mc.renderItem.renderItemOverlays(mc.fontRendererObj, stack, x, y)
                if (mc.currentScreen is GuiHudDesigner) glEnable(GL_DEPTH_TEST)
            }
        } catch (_: Exception) {
        } finally {
            disableStandardItemLighting(); enableAlpha(); disableBlend(); disableLighting()
        }
    }

    // ========== 阴影绘制（支持 mask 裁切） ==========
    private fun ShowShadow(startX: Float, startY: Float, width: Float, height: Float) {
        if (ShadowCheck) {
            GlowUtils.drawGlow(
                startX, startY,
                width, height,
                (shadowStrengh * 13F).toInt(),
                Color(0, 0, 0, 120),
                cornerRadius = cornerRadius,
                onlyBorder = false,
                mask = ShadowMask
            )
        }
    }

    // ========== 通知系统核心 ==========
    private fun drawToggleButton(startX: Float, startY: Float, moduleState: Boolean, animationState: SwitchAnimationState) {
        val buttonHeight = 19F; val buttonWidth = 30F; val buttonToButtonDistance = 4F; val buttonRounded = buttonHeight / 2
        val smallButtonWidth = buttonHeight - buttonToButtonDistance * 2
        val toBigBorderLen = 6F; val buttonStartX = (NOTIFICATION_HEIGHT - buttonHeight) / 2
        animationState.updateState(moduleState)
        val animation = animationState.getOutput()
        if (!moduleState) drawRoundedBorderRect(startX + toBigBorderLen, startY + buttonStartX, startX + toBigBorderLen + buttonWidth, startY + buttonStartX + buttonHeight,0.1f, Color.DARK_GRAY.rgb, Color.DARK_GRAY.rgb, buttonRounded)
        val color = if (moduleState) Color(ButtonColor.red, ButtonColor.green, ButtonColor.blue, 255) else Color(108, 108, 108, 255)
        drawRoundedBorderRect(startX + toBigBorderLen + 1, startY + buttonStartX + 1, startX + toBigBorderLen + buttonWidth - 1, startY + buttonStartX + buttonHeight - 1, 0.1f, color.rgb, color.rgb, buttonRounded - 1)
        val smallButtonX = startX + toBigBorderLen + buttonToButtonDistance + (buttonWidth - buttonToButtonDistance * 2 - smallButtonWidth) * animation.toFloat()
        drawRoundedBorderRect(smallButtonX, startY + buttonStartX + buttonToButtonDistance, smallButtonX + smallButtonWidth, startY + buttonStartX + buttonToButtonDistance + smallButtonWidth,0.1f,
            if (moduleState) Color((ButtonColor.red+50).coerceAtMost(255),(ButtonColor.green+50).coerceAtMost(255),(ButtonColor.blue+50).coerceAtMost(255),255).rgb else Color.DARK_GRAY.rgb,
            if (moduleState) Color((ButtonColor.red+50).coerceAtMost(255),(ButtonColor.green+50).coerceAtMost(255),(ButtonColor.blue+50).coerceAtMost(255),255).rgb else Color.DARK_GRAY.rgb,
            smallButtonWidth / 2)
    }

    private fun drawToggleText(startX:Float, startY: Float, textBar: Pair<String,String>) {
        val textHeight = 9F; val title = textBar.first; val description = textBar.second; val buttonToTextLen = 5F
        val textStartX = startX+37F+buttonToTextLen
        Fonts.fontGoogleSans40.drawString(title,textStartX,startY+NOTIFICATION_HEIGHT/2-textHeight -1f,Color(255,255,255,255).rgb)
        Fonts.fontRegular35.drawString(description,textStartX,startY+NOTIFICATION_HEIGHT/2 +2F ,Color(255,255,255,255).rgb)
    }

    enum class Direction { FORWARDS, BACKWARDS }
    class EaseOutExpo(private val duration: Long, private val end: Double) {
        private var start = 0.0; private var startTime = 0L; private var direction = Direction.FORWARDS
        init { startTime = System.currentTimeMillis() }
        fun setDirection(direction: Direction) {
            if (this.direction != direction) { this.direction = direction; startTime = System.currentTimeMillis(); start = getOutput() }
        }
        fun getOutput(): Double {
            val elapsedTime = (System.currentTimeMillis() - startTime).coerceAtMost(duration)
            val progress = elapsedTime.toDouble() / duration
            return when (direction) {
                Direction.FORWARDS -> if (progress == 1.0) end else (-2.0.pow(-10 * progress) + 1) * end
                Direction.BACKWARDS -> if (progress == 1.0) 0.0 else (2.0.pow(-10 * progress) * end)
            }.coerceIn(0.0, end)
        }
    }
    class SwitchAnimationState {
        private val animation = EaseOutExpo(300, 1.0)
        fun updateState(state: Boolean) { animation.setDirection(if (state) Direction.FORWARDS else Direction.BACKWARDS) }
        fun getOutput(): Double = animation.getOutput()
    }

    private abstract class Notification(
        val id: String = UUID.randomUUID().toString(),
        var title: String,
        var message: String,
        var createTime: Long = System.currentTimeMillis(),
        val duration: Long = 3000L
    ) {
        var isMarkedForDelete = false
        abstract fun draw(x: Float, y: Float)
        open var enabled: Boolean = false
        fun getHeight(): Float = NOTIFICATION_HEIGHT
        fun update() { if (isFading()) isMarkedForDelete = true }
        fun isFading(): Boolean = System.currentTimeMillis() > createTime + duration || isMarkedForDelete
    }

    private class ToggleNotification(title: String, message: String, duration: Long, enabled: Boolean, val moduleName: String)
        : Notification(title = title, message = message, duration = duration) {
        private val animationState = SwitchAnimationState()
        init { this.enabled = enabled }
        override fun draw(x: Float, y: Float) {
            drawToggleButton(x, y, enabled, animationState)
            drawToggleText(x, y, Pair(title, message))
        }
    }

    private class InfoNotification(title: String, message: String, duration: Long)
        : Notification(title = title, message = message, duration = duration) {
        override fun draw(x: Float, y: Float) {
            drawInfoIcon(x, y)
            drawIconTextContent(x + 6f + 24f + 8f, y, this.title, this.message)
        }
    }
    private class SuccessNotification(title: String, message: String, duration: Long)
        : Notification(title = title, message = message, duration = duration) {
        override fun draw(x: Float, y: Float) {
            drawSuccessIcon(x, y)
            drawIconTextContent(x + 6f + 24f + 8f, y, this.title, this.message)
        }
    }
    private class ErrorNotification(title: String, message: String, duration: Long)
        : Notification(title = title, message = message, duration = duration) {
        override fun draw(x: Float, y: Float) {
            drawErrorIcon(x, y)
            drawIconTextContent(x + 6f + 24f + 8f, y, this.title, this.message)
        }
    }
    private class WarningNotification(title: String, message: String, duration: Long)
        : Notification(title = title, message = message, duration = duration) {
        override fun draw(x: Float, y: Float) {
            drawWarningIcon(x, y)
            drawIconTextContent(x + 6f + 24f + 8f, y, this.title, this.message)
        }
    }

    private fun drawInfoIcon(x: Float, y: Float) {
        val bgX = x + 6f
        val bgY = y + (NOTIFICATION_HEIGHT - 24f) / 2f
        drawRoundedRect(bgX, bgY, bgX + 24f, bgY + 24f, Color(0, 120, 255).rgb, 4f)
        val text = "i"
        val font = Fonts.fontSemibold35
        val textWidth = font.getStringWidth(text)
        val textHeight = font.FONT_HEIGHT
        val cx = bgX + 12f
        val cy = bgY + 12f
        font.drawString(text, cx - textWidth / 2f, cy - textHeight / 2f, Color.WHITE.rgb, false)
    }

    private fun drawWarningIcon(x: Float, y: Float) {
        val bgX = x + 6f
        val bgY = y + (NOTIFICATION_HEIGHT - 24f) / 2f
        drawRoundedRect(bgX, bgY, bgX + 24f, bgY + 24f, Color(100, 100, 100).rgb, 4f)
        val text = "!"
        val font = Fonts.fontSemibold35
        val textWidth = font.getStringWidth(text)
        val textHeight = font.FONT_HEIGHT
        val cx = bgX + 12f
        val cy = bgY + 12f
        font.drawString(text, cx - textWidth / 2f, cy - textHeight / 2f - 6f, Color.WHITE.rgb, false)
    }

    private fun drawSuccessIcon(x: Float, y: Float) {
        val bgX = x + 6f
        val bgY = y + (NOTIFICATION_HEIGHT - 24f) / 2f
        drawRoundedRect(bgX, bgY, bgX + 24f, bgY + 24f, Color(0, 200, 100).rgb, 4f)
        glPushMatrix()
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1f, 1f, 1f, 1f)
        glLineWidth(2.5f)
        val cx = bgX + 12f
        val cy = bgY + 12f
        glBegin(GL_LINE_STRIP)
        glVertex2f(cx - 5f, cy + 1f)
        glVertex2f(cx - 1.5f, cy + 5f)
        glVertex2f(cx + 5f, cy - 4f)
        glEnd()
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glPopMatrix()
    }

    private fun drawErrorIcon(x: Float, y: Float) {
        val bgX = x + 6f
        val bgY = y + (NOTIFICATION_HEIGHT - 24f) / 2f
        drawRoundedRect(bgX, bgY, bgX + 24f, bgY + 24f, Color(220, 50, 50).rgb, 4f)
        glPushMatrix()
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1f, 1f, 1f, 1f)
        glLineWidth(2.5f)
        val cx = bgX + 12f
        val cy = bgY + 12f
        glBegin(GL_LINES)
        glVertex2f(cx - 5f, cy - 5f)
        glVertex2f(cx + 5f, cy + 5f)
        glVertex2f(cx + 5f, cy - 5f)
        glVertex2f(cx - 5f, cy + 5f)
        glEnd()
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glPopMatrix()
    }

    private fun drawIconTextContent(textX: Float, y: Float, title: String, message: String) {
        Fonts.fontGoogleSans40.drawString(title, textX, y + 8f, Color.WHITE.rgb, true)
        Fonts.fontRegular35.drawString(message, textX, y + 22f, Color(200, 200, 200).rgb, false)
    }

    // 公开调用方法
    fun showToggleNotification(title: String, message: String, enabled: Boolean, duration: Long = 3000L, moduleName: String? = null) {
        if (moduleName != null) {
            val existing = notifications.find { it is ToggleNotification && it.moduleName == moduleName }
            if (existing != null) { existing.createTime = System.currentTimeMillis(); existing.title = title; existing.message = message; existing.enabled = enabled; return }
        }
        notifications.add(ToggleNotification(title, message, duration, enabled, moduleName ?: ""))
    }

    fun showInfo(title: String, message: String, duration: Long = 3000L) {
        notifications.add(InfoNotification(title, message, duration))
    }
    fun showSuccess(title: String, message: String, duration: Long = 3000L) {
        notifications.add(SuccessNotification(title, message, duration))
    }
    fun showError(title: String, message: String, duration: Long = 3000L) {
        notifications.add(ErrorNotification(title, message, duration))
    }
    fun showWarning(title: String, message: String, duration: Long = 3000L) {
        notifications.add(WarningNotification(title, message, duration))
    }

    private fun updateNotifications() { notifications.forEach { it.update() }; notifications.removeAll { it.isMarkedForDelete } }
    private fun calcNotification(): Pair<Float,Float> {
        if (notifications.isEmpty()) return 0F to 0F
        var maxW = 0f; var totalH = 0f
        notifications.forEach { totalH += it.getHeight(); maxW = max(maxW, Fonts.fontSemibold35.getStringWidth(it.message).toFloat()+45F) }
        return maxW to totalH
    }

    private fun drawNotificationsUI(sr: ScaledResolution, startY: Float) {
        val screenWidth = sr.scaledWidth.toFloat()
        val (maxW, totalH) = calcNotification()
        val startX_a = (screenWidth - maxW) / 2
        AnimModuleEndY = AnimationUtil.base(AnimModuleEndY.toDouble(),(startY + totalH).toDouble(),0.6).toFloat().coerceAtLeast(0f)
        AnimStartX = AnimationUtil.base(AnimStartX.toDouble(),startX_a.toDouble(),0.8).toFloat().coerceAtLeast(0f)
        AnimEndX = AnimationUtil.base(AnimEndX.toDouble(),3.0+startX_a+maxW.toDouble(),0.8).toFloat().coerceAtLeast(0f)

        applyBlur(AnimStartX, startY, AnimEndX - AnimStartX, AnimModuleEndY - startY, sr, cornerRadius)
        drawRoundedBorderRect(AnimStartX, startY, AnimEndX , AnimModuleEndY,0.2F, getBgColor().rgb, getBgColor().rgb, cornerRadius)
        ShowShadow(AnimStartX, startY, AnimEndX-AnimStartX, AnimModuleEndY-startY)

        var currentY = startY
        notifications.forEach { if (totalH > 0) { it.draw(startX_a, currentY); currentY += it.getHeight() } }
    }

    private fun safeColor(colorA: Int) = colorA.coerceIn(0, 255)
}