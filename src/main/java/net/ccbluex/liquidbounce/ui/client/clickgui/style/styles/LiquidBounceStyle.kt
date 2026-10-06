/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.style.styles

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.guiColor
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.scale
import net.ccbluex.liquidbounce.ui.client.clickgui.ClickGui.clamp
import net.ccbluex.liquidbounce.ui.client.clickgui.Panel
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.ButtonElement
import net.ccbluex.liquidbounce.ui.client.clickgui.elements.ModuleElement
import net.ccbluex.liquidbounce.ui.client.clickgui.style.Style
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts.fontSemibold35
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getBlockName
import net.ccbluex.liquidbounce.utils.extensions.component1
import net.ccbluex.liquidbounce.utils.extensions.component2
import net.ccbluex.liquidbounce.utils.extensions.lerpWith
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.blendColors
import net.ccbluex.liquidbounce.utils.render.ColorUtils.minecraftRed
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI
import net.ccbluex.liquidbounce.utils.GlowUtils
import net.ccbluex.liquidbounce.utils.render.BlurUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorder
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawBorderedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawTexture
import net.ccbluex.liquidbounce.utils.render.RenderUtils.updateTextureCache
import net.ccbluex.liquidbounce.utils.ui.EditableText
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.StringUtils
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt

@SideOnly(Side.CLIENT)
object LiquidBounceStyle : Style() {
    /** 面板 / 子面板整块的模糊与阴影；阴影按圆角裁切，只留边缘。 */
    /** IRC 面板关闭按钮的包围盒（本帧绘制时更新，供 ClickGui 做点击判定） */
    var ircCloseBounds: FloatArray? = null

