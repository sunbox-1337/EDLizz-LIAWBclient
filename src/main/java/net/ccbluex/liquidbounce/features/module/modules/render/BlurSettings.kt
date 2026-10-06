package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import java.awt.Color

object BlurSettings : Module("BlurSettings", Category.RENDER) {
    val enabled by boolean("Enabled", false)
    val mode by choices(
        "Mode",
        arrayOf("Offset", "Radial", "Shader", "Gaussian", "Kawase", "Naven"),
        "Offset"
    ) { enabled }

    val offsetStrength by float("OffsetStrength", 8f, 0f..35f) { enabled && mode == "Offset" }
    val offsetDirections by int("OffsetDirections", 12, 4..64) { enabled && mode == "Offset" }
    val offsetLayers by int("OffsetLayers", 6, 4..128) { enabled && mode == "Offset" }
    val passes by int("Passes", 8, 0..50) { enabled && mode == "Offset" }

    val radialStrength by float("RadialStrength", 8f, 0f..35f) { enabled && mode == "Radial" }
    val radialSamples by int("RadialSamples", 8, 1..50) { enabled && mode == "Radial" }

    val shaderStrength by float("ShaderStrength", 4f, 0f..15f) { enabled && mode == "Shader" }
    val quality by int("Quality", 0, 0..50) { enabled && mode == "Shader" }
    val shaderIterations by int("ShaderIterations", 1, 0..20) { enabled && mode == "Shader" }

    val gaussianStrength by float("GaussianStrength", 4f, 0f..15f) { enabled && mode == "Gaussian" }
    val gaussianQuality by int("GaussianQuality", 0, 0..50) { enabled && mode == "Gaussian" }
    val gaussianOpacity by int("GaussianOpacity", 100, 0..100) { enabled && mode == "Gaussian" }
    val gaussianDirections by int("GaussianDirections", 24, 4..48) { enabled && mode == "Gaussian" }
    val gaussianLayers by int("GaussianLayers", 16, 2..32) { enabled && mode == "Gaussian" }

    val kawaseIterations by int("KawaseIterations", 3, 1..10) { enabled && mode == "Kawase" }
    val kawaseOffset by int("KawaseOffset", 2, 1..5) { enabled && mode == "Kawase" }

    val navenRadius by float("NavenRadius", 12f, 0f..120f) { enabled && mode == "Naven" }
    val navenLayers by int("NavenLayers", 8, 1..24) { enabled && mode == "Naven" }
    val navenDirections by int("NavenDirections", 16, 8..32) { enabled && mode == "Naven" }
    val navenOpacity by int("NavenOpacity", 100, 0..100) { enabled && mode == "Naven" }

    // 原版 GUI 按钮（单人/多人模式等）的模糊与阴影
    val guiButtonBlur by boolean("GuiButtonBlur", true) { enabled || liquidGlass }
    val guiButtonShadow by boolean("GuiButtonShadow", true) { enabled || liquidGlass }
    val guiButtonShadowMask by boolean("GuiButtonShadowMask", false) { enabled && guiButtonShadow }
    val guiButtonShadowStrength by int("GuiButtonShadowStrength", 10, 0..40) { enabled && guiButtonShadow }
    val guiButtonRadius by float("GuiButtonRadius", 4F, 0F..10F) { enabled }
    val guiButtonShadowRadius by float("GuiButtonShadowRadius", 4F, 0F..10F) { enabled && guiButtonShadow }

    val guiButtonBlurMethod by choices("GuiButtonBlurMethod", arrayOf("Blur", "Acrylic"), "Blur") { enabled && guiButtonBlur }

    // 原版 GUI 按钮颜色（自定义）
    val guiButtonColor by color("GuiButtonColor", Color(33, 33, 33, 200)) { enabled }
    val guiButtonDisabledColor by color("GuiButtonDisabledColor", Color(100, 100, 100, 150)) { enabled }
    val guiButtonTextColor by color("GuiButtonTextColor", Color(255, 255, 255)) { enabled }
    val guiButtonDisabledTextColor by color("GuiButtonDisabledTextColor", Color(170, 170, 170)) { enabled }
    val guiButtonShadowColor by color("GuiButtonShadowColor", Color(0, 0, 0, 90)) { enabled && guiButtonShadow }

