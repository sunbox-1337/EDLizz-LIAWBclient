package net.ccbluex.liquidbounce.utils;

import net.ccbluex.liquidbounce.features.module.modules.render.BlurSettings;
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer;
import net.ccbluex.liquidbounce.ui.font.Fonts;
import net.ccbluex.liquidbounce.utils.extras.GlowUtils2;
import net.ccbluex.liquidbounce.utils.render.BlurUtils;
import net.ccbluex.liquidbounce.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;

import static net.minecraft.client.renderer.GlStateManager.resetColor;

public class MaterialButtonRenderer {

    /** 每个按钮的点击动画状态，弱引用键控，关闭界面后自动回收。 */
    private static final Map<GuiButton, AnimState> ANIM_STATES = new WeakHashMap<>();

    private static final class AnimState {
        private boolean pressed;
        private float phase = 1f;   // 当前相位进度 0..1（1 = 已完成）
        private float from;         // 当前相位的起点进度
        private float progress;     // 动画进度 0..1（0 = 常态，1 = 完全按下）
        private long lastNanos;
    }

    public static void draw(GuiButton button, Minecraft mc, int mouseX, int mouseY) {
        if (!button.visible) return;

        boolean hovered = mouseX >= button.xPosition && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height;

        // 高斯钟曲线进度：按下向内收缩 + 渐变变色，松开弹回原色
        float anim = animate(button, hovered);

        float x = button.xPosition;
        float y = button.yPosition;
        float w = button.width;
        float h = button.height;

        // 按进度整体收缩（保持按钮中心不变）
        float scale = 1f - BlurSettings.INSTANCE.getGuiButtonAnimShrink() * anim;
        float animW = w * scale;
        float animH = h * scale;
        float animX = x + (w - animW) / 2f;
        float animY = y + (h - animH) / 2f;

        float radius = BlurSettings.INSTANCE.getGuiButtonRadius() * scale;
        float shadowRadius = BlurSettings.INSTANCE.getGuiButtonShadowRadius();

        boolean effects = BlurSettings.INSTANCE.getActive();

        int bgBase = button.enabled
                ? BlurSettings.INSTANCE.guiButtonBgArgb()
                : BlurSettings.INSTANCE.guiButtonDisabledBgArgb();
        int bgPress = button.enabled
                ? BlurSettings.INSTANCE.guiButtonPressArgb()
                : BlurSettings.INSTANCE.guiButtonDisabledPressArgb();
        int bg = lerpArgb(bgBase, bgPress, anim);

        // 背景模糊（方法可选：Blur = BlurUtils 真实模糊 / Acrylic = GlowUtils2 亚克力）
        if (effects && BlurSettings.INSTANCE.getGuiButtonBlur()) {
            if ("Acrylic".equalsIgnoreCase(BlurSettings.INSTANCE.getGuiButtonBlurMethod())) {
                // GlowUtils2 在当前 GUI 变换下绘制，坐标无需缩放
                GlowUtils2.INSTANCE.drawAcrylicBlur(
                        animX, animY, animW, animH,
                        (int) BlurSettings.INSTANCE.getOffsetStrength(),
                        new Color(bg, true),
                        0.6f
                );
            } else {
                // BlurUtils 在帧缓冲像素空间绘制（自带 glOrtho），GUI 坐标需乘以缩放因子
                float guiScale = new ScaledResolution(mc).getScaleFactor();
                BlurUtils.INSTANCE.drawOffsetBlur(
                        animX * guiScale, animY * guiScale, animW * guiScale, animH * guiScale,
                        BlurSettings.INSTANCE.getPasses(),
                        BlurSettings.INSTANCE.getOffsetStrength(),
                        radius * guiScale
                );
            }
        }

        // 阴影（GlowUtils）；圆角用独立的 GuiButtonShadowRadius 以便和背景贴合
        if (effects && BlurSettings.INSTANCE.getGuiButtonShadow()) {
            GlowUtils.INSTANCE.drawGlow(
                    animX, animY, animW, animH,
                    BlurSettings.INSTANCE.getGuiButtonShadowStrength(),
                    new Color(BlurSettings.INSTANCE.guiButtonShadowArgb(), true),
                    shadowRadius,
                    false,
                    BlurSettings.INSTANCE.getGuiButtonShadowMask()
            );
        }

        // hover 白光（GlowUtils）：从光标位置散开的径向泛光，按按钮圆角裁切；仅可点击按钮
        if (BlurSettings.INSTANCE.getGuiButtonHoverGlow() && button.enabled && hovered) {
            GlowUtils.INSTANCE.drawRoundedRadialGlow(
                    animX, animY, animW, animH,
                    mouseX, mouseY,
                    BlurSettings.INSTANCE.getGuiButtonHoverGlowRadius(),
                    new Color(BlurSettings.INSTANCE.guiButtonHoverGlowArgb(), true),
                    radius
            );
        }

        // 背景（颜色/圆角可自定义，按下时向自定义颜色渐变）
        RenderUtils.INSTANCE.drawRoundedRect(
                animX, animY, animX + animW, animY + animH,
                bg, radius, RenderUtils.RoundedCorners.ALL
        );

        // 文字（颜色可自定义，跟随按钮一起缩放）
        AWTFontRenderer.Companion.setAssumeNonVolatile(true);
        FontRenderer fontRenderer = Fonts.fontSemibold35;
        int textColor = button.enabled
                ? BlurSettings.INSTANCE.guiButtonTextArgb()
                : BlurSettings.INSTANCE.guiButtonDisabledTextArgb();

        float centerX = x + w / 2f;
        float centerY = y + h / 2f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0f);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.translate(-centerX, -centerY, 0f);
        fontRenderer.drawStringWithShadow(
                button.displayString,
                x + (w - fontRenderer.getStringWidth(button.displayString)) / 2f,
                y + (h - 5) / 2f,
                textColor
        );
        GlStateManager.popMatrix();

