package net.ccbluex.liquidbounce.injection.forge.mixins.gui;

import net.ccbluex.liquidbounce.features.module.modules.render.ChatStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.IChatComponent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

@Mixin(GuiNewChat.class)
public class MixinGuiNewChat {

    // 原版每行各画一段背景；这里累计它们的并集，合并成一整块矩形。
    private static boolean lb$first = true;
    private static boolean lb$have = false;
    private static int lb$l, lb$t, lb$r, lb$b;
    private static final FloatBuffer lb$mat = BufferUtils.createFloatBuffer(16);

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void lb$frameStart(int updateCounter, CallbackInfo ci) {
        lb$first = true;
    }

    /**
     * 隐藏刷进聊天栏的 OpenGL 报错（例如 OptiFine / 高清修复在启动后打印的 "OpenGL Error"）。
     */
    @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), cancellable = true)
    private void lb$hideGlErrors(IChatComponent chatComponent, int chatLineId, CallbackInfo ci) {
        if (chatComponent == null) return;
        String text = chatComponent.getUnformattedText();
        if (text == null) return;
        String lower = text.toLowerCase();
        if (lower.contains("opengl") || lower.contains("gl error") || lower.contains("gl_error")) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "drawChat",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V", ordinal = 0)
    )
    private void lb$chatBackground(int left, int top, int right, int bottom, int color) {
        if (!ChatStyle.INSTANCE.handleEvents()) {
            // ChatStyle 关闭 → 保持原版：每行一段纯色背景
            ChatStyle.INSTANCE.drawPlainRect(left, top, right, bottom, color);
            return;
        }

        if (lb$first) {
            lb$first = false;
            if (lb$have) {
                // 聊天背景画在「translate + scale」后的矩阵里，用当前 MODELVIEW 把局部矩形换算成屏幕 GUI 坐标，
                // 再乘以 ScaledResolution 的缩放系数 = 屏幕像素（模糊是屏幕空间采样）。
                lb$mat.clear();
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, lb$mat);
                float x1 = lb$mat.get(0) * lb$l + lb$mat.get(4) * lb$t + lb$mat.get(12);
                float y1 = lb$mat.get(1) * lb$l + lb$mat.get(5) * lb$t + lb$mat.get(13);
                float x2 = lb$mat.get(0) * lb$r + lb$mat.get(4) * lb$b + lb$mat.get(12);
                float y2 = lb$mat.get(1) * lb$r + lb$mat.get(5) * lb$b + lb$mat.get(13);
                double sf = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
                ChatStyle.INSTANCE.drawBackground(
                    lb$l, lb$t, lb$r, lb$b,
                    (float) (x1 * sf), (float) (y1 * sf),
                    (float) ((x2 - x1) * sf), (float) ((y2 - y1) * sf)
                );
            }
            lb$l = left;
            lb$t = top;
            lb$r = right;
            lb$b = bottom;
            lb$have = true;
        } else {
            lb$l = Math.min(lb$l, left);
            lb$t = Math.min(lb$t, top);
            lb$r = Math.max(lb$r, right);
            lb$b = Math.max(lb$b, bottom);
        }
    }
}