    fun guiButtonBgArgb() =
        Color(guiButtonColor.red, guiButtonColor.green, guiButtonColor.blue, guiButtonColor.alpha).rgb
    fun guiButtonDisabledBgArgb() =
        Color(guiButtonDisabledColor.red, guiButtonDisabledColor.green, guiButtonDisabledColor.blue, guiButtonDisabledColor.alpha).rgb
    fun guiButtonTextArgb() =
        Color(guiButtonTextColor.red, guiButtonTextColor.green, guiButtonTextColor.blue, guiButtonTextColor.alpha).rgb
    fun guiButtonDisabledTextArgb() =
        Color(guiButtonDisabledTextColor.red, guiButtonDisabledTextColor.green, guiButtonDisabledTextColor.blue, guiButtonDisabledTextColor.alpha).rgb
    fun guiButtonShadowArgb() =
        Color(guiButtonShadowColor.red, guiButtonShadowColor.green, guiButtonShadowColor.blue, guiButtonShadowColor.alpha).rgb

    // 原版 GUI 按钮点击动画：按下沿高斯钟前半段收缩 + 向自定义颜色渐变，松开沿对称后半段弹回
    val guiButtonClickAnim by boolean("GuiButtonClickAnim", true) { enabled }
    val guiButtonAnimSpeed by float("GuiButtonAnimSpeed", 6f, 1f..30f) { guiButtonClickAnim }
    val guiButtonAnimShrink by float("GuiButtonAnimShrink", 0.08f, 0f..0.5f) { guiButtonClickAnim }
    val guiButtonAnimCurve by float("GuiButtonAnimCurve", 2.4f, 0.5f..8f) { guiButtonClickAnim }
    val guiButtonPressColor by color("GuiButtonPressColor", Color(255, 235, 59, 230)) { guiButtonClickAnim }
    val guiButtonDisabledPressColor by color("GuiButtonDisabledPressColor", Color(140, 140, 140, 190)) { guiButtonClickAnim }

    fun guiButtonPressArgb() =
        Color(guiButtonPressColor.red, guiButtonPressColor.green, guiButtonPressColor.blue, guiButtonPressColor.alpha).rgb
    fun guiButtonDisabledPressArgb() =
        Color(guiButtonDisabledPressColor.red, guiButtonDisabledPressColor.green, guiButtonDisabledPressColor.blue, guiButtonDisabledPressColor.alpha).rgb

    // 原版 GUI 按钮 hover 白光：光标移到按钮上时亮起（仅对可点击按钮生效，禁用的不亮）
    val guiButtonHoverGlow by boolean("GuiButtonHoverGlow", true) { enabled || liquidGlass }
    val guiButtonHoverGlowColor by color("GuiButtonHoverGlowColor", Color(255, 255, 255, 200)) { guiButtonHoverGlow }
    val guiButtonHoverGlowRadius by int("GuiButtonHoverGlowRadius", 80, 5..400) { guiButtonHoverGlow }

    fun guiButtonHoverGlowArgb() =
        Color(guiButtonHoverGlowColor.red, guiButtonHoverGlowColor.green, guiButtonHoverGlowColor.blue, guiButtonHoverGlowColor.alpha).rgb

    // 边框模糊设置
    val borderEnabled by boolean("BorderEnabled", false) { enabled }
    val borderWidth by float("BorderWidth", 4f, 0f..20f) { borderEnabled }
    val borderBlurStrength by float("BorderBlurStrength", 8f, 0f..35f) { borderEnabled }
    val borderColor by color("BorderColor", Color(255, 255, 255, 150)) { borderEnabled }
    val borderOpacity by int("BorderOpacity", 100, 0..100) { borderEnabled }
    val borderOnly by boolean("BorderOnly", true) { borderEnabled }

    // 各 HUD 元素的模糊开关
    val chatStyle by boolean("ChatStyle", true) { enabled || liquidGlass }
    val watermark by boolean("Watermark", true) { enabled || liquidGlass }
    val scoreboard by boolean("Scoreboard", true) { enabled || liquidGlass }
    val notifications by boolean("Notifications", true) { enabled || liquidGlass }
    val targetHUD by boolean("TargetHUD", true) { enabled || liquidGlass }
    val blockRateDisplay by boolean("BlockRateDisplay", true) { enabled || liquidGlass }
    val gapple by boolean("Gapple", true) { enabled || liquidGlass }
    val arraylist by boolean("Arraylist", true) { enabled || liquidGlass }
    val chestStealer by boolean("ChestStealer", true) { enabled || liquidGlass }
    val inventory by boolean("Inventory", true) { enabled || liquidGlass }
    val hotbar by boolean("Hotbar", true) { enabled || liquidGlass }
    val armor by boolean("Armor", true) { enabled || liquidGlass }
    val keystrokes by boolean("Keystrokes", true) { enabled || liquidGlass }
    val potionEffects by boolean("PotionEffects", true) { enabled || liquidGlass }