        AWTFontRenderer.Companion.setAssumeNonVolatile(false);
        resetColor();
    }

    /**
     * 推进点击动画并返回 0..1 的进度。
     * 按下沿高斯钟前半段收缩，松开沿对称的后半段弹回（两端平缓、中段最快）。
     */
    private static float animate(GuiButton button, boolean hovered) {
        if (!BlurSettings.INSTANCE.getGuiButtonClickAnim()) {
            AnimState state = ANIM_STATES.get(button);
            if (state != null) {
                state.pressed = false;
                state.phase = 1f;
                state.progress = 0f;
            }
            return 0f;
        }

        AnimState state = ANIM_STATES.get(button);
        if (state == null) {
            state = new AnimState();
            state.lastNanos = System.nanoTime();
            ANIM_STATES.put(button, state);
        }

        long now = System.nanoTime();
        float dt = Math.min((now - state.lastNanos) / 1_000_000_000f, 0.1f);
        state.lastNanos = now;

        boolean pressed = button.enabled && hovered && Mouse.isButtonDown(0);
        if (pressed != state.pressed) {
            state.pressed = pressed;
            state.from = state.progress;
            state.phase = 0f;
        }

        if (state.phase < 1f) {
            state.phase = Math.min(1f, state.phase + BlurSettings.INSTANCE.getGuiButtonAnimSpeed() * dt);
            float eased = gaussianHalf(state.phase, BlurSettings.INSTANCE.getGuiButtonAnimCurve());
            state.progress = state.pressed
                    ? state.from + (1f - state.from) * eased
                    : state.from * (1f - eased);
        }

        return state.progress;
    }

    /** 高斯钟的单调半段：t=0 → 0，t=1 → 1，两端斜率低、中段最快。 */
    private static float gaussianHalf(float t, float curve) {
        float edge = (float) Math.exp(-curve);
        float value = (float) Math.exp(-curve * (t - 1f) * (t - 1f));
        return (value - edge) / (1f - edge);
    }

    private static int lerpArgb(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int a = lerpChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