    /**
     * IRC 面板 —— 纯前端 UI，不接任何后端（没有登录、没有收发）。
     * 圆角 / 阴影 / 模糊全部读 ClickGUI 自己的参数，和面板完全同源。
     *
     * @param progress 0..1 展开进度，缓出走 ClickGui
     * @return 无关（关闭按钮的命中在 ClickGui.mouseClicked 里用 [ircCloseBounds] 判定）
     */
    fun drawIrcPanel(progress: Float, mouseX: Int, mouseY: Int) {
        val (scaledWidth, scaledHeight) = ScaledResolution(mc)
        val screenWidth = scaledWidth / scale
        val screenHeight = scaledHeight / scale

        val fullLeft = 40F
        val fullTop = 18F
        val fullRight = screenWidth - 40F
        val fullBottom = screenHeight - 18F

        // 展开原点 = 左下角那个 IRC 按钮的中心。
        // 按钮画在 glScaled 之前（屏幕坐标 45, height-41，尺寸 32×32），
        // 这里要换到 glScaled 之后的局部坐标，所以除了 scale。
        val originX = 61F / scale
        val originY = (scaledHeight - 25F) / scale

        // 从按钮那一点向外"飞"到目标矩形：progress 为 0 时整个面板缩成一个点
        val left = originX + (fullLeft - originX) * progress
        val top = originY + (fullTop - originY) * progress
        val right = originX + (fullRight - originX) * progress
        val bottom = originY + (fullBottom - originY) * progress

        val radius = ClickGUI.lbPanelRadius

        // 和面板同一条管线：模糊 + 阴影 → 底色 → 边框
        drawPanelShader(left, top, right, bottom, radius, ClickGUI.lbPanelBlur)
        drawRoundedRect(left, top, right, bottom, ClickGUI.lbPanelColor.rgb, radius)
        drawRoundedBorder(left, top, right, bottom, 1F, ClickGUI.lbPanelBorderColor.rgb, radius)

        // 关闭按钮：右上角，hover 变红（Win10 的 #E81123）
        val closeSize = 11F
        val closeLeft = right - closeSize - 5F
        val closeTop = top + 5F
        ircCloseBounds = floatArrayOf(closeLeft, closeTop, closeLeft + closeSize, closeTop + closeSize)

        val closeHovered = mouseX >= closeLeft && mouseX <= closeLeft + closeSize &&
                mouseY >= closeTop && mouseY <= closeTop + closeSize

        drawRoundedRect(
            closeLeft, closeTop, closeLeft + closeSize, closeTop + closeSize,
            if (closeHovered) Color(232, 17, 35, 235).rgb else Color(255, 255, 255, 26).rgb, 3F
        )
        fontSemibold35.drawString(
            "x",
            (closeLeft + 3.5F).roundToInt(),
            (closeTop + 1.5F).roundToInt(),
            if (closeHovered) Int.MAX_VALUE else Color(200, 200, 200).rgb
        )

        // 标题
        fontSemibold35.drawString("IRC", (left + 7F).roundToInt(), (top + 5F).roundToInt(), guiColor)

        // 拉取新消息（内部自己限频 2 秒，不会每帧开连接）
        val irc = net.ccbluex.liquidbounce.chat.IrcClient
        irc.poll()

        // 面板整体跟着 progress 淡入，避免刚展开时文字先蹦出来
        val hintAlpha = (255 * progress).toInt().coerceIn(0, 255)
        val hintColor = (0x00FFFFFF) or (hintAlpha shl 24)

        if (!irc.isLoggedIn) {
            // 未登录：提示 + 登录按钮。点了打开主界面 auth，
            // 登录成功后 sendTcp 的钩子会把凭据同步给 IrcClient，面板自动切到已登录态。
            val hintY = (top + (bottom - top) / 2F).roundToInt()

            fontSemibold35.drawString(
                "Not logged in",
                ((left + right) / 2F - fontSemibold35.getStringWidth("Not logged in") / 2F).roundToInt(),
                hintY - 16,
                hintColor
            )

            val buttonWidth = 60F
            val buttonLeft = (left + right) / 2F - buttonWidth / 2F
            val buttonTop = hintY - 6F
            val buttonBottom = buttonTop + 15F

            // 未登录时这个包围盒代表「登录」按钮；已登录时代表输入框。
            // 两者不会同时存在，所以共用一个字段就够，ClickGui 里用 isLoggedIn 区分。
            irc.inputBounds = floatArrayOf(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonBottom)

            val hovered = mouseX >= buttonLeft && mouseX <= buttonLeft + buttonWidth &&
                    mouseY >= buttonTop && mouseY <= buttonBottom

            drawRoundedRect(
                buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonBottom,
                if (hovered) ClickGUI.lbPanelBorderColor.rgb else Color(255, 255, 255, 26).rgb, 4F
            )
            fontSemibold35.drawString(
                "登录",
                (buttonLeft + buttonWidth / 2F - fontSemibold35.getStringWidth("登录") / 2F).roundToInt(),
                (buttonTop + 3F).roundToInt(),
                hintColor
            )

            // 登录失败的原因（服务端返回的 message，例如 Invalid credentials）
            irc.lastError?.let { error ->
                fontSemibold35.drawString(
                    error,
                    ((left + right) / 2F - fontSemibold35.getStringWidth(error) / 2F).roundToInt(),
                    (buttonBottom + 5F).roundToInt(),
                    (0xFFFF6E6E.toInt() and 0x00FFFFFF) or (hintAlpha shl 24)
                )
            }
        } else {
            // 底部输入行：左边文本区（点一下聚焦），右端一个「发送」按钮
            val inputTop = bottom - 15F
            val inputBottom = bottom - 4F
            val sendWidth = 26F
            val sendLeft = right - 5F - sendWidth
            val inputRight = sendLeft - 3F

            // 文本区包围盒。发送按钮的位置由 irc.inputBounds[2] 推出，
            // 所以 ClickGui 那边不需要再多一个字段。
            irc.inputBounds = floatArrayOf(left + 5F, inputTop, inputRight, inputBottom)

            val focused = irc.inputFocused
            drawRoundedRect(
                left + 5F, inputTop, inputRight, inputBottom,
                if (focused) Color(255, 255, 255, 40).rgb else Color(255, 255, 255, 18).rgb, 4F
            )
            drawRoundedBorder(
                left + 5F, inputTop, inputRight, inputBottom, 1F,
                if (focused) ClickGUI.lbPanelBorderColor.rgb else Color(255, 255, 255, 30).rgb, 4F
            )

            val placeholder = irc.draft.isEmpty() && !focused
            fontSemibold35.drawString(
                if (placeholder) "点击这里输入..." else irc.draft,
                (left + 9F).roundToInt(),
                (inputTop + 2F).roundToInt(),
                if (placeholder) {
                    (0x9E9E9E and 0x00FFFFFF) or (hintAlpha shl 24)
                } else {
                    hintColor
                }
            )

            // 光标：聚焦时 500ms 闪一次
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                fontSemibold35.drawString(
                    "|",
                    (left + 9F + fontSemibold35.getStringWidth(irc.draft)).roundToInt(),
                    (inputTop + 2F).roundToInt(),
                    hintColor
                )
            }

            // 发送按钮：有草稿时才高亮，空的时候是灰的（点了也没内容可发）
            val canSend = irc.draft.isNotBlank()
            drawRoundedRect(
                sendLeft, inputTop, right - 5F, inputBottom,
                if (canSend) ClickGUI.lbPanelBorderColor.rgb else Color(255, 255, 255, 18).rgb, 4F
            )
            fontSemibold35.drawString(
                "发送",
                (sendLeft + sendWidth / 2F - fontSemibold35.getStringWidth("发送") / 2F).roundToInt(),
                (inputTop + 2F).roundToInt(),
                if (canSend) hintColor else (0x9E9E9E and 0x00FFFFFF) or (hintAlpha shl 24)
            )

            // 在线玩家列表：内部限频 5 秒 + 起后台线程，不阻塞渲染
            irc.pollOnline()

            // 右侧 1/4 留给在线玩家栏，聊天区缩到左边 3/4
            val sidebarWidth = (right - left) / 4F
            val chatRight = right - sidebarWidth

            // 单行气泡：名字 + 内容同行，宽度按实际文字长度自适应
            val lineHeight = 9F
            val bubblePadding = 4F
            val bubbleGap = 3F
            val bubbleLeft = left + 5F
            val bubbleRight = right - 5F
            val bubbleMaxWidth = bubbleRight - bubbleLeft
            val bubbleHeight = bubblePadding * 2F + lineHeight
            val visibleBottom = inputTop - 6F

            // 滚轮偏移：按「头部 + 气泡」估算
            val visibleHeight = visibleBottom - (top + 18F)
            val avatarSize = 11F
            val headerHeight = avatarSize + 2F
            val contentHeight = irc.messages.size * (headerHeight + bubbleHeight + bubbleGap * 2F)
            irc.scroll = irc.scroll.coerceIn(0F, (contentHeight - visibleHeight).coerceAtLeast(0F))

            // 从下往上铺：最新的贴底，越往上越旧
            val ordered = irc.messages.asReversed().toList()
            var cursor = visibleBottom + irc.scroll

            // 头像延后画：drawHead 会污染 GL 状态，之后画的圆角矩形会整片变透明
            val pendingAvatars = mutableListOf<Triple<String, Float, Float>>()

            for ((index, message) in ordered.withIndex()) {
                // 上面那条（更旧）是同一个人 → 不重复显示头像和名字，紧跟下面
                val above = ordered.getOrNull(index + 1)
                val showHeader = above == null || above.user != message.user

                val itemHeight = if (showHeader) headerHeight + bubbleHeight else bubbleHeight
                val itemTop = cursor - itemHeight
                if (itemTop < top + 18F) break

                // 底部裁剪：滚轮上翻后，最新的几条会被推出可视区，
                // 这里必须跳过（否则就漏到输入行下面去了）
                val itemBottom = cursor.coerceAtMost(visibleBottom)
                if (itemBottom - itemTop < 1F) {
                    cursor = itemTop - bubbleGap
                    continue
                }

                val isPrivate = message.target.isNotBlank()
                val body = if (isPrivate) "[私聊] ${message.text}" else message.text

                if (showHeader) {
                    // 头像先只记位置，等所有气泡画完再统一画（见循环外）
                    pendingAvatars += Triple(message.user, bubbleLeft + 1F, itemTop + 1F)

                    fontSemibold35.drawString(
                        message.user,
                        (bubbleLeft + avatarSize + 3F).roundToInt(),
                        (itemTop + 2F).roundToInt(),
                        (0xB0FFFFFF.toInt() and 0x00FFFFFF) or (hintAlpha shl 24)
                    )
                }

                val bubbleTop = itemBottom - bubbleHeight
                val textWidth = fontSemibold35.getStringWidth(body)
                val bubbleWidth = (textWidth + bubblePadding * 2F).coerceAtMost(bubbleMaxWidth)

                drawRoundedRect(
                    bubbleLeft, bubbleTop, bubbleLeft + bubbleWidth, itemBottom,
                    if (isPrivate) Color(60, 110, 160, 70).rgb else Color(255, 255, 255, 24).rgb, 4F
                )

                fontSemibold35.drawString(
                    fontSemibold35.trimStringToWidth(body, (bubbleWidth - bubblePadding * 2F).toInt()),
                    (bubbleLeft + bubblePadding).roundToInt(),
                    (bubbleTop + bubblePadding).roundToInt(),
                    if (isPrivate) {
                        (0xFF7EC8FF.toInt() and 0x00FFFFFF) or (hintAlpha shl 24)
                    } else {
                        hintColor
                    }
                )

                cursor = itemTop - bubbleGap
            }

            // 统一画头像。必须放在最后：drawHead 的 GL 状态影响不到上面的气泡和文字
            for ((name, avatarX, avatarY) in pendingAvatars) {
                runCatching {
                    net.ccbluex.liquidbounce.ui.client.hud.element.elements.Target().drawHead(
                        net.ccbluex.liquidbounce.chat.IrcAvatars.resolve("", name),
                        avatarX.toInt(),
                        avatarY.toInt(),
                        avatarSize.toInt(),
                        avatarSize.toInt(),
                        java.awt.Color.WHITE
                    )
                }
            }
        }
    }

    private fun drawPanelShader(left: Float, top: Float, right: Float, bottom: Float, radius: Float, blur: Boolean) {
        if (blur && BlurSettings.active) {
            // ClickGui 会 glScaled(scale) 之后再画 → 屏幕像素 = 局部坐标 * scale * scaleFactor
            val screenScale = ScaledResolution(mc).scaleFactor.toFloat() * scale
            BlurUtils.drawOffsetBlur(
                left * screenScale,
                top * screenScale,
                (right - left) * screenScale,
                (bottom - top) * screenScale,
                samples = BlurSettings.passes,
                strength = 0f,
                radius = radius * screenScale
            )
        }

        if (ClickGUI.lbPanelShadow) {
            GlowUtils.drawCompositeGlow(
                listOf(floatArrayOf(left, top, right, bottom)),
                (ClickGUI.lbShadowStrength * 13F).toInt(),
                ClickGUI.lbShadowColor,
                mask = ClickGUI.lbShadowMask,
                cornerRadius = radius
            )
        }
    }

    /**
     * 滑杆：灰色长条 + 激活段亮白(轻微白色光) + 白色圆头(glow 阴影)。
     * activeRanges 与 handles 都是 0..1 的归一化位置。
     */
    private fun drawSlider(
        minX: Int, maxX: Int, yPos: Int,
        activeRanges: List<ClosedFloatingPointRange<Float>>, handles: List<Float>
    ) {
        val trackX1 = minX + 4F
        val trackX2 = maxX - 4F
        if (trackX2 - trackX1 <= 0F) return

        val centerY = yPos + 18F
        val trackHeight = 3F
        val trackTop = centerY - trackHeight / 2F
        val trackBottom = centerY + trackHeight / 2F
        val span = trackX2 - trackX1

        // 灰色轨道
        drawRoundedRect(trackX1, trackTop, trackX2, trackBottom, Color(90, 90, 90, 200).rgb, trackHeight / 2F)

        // 激活范围：亮白 + 轻微白光
        for (range in activeRanges) {
            val start = trackX1 + span * range.start.coerceIn(0F, 1F)
            val end = trackX1 + span * range.endInclusive.coerceIn(0F, 1F)
            if (end - start <= 0F) continue

            GlowUtils.drawGlow(
                start - 2F, trackTop - 1.5F, (end - start) + 4F, trackHeight + 3F,
                6, Color(255, 255, 255, 70),
                cornerRadius = (trackHeight + 3F) / 2F
            )
            drawRoundedRect(start, trackTop, end, trackBottom, Color.WHITE.rgb, trackHeight / 2F)
        }

        // 白色圆头 + 两层 glow（和开关一致）：底层黑投影、上层白光
        val handleRadius = 4.5F
        for (t in handles) {
            val centerX = trackX1 + span * t.coerceIn(0F, 1F)
            GlowUtils.drawGlow(
                centerX - handleRadius, centerY - handleRadius,
                handleRadius * 2F, handleRadius * 2F,
                8, Color(0, 0, 0, 160),
                cornerRadius = handleRadius
            )
            GlowUtils.drawGlow(
                centerX - handleRadius, centerY - handleRadius,
                handleRadius * 2F, handleRadius * 2F,
                8, Color(255, 255, 255, 150),
                cornerRadius = handleRadius
            )
            drawRoundedRect(
                centerX - handleRadius, centerY - handleRadius,
                centerX + handleRadius, centerY + handleRadius,
                Color.WHITE.rgb, handleRadius
            )
        }
    }

    /**
     * ClickGUI 字体设置。这个成员会遮蔽上面 import 的 Fonts.fontSemibold35，
     * 所以本文件里所有 fontSemibold35 调用都会跟着 LB-Font 走。
     */
    /**
     * ClickGUI 字体设置。这个成员会遮蔽上面 import 的 Fonts.fontSemibold35，
     * 所以本文件里所有 fontSemibold35 调用都会跟着 LB-Font 走。
     *
     * 用安全转换：LB-Font 里如果选了原版字体（不是 GameFontRenderer），
     * 以前会 ClassCastException 崩掉，现在直接回退到 semibold35。
     */
    private val fontSemibold35: net.ccbluex.liquidbounce.ui.font.GameFontRenderer
        get() = ClickGUI.lbFont as? net.ccbluex.liquidbounce.ui.font.GameFontRenderer
            ?: net.ccbluex.liquidbounce.ui.font.Fonts.fontSemibold35

    /** 同一个 Value 上有多条独立动画通道，用 (对象, 通道名) 做键 */
    private data class AnimKey(val owner: Any, val channel: String)

    /** 控件动画状态（键是 Value 实例），值是 0..1 的进度 */
    private val animationStates = HashMap<Any, Float>()

    /**
     * 缓出：位移正比于剩余距离 —— 起步最快，越接近目标越慢。
     * 所有控件动画（开关滑动、选择器展开）都走这一个函数。
     */
    private fun easeOut(key: Any, target: Float, speed: Float = 1.2F): Float {
        val current = animationStates[key] ?: target
        val remaining = target - current

        if (abs(remaining) < 0.005F) {
            animationStates[key] = target
            return target
        }

        val factor = (speed * deltaTime / 100F).coerceIn(0.02F, 0.5F)
        val next = current + remaining * factor

        animationStates[key] = next
        return next
    }

    override fun drawPanel(mouseX: Int, mouseY: Int, panel: Panel) {
        val radius = ClickGUI.lbPanelRadius
        val left = panel.x.toFloat()
        val top = panel.y.toFloat()
        val right = (panel.x + panel.width).toFloat()
        val bottom = (panel.y + panel.height + panel.fade).toFloat()

        drawPanelShader(left, top, right, bottom, radius, ClickGUI.lbPanelBlur)

        drawRoundedRect(left, top, right, bottom, ClickGUI.lbPanelColor.rgb, radius)
        drawRoundedBorder(left, top, right, bottom, 1F, ClickGUI.lbPanelBorderColor.rgb, radius)

        val xPos = panel.x - (fontSemibold35.getStringWidth(StringUtils.stripControlCodes(panel.name)) - 100) / 2
        fontSemibold35.drawString(panel.name, xPos, panel.y + 6, Color.WHITE.rgb)

        if (false) { // 滚动条已停用：只保留滚轮滑动（原条件 panel.scrollbar && panel.fade > 0）
            drawRect(panel.x - 2, panel.y + 21, panel.x, panel.y + 16 + panel.fade, Color.DARK_GRAY.rgb)

            val visibleRange = panel.getVisibleRange()
            val minY =
                panel.y + 21 + panel.fade * if (visibleRange.first > 0) visibleRange.first / panel.elements.lastIndex.toFloat()
                else 0f
            val maxY =
                panel.y + 16 + panel.fade * if (visibleRange.last > 0) visibleRange.last / panel.elements.lastIndex.toFloat()
                else 0f

            drawRect(panel.x - 2, minY.roundToInt(), panel.x, maxY.roundToInt(), Color.GRAY.rgb)
        }
    }

    override fun drawHoverText(mouseX: Int, mouseY: Int, text: String) {
        val lines = text.lines()

        val width = lines.maxOfOrNull { fontSemibold35.getStringWidth(it) + 14 }
            ?: return // Makes no sense to render empty lines
        val height = fontSemibold35.fontHeight * lines.size + 3

        // Don't draw hover text beyond window boundaries
        val (scaledWidth, scaledHeight) = ScaledResolution(mc)
        val x = mouseX.clamp(0, (scaledWidth / scale - width).roundToInt())
        val y = mouseY.clamp(0, (scaledHeight / scale - height).roundToInt())

        drawBorderedRect(x + 9, y, x + width, y + height, 1, Color.GRAY.rgb, Int.MIN_VALUE)
        lines.forEachIndexed { index, text ->
            fontSemibold35.drawString(text, x + 12, y + 3 + (fontSemibold35.fontHeight) * index, Int.MAX_VALUE)
        }
    }

    override fun drawButtonElement(mouseX: Int, mouseY: Int, buttonElement: ButtonElement) {
        val xPos = buttonElement.x - (fontSemibold35.getStringWidth(buttonElement.displayName) - 100) / 2
        fontSemibold35.drawString(buttonElement.displayName, xPos, buttonElement.y + 6, buttonElement.color)
    }

    override fun drawModuleElementAndClick(
        mouseX: Int, mouseY: Int, moduleElement: ModuleElement, mouseButton: Int?
    ): Boolean {
        // 开启的模块：整行上色（不圆角）+ 同色的模糊
        if (moduleElement.module.state && ClickGUI.lbModuleColor.alpha > 0) {
            val rowLeft = moduleElement.x.toFloat()
            val rowTop = moduleElement.y.toFloat()
            val rowRight = (moduleElement.x + moduleElement.width).toFloat()
            // 行高 +1：元素间距本来就是 height + 1，补上这 1px 相邻模块的色块就能拼成一片
            val rowBottom = (moduleElement.y + moduleElement.height + 1).toFloat()

            if (ClickGUI.lbModuleBlur) {
                GlowUtils.drawGlow(
                    rowLeft, rowTop, rowRight - rowLeft, rowBottom - rowTop,
                    10, ClickGUI.lbModuleColor, mask = true
                )
            }

            drawRoundedRect(rowLeft, rowTop, rowRight, rowBottom, ClickGUI.lbModuleColor.rgb, 0F)
        }

        val xPos = moduleElement.x - (fontSemibold35.getStringWidth(moduleElement.displayName) - 100) / 2
        fontSemibold35.drawString(
            moduleElement.displayName, xPos, moduleElement.y + 6, if (moduleElement.module.state) {
                if (moduleElement.module.isActive) guiColor
                // Make inactive modules have alpha set to 100
                else (guiColor and 0x00FFFFFF) or (0x64 shl 24)
            } else Int.MAX_VALUE
        )

        val moduleValues = moduleElement.module.values.filter { it.shouldRender() }
        if (moduleValues.isNotEmpty()) {
            if (moduleElement.showSettings) {
                var yPos = moduleElement.y + 4

                val minX = moduleElement.x + moduleElement.width + 4
                val maxX = moduleElement.x + moduleElement.width + moduleElement.settingsWidth

                if (moduleElement.settingsWidth > 0 && moduleElement.settingsHeight > 0) {
                    val subLeft = minX.toFloat()
                    val subTop = yPos.toFloat()
                    val subRight = maxX.toFloat()
                    val subBottom = (yPos + moduleElement.settingsHeight).toFloat()
                    val subRadius = ClickGUI.lbPanelRadius

                    drawPanelShader(subLeft, subTop, subRight, subBottom, subRadius, ClickGUI.lbSubPanelBlur)
                    drawRoundedRect(subLeft, subTop, subRight, subBottom, ClickGUI.lbSubPanelColor.rgb, subRadius)
                    drawRoundedBorder(subLeft, subTop, subRight, subBottom, 1F, ClickGUI.lbPanelBorderColor.rgb, subRadius)
                }

                for (value in moduleValues) {
                    assumeNonVolatile = value.get() is Number

                    val suffix = value.suffix ?: ""

                    when (value) {
                        is BoolValue -> {
                            val text = value.name

                            // 胶囊 + 圆头
                            val capsuleWidth = 20F
                            val capsuleHeight = 8F
                            val circleRadius = 5F
                            val enabled = value.get()

                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(text) + capsuleWidth.toInt() + 14

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos..yPos + 13) {
                                value.toggle()
                                clickSound()
                                return true
                            }

                            fontSemibold35.drawString(
                                text, minX + 2, yPos + 3, if (enabled) guiColor else Int.MAX_VALUE
                            )

                            // 圆头滑动动画（缓出）
                            val slide = easeOut(value, if (enabled) 1F else 0F)

                            val capsuleX = maxX - capsuleWidth - 3F
                            val capsuleY = yPos + 3F
                            val circleY = capsuleY + capsuleHeight / 2F
                            val circleX = capsuleX + circleRadius + (capsuleWidth - circleRadius * 2F) * slide

                            // 影子分两层：底层恒定的黑色投影，上层是开启时的白光。
                            // 白色始终画在黑色之上，所以两个图层是叠出来的立体感。
                            // alpha 按 32 阶梯量化 —— drawGlow 会把阴影烘焙成贴图并缓存，
                            // 逐帧变化的 alpha 会不停新建贴图。
                            val whiteAlpha = (160 * slide).toInt() / 32 * 32

                            GlowUtils.drawGlow(
                                capsuleX, capsuleY, capsuleWidth, capsuleHeight,
                                8, Color(0, 0, 0, 160),
                                cornerRadius = capsuleHeight / 2F
                            )

                            if (whiteAlpha > 0) {
                                GlowUtils.drawGlow(
                                    capsuleX - 1F, capsuleY - 1F, capsuleWidth + 2F, capsuleHeight + 2F,
                                    10, Color(255, 255, 255, whiteAlpha),
                                    cornerRadius = capsuleHeight / 2F
                                )
                            }

                            // 胶囊：灰 → 白
                            val capsuleShade = (90 + (255 - 90) * slide).toInt()
                            drawRoundedRect(
                                capsuleX, capsuleY, capsuleX + capsuleWidth, capsuleY + capsuleHeight,
                                Color(capsuleShade, capsuleShade, capsuleShade, 255).rgb, capsuleHeight / 2F
                            )

                            // 白色圆头：直径大于胶囊高度
                            drawRoundedRect(
                                circleX - circleRadius, circleY - circleRadius,
                                circleX + circleRadius, circleY + circleRadius,
                                Color.WHITE.rgb, circleRadius
                            )

                            yPos += 14
                        }

                        is ListValue -> {
                            val text = value.name

                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(text) + 16

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos + 2..yPos + 14) {
                                value.openList = !value.openList
                                clickSound()
                                return true
                            }

                            fontSemibold35.drawString("§c$text", minX + 2, yPos + 4, Color.WHITE.rgb)
                            fontSemibold35.drawString(
                                if (value.openList) "-" else "+",
                                maxX - if (value.openList) 5 else 6,
                                yPos + 4,
                                Color.WHITE.rgb
                            )

                            yPos += 12

                            // 展开 / 收起动画（缓出）
                            val expand = easeOut(AnimKey(value, "expand"), if (value.openList) 1F else 0F)

                            // 选择动画：高亮在行与行之间滑过去，而不是瞬移
                            val selectedIndex = value.values.indexOf(value.get()).coerceAtLeast(0)
                            val indexAnim = easeOut(AnimKey(value, "index"), selectedIndex.toFloat())

                            val entryHeight = 12 * expand
                            val listTop = (yPos - 1).toFloat()

                            // 文字在行内垂直居中（之前固定写 yPos + 4，在矩形里是偏下的）
                            val textOffset = ((entryHeight - fontSemibold35.height) / 2F).roundToInt()

                            if (expand > 0.01F) {
                                // 所有选项共用一整块大圆角矩形，高度跟着动画走；
                                // 左右各缩进 1px，免得被面板边框压住一小条
                                drawRoundedRect(
                                    minX + 1F, listTop, maxX - 1F, listTop + value.values.size * entryHeight,
                                    Color(255, 255, 255, 18).rgb, 3F
                                )

                                // 当前选择的高亮：位置跟着 indexAnim 滑动
                                if (expand > 0.3F) {
                                    val highlightTop = listTop + indexAnim * entryHeight

                                    // 展开动画跑完之前不画模糊阴影：
                                    // drawCompositeGlow 按 (画布尺寸, 颜色) 缓存烘焙贴图，而展开过程中
                                    // 高度和 alpha 每帧都在变 → 每帧重建一次 Java2D 高斯模糊，这就是卡顿来源。
                                    // 定格后再画，尺寸和颜色都不变，缓存键稳定，只烘焙一次。
                                    if (expand > 0.99F) {
                                        GlowUtils.drawCompositeGlow(
                                            listOf(
                                                floatArrayOf(
                                                    minX + 1F, highlightTop, maxX - 1F, highlightTop + entryHeight
                                                )
                                            ),
                                            10, Color(0, 0, 0, 120),
                                            mask = true, cornerRadius = 3F
                                        )
                                    }

                                    drawRoundedRect(
                                        minX + 1F, highlightTop, maxX - 1F, highlightTop + entryHeight,
                                        Color(255, 255, 255, (45 * expand).toInt()).rgb, 3F
                                    )
                                }
                            }

                            for (valueOfList in value.values) {
                                moduleElement.settingsWidth = fontSemibold35.getStringWidth(valueOfList) + 16

                                if (value.openList || expand > 0.01F) {
                                    if (value.openList && mouseButton == 0 && mouseX in minX..maxX &&
                                        mouseY in yPos + 2..yPos + 14
                                    ) {
                                        value.set(valueOfList)
                                        clickSound()
                                        return true
                                    }

                                    // 文字 alpha 跟着展开进度渐变消失，而不是到一半突然没
                                    if (expand > 0.05F) {
                                        val textColor = if (value.get() == valueOfList) guiColor else Int.MAX_VALUE
                                        val fadedAlpha = (expand.coerceIn(0F, 1F) * 255).toInt()

                                        fontSemibold35.drawString(
                                            valueOfList,
                                            minX + 2,
                                            yPos + textOffset + 1,
                                            (textColor and 0x00FFFFFF) or (fadedAlpha shl 24)
                                        )
                                    }

                                    yPos += entryHeight.roundToInt()
                                }
                            }
                        }

                        is FloatValue -> {
                            val text = value.name + "§f: §c" + round(value.get()) + " §8${suffix}§c"
                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(text) + 8

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos + 15..yPos + 21 || sliderValueHeld == value) {
                                val percentage = (mouseX - minX - 4) / (maxX - minX - 8).toFloat()
                                value.setAndSaveValueOnButtonRelease(
                                    round(value.minimum + (value.maximum - value.minimum) * percentage).coerceIn(
                                        value.range
                                    )
                                )

                                // Keep changing this slider until mouse is unpressed.
                                sliderValueHeld = value

                                // Stop rendering and interacting only when this event was triggered by a mouse click.
                                if (mouseButton == 0) return true
                            }

                            val displayValue = value.get().coerceIn(value.range)
                            val sliderSpan = value.maximum - value.minimum
                            val ratio = if (sliderSpan == 0F) 0F else (displayValue - value.minimum) / sliderSpan
                            drawSlider(minX, maxX, yPos, listOf(0F..ratio), listOf(ratio))

                            fontSemibold35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 22
                        }

                        is BlockValue -> {
                            val text =
                                value.name + "§f: §c" + getBlockName(value.get()) + " (" + value.get() + ")" + " §8$suffix"

                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(text) + 8

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos + 15..yPos + 21 || sliderValueHeld == value) {
                                val percentage = (mouseX - minX - 4) / (maxX - minX - 8).toFloat()
                                value.setAndSaveValueOnButtonRelease(
                                    (value.minimum + (value.maximum - value.minimum) * percentage).roundToInt()
                                        .coerceIn(value.range)
                                )

                                // Keep changing this slider until mouse is unpressed.
                                sliderValueHeld = value

                                // Stop rendering and interacting only when this event was triggered by a mouse click.
                                if (mouseButton == 0) return true
                            }

                            val displayValue = value.get().coerceIn(value.range)
                            val sliderSpan = (value.maximum - value.minimum).toFloat()
                            val ratio = if (sliderSpan == 0F) 0F else (displayValue - value.minimum).toFloat() / sliderSpan
                            drawSlider(minX, maxX, yPos, listOf(0F..ratio), listOf(ratio))

                            fontSemibold35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 22
                        }

                        is IntValue -> {
                            val text = value.name + "§f: §c" + value.get() + " §8$suffix"

                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(text) + 8

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos + 15..yPos + 21 || sliderValueHeld == value) {
                                val percentage = (mouseX - minX - 4) / (maxX - minX - 8).toFloat()
                                value.setAndSaveValueOnButtonRelease(
                                    (value.minimum + (value.maximum - value.minimum) * percentage).roundToInt()
                                        .coerceIn(value.range)
                                )

                                // Keep changing this slider until mouse is unpressed.
                                sliderValueHeld = value

                                // Stop rendering and interacting only when this event was triggered by a mouse click.
                                if (mouseButton == 0) return true
                            }

                            val displayValue = value.get().coerceIn(value.range)
                            val sliderSpan = (value.maximum - value.minimum).toFloat()
                            val ratio = if (sliderSpan == 0F) 0F else (displayValue - value.minimum).toFloat() / sliderSpan
                            drawSlider(minX, maxX, yPos, listOf(0F..ratio), listOf(ratio))

                            fontSemibold35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 22
                        }

                        is IntRangeValue -> {
                            val slider1 = value.get().first
                            val slider2 = value.get().last

                            val text = "${value.name}§f: §c$slider1 §f- §c$slider2 §8${suffix}"
                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(text) + 8

                            val startX = minX + 4
                            val startY = yPos + 14
                            val width = moduleElement.settingsWidth - 12

                            val endX = startX + width

                            val currSlider = value.lastChosenSlider

                            if (mouseButton == 0 && mouseX in startX..endX && mouseY in startY - 2..startY + 7 || sliderValueHeld == value) {
                                val leftSliderPos =
                                    startX + (slider1 - value.minimum).toFloat() / (value.maximum - value.minimum) * (endX - startX)
                                val rightSliderPos =
                                    startX + (slider2 - value.minimum).toFloat() / (value.maximum - value.minimum) * (endX - startX)

                                val distToSlider1 = mouseX - leftSliderPos
                                val distToSlider2 = mouseX - rightSliderPos

                                val closerToLeft = abs(distToSlider1) < abs(distToSlider2)

                                val isOnLeftSlider =
                                    (mouseX.toFloat() in startX.toFloat()..leftSliderPos || closerToLeft) && rightSliderPos > startX
                                val isOnRightSlider =
                                    (mouseX.toFloat() in rightSliderPos..endX.toFloat() || !closerToLeft) && leftSliderPos < endX

                                val percentage = (mouseX.toFloat() - startX) / (endX - startX)

                                if (isOnLeftSlider && currSlider == null || currSlider == RangeSlider.LEFT) {
                                    withDelayedSave {
                                        value.setFirst(
                                            value.lerpWith(percentage).coerceIn(value.minimum, slider2), false
                                        )
                                    }
                                }

                                if (isOnRightSlider && currSlider == null || currSlider == RangeSlider.RIGHT) {
                                    withDelayedSave {
                                        value.setLast(
                                            value.lerpWith(percentage).coerceIn(slider1, value.maximum), false
                                        )
                                    }
                                }

                                // Keep changing this slider until mouse is unpressed.
                                sliderValueHeld = value

                                // Stop rendering and interacting only when this event was triggered by a mouse click.
                                if (mouseButton == 0) {
                                    value.lastChosenSlider = when {
                                        isOnLeftSlider -> RangeSlider.LEFT
                                        isOnRightSlider -> RangeSlider.RIGHT
                                        else -> null
                                    }
                                    return true
                                }
                            }

                            val rangeSpan = (value.maximum - value.minimum).toFloat()
                            val ratio1 = if (rangeSpan == 0F) 0F else (value.get().first - value.minimum).toFloat() / rangeSpan
                            val ratio2 = if (rangeSpan == 0F) 0F else (value.get().last - value.minimum).toFloat() / rangeSpan
                            drawSlider(minX, maxX, yPos, listOf(ratio1..ratio2), listOf(ratio1, ratio2))

                            fontSemibold35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 22
                        }

                        is FloatRangeValue -> {
                            val slider1 = value.get().start
                            val slider2 = value.get().endInclusive

                            val text = "${value.name}§f: §c${round(slider1)} §f- §c${round(slider2)} §8${suffix}"
                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(text) + 8

                            val startX = minX + 4
                            val startY = yPos + 14
                            val width = moduleElement.settingsWidth - 12

                            val endX = startX + width

                            val currSlider = value.lastChosenSlider

                            if (mouseButton == 0 && mouseX in startX..endX && mouseY in startY - 2..startY + 7 || sliderValueHeld == value) {
                                val leftSliderPos =
                                    startX + (slider1 - value.minimum) / (value.maximum - value.minimum) * (endX - startX)
                                val rightSliderPos =
                                    startX + (slider2 - value.minimum) / (value.maximum - value.minimum) * (endX - startX)

                                val distToSlider1 = mouseX - leftSliderPos
                                val distToSlider2 = mouseX - rightSliderPos

                                val closerToLeft = abs(distToSlider1) < abs(distToSlider2)

                                val isOnLeftSlider =
                                    (mouseX.toFloat() in startX.toFloat()..leftSliderPos || closerToLeft) && rightSliderPos > startX
                                val isOnRightSlider =
                                    (mouseX.toFloat() in rightSliderPos..endX.toFloat() || !closerToLeft) && leftSliderPos < endX

                                val percentage = (mouseX.toFloat() - startX) / (endX - startX)

                                if (isOnLeftSlider && currSlider == null || currSlider == RangeSlider.LEFT) {
                                    withDelayedSave {
                                        value.setFirst(
                                            value.lerpWith(percentage).coerceIn(value.minimum, slider2), false
                                        )
                                    }
                                }

                                if (isOnRightSlider && currSlider == null || currSlider == RangeSlider.RIGHT) {
                                    withDelayedSave {
                                        value.setLast(
                                            value.lerpWith(percentage).coerceIn(slider1, value.maximum), false
                                        )
                                    }
                                }

                                // Keep changing this slider until mouse is unpressed.
                                sliderValueHeld = value

                                // Stop rendering and interacting only when this event was triggered by a mouse click.
                                if (mouseButton == 0) {
                                    value.lastChosenSlider = when {
                                        isOnLeftSlider -> RangeSlider.LEFT
                                        isOnRightSlider -> RangeSlider.RIGHT
                                        else -> null
                                    }
                                    return true
                                }
                            }

                            val rangeSpan = value.maximum - value.minimum
                            val ratio1 = if (rangeSpan == 0F) 0F else (value.get().start - value.minimum) / rangeSpan
                            val ratio2 = if (rangeSpan == 0F) 0F else (value.get().endInclusive - value.minimum) / rangeSpan
                            drawSlider(minX, maxX, yPos, listOf(ratio1..ratio2), listOf(ratio1, ratio2))

                            fontSemibold35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 22
                        }

                        is FontValue -> {
                            val displayString = value.displayName
                            moduleElement.settingsWidth = fontSemibold35.getStringWidth(displayString) + 8

                            if (mouseButton != null && mouseX in minX..maxX && mouseY in yPos + 4..yPos + 12) {
                                // Cycle to next font when left-clicked, previous when right-clicked.
                                if (mouseButton == 0) value.next()
                                else value.previous()
                                clickSound()
                                return true
                            }

                            fontSemibold35.drawString(displayString, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 11
                        }

                        is ColorValue -> {
                            val currentColor = value.selectedColor()

                            val spacing = 12

                            val startX = moduleElement.x + moduleElement.width + 4
                            val startY = yPos - 1

                            // Color preview
                            val colorPreviewSize = 9
                            val colorPreviewX2 = maxX - colorPreviewSize
                            val colorPreviewX1 = colorPreviewX2 - colorPreviewSize
                            val colorPreviewY1 = startY + 2
                            val colorPreviewY2 = colorPreviewY1 + colorPreviewSize

                            val rainbowPreviewX2 = colorPreviewX1 - colorPreviewSize
                            val rainbowPreviewX1 = rainbowPreviewX2 - colorPreviewSize

                            // Text
                            val textX = startX + 2F
                            val textY = startY + 4F

                            // Sliders
                            val hueSliderWidth = 7
                            val hueSliderHeight = 50
                            val colorPickerWidth = 75
                            val colorPickerHeight = 50

                            val spacingBetweenSliders = 5

                            val rgbaOptionHeight = if (value.showOptions) fontSemibold35.height * 4 else 0

                            val colorPickerStartX = textX.toInt()
                            val colorPickerEndX = colorPickerStartX + colorPickerWidth
                            val colorPickerStartY = rgbaOptionHeight + colorPreviewY2 + spacing / 3
                            val colorPickerEndY = colorPickerStartY + colorPickerHeight

                            val hueSliderStartY = colorPickerStartY
                            val hueSliderEndY = colorPickerStartY + hueSliderHeight

                            val hueSliderX = colorPickerEndX + spacingBetweenSliders

                            val opacityStartX = hueSliderX + hueSliderWidth + spacingBetweenSliders
                            val opacityEndX = opacityStartX + hueSliderWidth

                            val rainbow = value.rainbow

                            if (mouseButton in arrayOf(0, 1)) {
                                val isColorPreview =
                                    mouseX in colorPreviewX1..colorPreviewX2 && mouseY in colorPreviewY1..colorPreviewY2
                                val isRainbowPreview =
                                    mouseX in rainbowPreviewX1..rainbowPreviewX2 && mouseY in colorPreviewY1..colorPreviewY2

                                when {
                                    isColorPreview -> {
                                        if (mouseButton == 0 && rainbow) value.rainbow = false
                                        if (mouseButton == 1) value.showPicker = !value.showPicker
                                        clickSound()
                                        return true
                                    }

                                    isRainbowPreview -> {
                                        if (mouseButton == 0) value.rainbow = true
                                        if (mouseButton == 1) value.showPicker = !value.showPicker
                                        clickSound()
                                        return true
                                    }
                                }
                            }

                            val startText = "${value.name}: "
                            val valueText = "#%08X".format(currentColor.rgb)
                            val combinedText = startText + valueText

                            val combinedWidth = opacityEndX - colorPickerStartX
                            val optimalWidth = maxOf(fontSemibold35.getStringWidth(combinedText), combinedWidth)
                            moduleElement.settingsWidth = optimalWidth + spacing * 4

                            val valueX = startX + fontSemibold35.getStringWidth(startText)
                            val valueWidth = fontSemibold35.getStringWidth(valueText)

                            if (mouseButton == 1 && mouseX in valueX..valueX + valueWidth && mouseY.toFloat() in textY - 2..textY + fontSemibold35.height - 3F) {
                                value.showOptions = !value.showOptions

                                if (!value.showOptions) {
                                    resetChosenText(value)
                                }
                            }

                            val widestLabel = rgbaLabels.maxOf { fontSemibold35.getStringWidth(it) }

                            var highlightCursor = {}

                            chosenText?.let {
                                if (it.value != value) {
                                    return@let
                                }

                                val startValueX = textX + widestLabel + 3
                                val cursorY = textY + value.rgbaIndex * fontSemibold35.height + 10

                                if (it.selectionActive()) {
                                    val start =
                                        startValueX + fontSemibold35.getStringWidth(it.string.take(it.selectionStart!!))
                                    val end =
                                        startValueX + fontSemibold35.getStringWidth(it.string.take(it.selectionEnd!!))
                                    drawRect(
                                        start,
                                        cursorY - 3f,
                                        end,
                                        cursorY + fontSemibold35.fontHeight - 2,
                                        Color(7, 152, 252).rgb
                                    )
                                }

                                highlightCursor = {
                                    val cursorX = startValueX + fontSemibold35.getStringWidth(it.cursorString)
                                    drawRect(
                                        cursorX,
                                        cursorY - 3F,
                                        cursorX + 1F,
                                        cursorY + fontSemibold35.fontHeight - 2,
                                        Color.WHITE.rgb
                                    )
                                }
                            }

                            if (value.showOptions) {
                                val mainColor = value.get()
                                val rgbaValues = listOf(mainColor.red, mainColor.green, mainColor.blue, mainColor.alpha)
                                val rgbaYStart = textY + 10

                                var noClickAmount = 0

                                val maxWidth = fontSemibold35.getStringWidth("255")

                                rgbaLabels.forEachIndexed { index, label ->
                                    val rgbaValueText = "${rgbaValues[index]}"
                                    val colorX = textX + widestLabel + 4
                                    val yPosition = rgbaYStart + index * fontSemibold35.height

                                    val isEmpty =
                                        chosenText?.value == value && value.rgbaIndex == index && chosenText?.string.isNullOrEmpty()

                                    val extraSpacing = if (isEmpty) maxWidth + 4 else 0
                                    val finalX = colorX + extraSpacing

                                    val defaultColor = if (isEmpty) Color.LIGHT_GRAY else minecraftRed
                                    val defaultText = if (isEmpty) "($rgbaValueText)" else rgbaValueText

                                    fontSemibold35.drawString(label, textX, yPosition, Color.WHITE.rgb)
                                    fontSemibold35.drawString(defaultText, finalX, yPosition, defaultColor.rgb)

                                    if (mouseButton == 0) {
                                        if (mouseX.toFloat() in finalX..finalX + maxWidth && mouseY.toFloat() in yPosition - 2..yPosition + 6) {
                                            chosenText = EditableText.forRGBA(value, index)
                                        } else {
                                            noClickAmount++
                                        }
                                    }
                                }

                                // Were none of these labels clicked on?
                                if (noClickAmount == rgbaLabels.size) {
                                    resetChosenText(value)
                                }
                            }

                            fontSemibold35.drawString(combinedText, textX, textY, Color.WHITE.rgb)

                            highlightCursor()

                            val normalBorderColor = if (rainbow) 0 else Color.BLUE.rgb
                            val rainbowBorderColor = if (rainbow) Color.BLUE.rgb else 0

                            val hue = if (rainbow) {
                                Color.RGBtoHSB(currentColor.red, currentColor.green, currentColor.blue, null)[0]
                            } else {
                                value.hueSliderY
                            }

                            if (value.showPicker) {
                                // Color Picker
                                value.updateTextureCache(
                                    id = 0,
                                    hue = hue,
                                    width = colorPickerWidth,
                                    height = colorPickerHeight,
                                    generateImage = { image, _ ->
                                        for (px in 0 until colorPickerWidth) {
                                            for (py in 0 until colorPickerHeight) {
                                                val localS = px / colorPickerWidth.toFloat()
                                                val localB = 1.0f - (py / colorPickerHeight.toFloat())
                                                val rgb = Color.HSBtoRGB(hue, localS, localB)
                                                image.setRGB(px, py, rgb)
                                            }
                                        }
                                    },
                                    drawAt = { id ->
                                        drawTexture(
                                            id,
                                            colorPickerStartX,
                                            colorPickerStartY,
                                            colorPickerWidth,
                                            colorPickerHeight
                                        )
                                    })

                                val markerX = (colorPickerStartX..colorPickerEndX).lerpWith(value.colorPickerPos.x)
                                val markerY = (colorPickerStartY..colorPickerEndY).lerpWith(value.colorPickerPos.y)

                                if (!rainbow) {
                                    RenderUtils.drawBorder(
                                        markerX - 2f, markerY - 2f, markerX + 3f, markerY + 3f, 1.5f, Color.WHITE.rgb
                                    )
                                }

                                // Hue slider
                                value.updateTextureCache(
                                    id = 1,
                                    hue = hue,
                                    width = hueSliderWidth,
                                    height = hueSliderHeight,
                                    generateImage = { image, _ ->
                                        for (y in 0 until hueSliderHeight) {
                                            for (x in 0 until hueSliderWidth) {
                                                val localHue = y / hueSliderHeight.toFloat()
                                                val rgb = Color.HSBtoRGB(localHue, 1.0f, 1.0f)
                                                image.setRGB(x, y, rgb)
                                            }
                                        }
                                    },
                                    drawAt = { id ->
                                        drawTexture(
                                            id, hueSliderX, colorPickerStartY, hueSliderWidth, hueSliderHeight
                                        )
                                    })

                                // Opacity slider
                                value.updateTextureCache(
                                    id = 2,
                                    hue = currentColor.rgb.toFloat(),
                                    width = hueSliderWidth,
                                    height = hueSliderHeight,
                                    generateImage = { image, _ ->
                                        val gridSize = 1

                                        for (y in 0 until hueSliderHeight) {
                                            for (x in 0 until hueSliderWidth) {
                                                val gridX = x / gridSize
                                                val gridY = y / gridSize

                                                val checkerboardColor = if ((gridY + gridX) % 2 == 0) {
                                                    Color.WHITE.rgb
                                                } else {
                                                    Color.BLACK.rgb
                                                }

                                                val alpha =
                                                    ((1 - y.toFloat() / hueSliderHeight.toFloat()) * 255).roundToInt()

                                                val finalColor = blendColors(
                                                    Color(checkerboardColor), currentColor.withAlpha(alpha)
                                                )

                                                image.setRGB(x, y, finalColor.rgb)
                                            }
                                        }
                                    },
                                    drawAt = { id ->
                                        drawTexture(
                                            id, opacityStartX, colorPickerStartY, hueSliderWidth, hueSliderHeight
                                        )
                                    })

                                val opacityMarkerY = (hueSliderStartY..hueSliderEndY).lerpWith(1 - value.opacitySliderY)
                                val hueMarkerY = (hueSliderStartY..hueSliderEndY).lerpWith(hue)

                                RenderUtils.drawBorder(
                                    hueSliderX.toFloat() - 1,
                                    hueMarkerY - 1f,
                                    hueSliderX + hueSliderWidth + 1f,
                                    hueMarkerY + 1f,
                                    1.5f,
                                    Color.WHITE.rgb,
                                )

                                RenderUtils.drawBorder(
                                    opacityStartX.toFloat() - 1,
                                    opacityMarkerY - 1f,
                                    opacityEndX + 1f,
                                    opacityMarkerY + 1f,
                                    1.5f,
                                    Color.WHITE.rgb,
                                )

                                val inColorPicker =
                                    mouseX in colorPickerStartX until colorPickerEndX && mouseY in colorPickerStartY until colorPickerEndY && !rainbow
                                val inHueSlider =
                                    mouseX in hueSliderX - 1..hueSliderX + hueSliderWidth + 1 && mouseY in hueSliderStartY until hueSliderEndY && !rainbow
                                val inOpacitySlider =
                                    mouseX in opacityStartX - 1..opacityEndX + 1 && mouseY in hueSliderStartY until hueSliderEndY

                                // Must be outside the if statements below since we check for mouse button state.
                                // If it's inside the statement, it will not update the mouse button state on time.
                                val sliderType = value.lastChosenSlider

                                if (mouseButton == 0 && (inColorPicker || inHueSlider || inOpacitySlider) || sliderValueHeld == value && value.lastChosenSlider != null) {
                                    if (inColorPicker && sliderType == null || sliderType == ColorValue.SliderType.COLOR) {
                                        val newS = ((mouseX - colorPickerStartX) / colorPickerWidth.toFloat()).coerceIn(
                                            0f, 1f
                                        )
                                        val newB =
                                            (1.0f - (mouseY - colorPickerStartY) / colorPickerHeight.toFloat()).coerceIn(
                                                0f, 1f
                                            )
                                        value.colorPickerPos.x = newS
                                        value.colorPickerPos.y = 1 - newB
                                    }

                                    var finalColor = Color(
                                        Color.HSBtoRGB(
                                            value.hueSliderY, value.colorPickerPos.x, 1 - value.colorPickerPos.y
                                        )
                                    )

                                    if (inHueSlider && sliderType == null || sliderType == ColorValue.SliderType.HUE) {
                                        value.hueSliderY =
                                            ((mouseY - hueSliderStartY) / hueSliderHeight.toFloat()).coerceIn(
                                                0f, 1f
                                            )

                                        finalColor = Color(
                                            Color.HSBtoRGB(
                                                value.hueSliderY, value.colorPickerPos.x, 1 - value.colorPickerPos.y
                                            )
                                        )
                                    }

                                    if (inOpacitySlider && sliderType == null || sliderType == ColorValue.SliderType.OPACITY) {
                                        value.opacitySliderY =
                                            1 - ((mouseY - hueSliderStartY) / hueSliderHeight.toFloat()).coerceIn(
                                                0f, 1f
                                            )
                                    }

                                    finalColor = finalColor.withAlpha((value.opacitySliderY * 255).roundToInt())

                                    sliderValueHeld = value
                                    value.setAndSaveValueOnButtonRelease(finalColor)

                                    if (mouseButton == 0) {
                                        value.lastChosenSlider = when {
                                            inColorPicker && !rainbow -> ColorValue.SliderType.COLOR
                                            inHueSlider && !rainbow -> ColorValue.SliderType.HUE
                                            inOpacitySlider -> ColorValue.SliderType.OPACITY
                                            else -> null
                                        }
                                        return true
                                    }
                                }
                                yPos += colorPickerHeight + colorPreviewSize - 6
                            }

                            drawBorderedRect(
                                colorPreviewX1,
                                colorPreviewY1,
                                colorPreviewX2,
                                colorPreviewY2,
                                1.5f,
                                normalBorderColor,
                                value.get().rgb
                            )

                            drawBorderedRect(
                                rainbowPreviewX1,
                                colorPreviewY1,
                                rainbowPreviewX2,
                                colorPreviewY2,
                                1.5f,
                                rainbowBorderColor,
                                ColorUtils.rainbow(alpha = value.opacitySliderY).rgb
                            )

                            yPos += spacing + rgbaOptionHeight
                        }

                        else -> {
                            val startText = value.name + "§f: "
                            var valueText = "${value.get()}"

                            val combinedWidth = fontSemibold35.getStringWidth(startText + valueText)

                            moduleElement.settingsWidth = combinedWidth + 8

                            val textY = yPos + 4
                            val startX = minX + 2
                            var textX = startX + fontSemibold35.getStringWidth(startText)

                            if (mouseButton == 0) {
                                chosenText =
                                    if (mouseX in textX..maxX && mouseY in textY - 2..textY + 6 && value is TextValue) {
                                        EditableText.forTextValue(value)
                                    } else {
                                        null
                                    }
                            }

                            val shouldPushToRight =
                                value is TextValue && chosenText?.value == value && chosenText?.string != value.get()

                            var highlightCursor: (Int) -> Unit = {}

                            chosenText?.let {
                                if (it.value != value) {
                                    return@let
                                }

                                val input = it.string

                                if (it.selectionActive()) {
                                    val start =
                                        textX - 1 + fontSemibold35.getStringWidth(input.take(it.selectionStart!!))
                                    val end = textX - 1 + fontSemibold35.getStringWidth(input.take(it.selectionEnd!!))
                                    drawRect(
                                        start,
                                        textY - 3,
                                        end,
                                        textY + fontSemibold35.fontHeight - 2,
                                        Color(7, 152, 252).rgb
                                    )
                                }

                                highlightCursor = { textX ->
                                    val cursorX = textX + fontSemibold35.getStringWidth(input.take(it.cursorIndex))
                                    drawRect(
                                        cursorX,
                                        textY - 3,
                                        cursorX + 1,
                                        textY + fontSemibold35.fontHeight - 2,
                                        Color.WHITE.rgb
                                    )
                                }
                            }

                            fontSemibold35.drawString(startText, startX, textY, Color.WHITE.rgb)

                            val defaultColor = if (shouldPushToRight) Color.LIGHT_GRAY else minecraftRed

                            val originalX = textX - 1

                            // This usually happens when a value rejects a change and auto-sets it to a default value.
                            if (shouldPushToRight) {
                                valueText = "($valueText)"
                                val valueWidth = fontSemibold35.getStringWidth(valueText)
                                moduleElement.settingsWidth = combinedWidth + valueWidth + 12
                                fontSemibold35.drawString(chosenText!!.string, textX, textY, minecraftRed.rgb)
                                textX += valueWidth + 4
                            }

                            fontSemibold35.drawString(valueText, textX, textY, defaultColor.rgb)

                            highlightCursor(originalX)

                            yPos += 12
                        }
                    }
                }

                moduleElement.adjustWidth()

                moduleElement.settingsHeight = yPos - moduleElement.y - 4

                if (moduleElement.settingsWidth > 0 && yPos > moduleElement.y + 4) {
                    if (mouseButton != null && mouseX in minX..maxX && mouseY in moduleElement.y + 6..yPos + 2) {
                        return true
                    }
                }
            }
        }
        return false
    }
}