    // 新增：ClientDetector 模块的模糊开关
    val clientDetector by boolean("ClientDetector", true) { enabled || liquidGlass }

    // ===== Container/Inventory style (shadow + blur + rounded corners) =====
    val containerStyle by boolean("ContainerStyle", false)
    val containerBlur by boolean("ContainerBlur", true) { containerStyle }
    val containerPanelColor by color("ContainerPanelColor", Color(16, 16, 16, 190)) { containerStyle }
    val containerRadius by float("ContainerRadius", 6f, 0f..20f) { containerStyle }
    val containerShadow by boolean("ContainerShadow", true) { containerStyle }
    val containerShadowColor by color("ContainerShadowColor", Color(0, 0, 0, 150)) { containerStyle && containerShadow }
    val containerShadowStrength by int("ContainerShadowStrength", 16, 0..40) { containerStyle && containerShadow }
    val containerShadowMask by boolean("ContainerShadowMask", true) { containerStyle && containerShadow }
    val containerSlotBox by boolean("ContainerSlotBox", true) { containerStyle }
    val containerSlotColor by color("ContainerSlotColor", Color(255, 255, 255, 30)) { containerStyle && containerSlotBox }
    val containerSlotRadius by float("ContainerSlotRadius", 5f, 0f..10f) { containerStyle && containerSlotBox }

    // ===== Liquid Glass（独立于模糊的液态玻璃开关与参数） =====
    // 与上面的 enabled/模糊完全独立：可以只开玻璃不开模糊，也可以两者都开（玻璃会对"已模糊"的画面再折射）。
    // 位置与圆角半径不在这里设置——组件照常把 x/y/w/h/radius 交给 BlurUtils.drawOffsetBlur，
    // 由 BlurUtils 转交给 LiquidGlassUtils（复用组件已有的定位/圆角）。
    val liquidGlass by boolean("LiquidGlass", false)

    /** ArrayList 是否单独套用液态玻璃效果（可独立于全局 LiquidGlass 关闭）。 */
    val arraylistLiquidGlass by boolean("ArraylistLiquidGlass", true) { liquidGlass }

    /** 组件的模糊调用点是否需要触发后处理（模糊或玻璃任一开启）。 */
    val active: Boolean
        get() = enabled || liquidGlass

    val liquidGlassMode by choices("LiquidGlassMode", arrayOf("Clear", "Tinted"), "Clear") { liquidGlass }

    // 公共
    val lgBlurRadius by float("LG-BlurRadius", 6f, 0f..32f) { liquidGlass }
    val lgAspectCorrect by boolean("LG-AspectCorrect", true) { liquidGlass }
    val lgRefractionPower by float("LG-RefractionPower", 0.75f, 0f..8f) { liquidGlass }
    val lgRefractionEdge by float("LG-RefractionEdge", 12f, 1f..64f) { liquidGlass }
    val lgNoise by float("LG-Noise", 0.03f, 0f..0.3f) { liquidGlass }
    val lgAlpha by float("LG-Alpha", 1f, 0.1f..1f) { liquidGlass }

    // Clear 模式：方向性边缘高光
    val lgGlowWeight by float("LG-GlowWeight", 0.35f, -1f..1f) { liquidGlass && liquidGlassMode == "Clear" }
    val lgGlowBias by float("LG-GlowBias", 0f, -1f..1f) { liquidGlass && liquidGlassMode == "Clear" }
    val lgGlowEdge0 by float("LG-GlowEdge0", 0f, -1f..1f) { liquidGlass && liquidGlassMode == "Clear" }
    val lgGlowEdge1 by float("LG-GlowEdge1", 0.4f, -1f..1f) { liquidGlass && liquidGlassMode == "Clear" }

    // Tinted 模式：色散 + 自适应染色 + 亮度整形
    val lgTintR by float("LG-TintR", 0.82f, 0f..1f) { liquidGlass && liquidGlassMode == "Tinted" }
    val lgTintG by float("LG-TintG", 0.88f, 0f..1f) { liquidGlass && liquidGlassMode == "Tinted" }
    val lgTintB by float("LG-TintB", 1f, 0f..1f) { liquidGlass && liquidGlassMode == "Tinted" }
    val lgTintStrength by float("LG-TintStrength", 0.12f, 0f..1f) { liquidGlass && liquidGlassMode == "Tinted" }
    val lgChromaStrength by float("LG-ChromaStrength", 0.0015f, 0f..0.02f) { liquidGlass && liquidGlassMode == "Tinted" }
    val lgDarkness by float("LG-Darkness", 0f, 0f..1f) { liquidGlass && liquidGlassMode == "Tinted" }
}