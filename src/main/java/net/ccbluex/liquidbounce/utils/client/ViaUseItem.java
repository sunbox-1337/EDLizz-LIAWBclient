package net.ccbluex.liquidbounce.utils.client;

import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import de.florianmichael.vialoadingbase.ViaLoadingBase;

/**
 * ViaUseItem —— MoonLight「GrimAC NoSlow」跨版本核心（移植自 MoonLight NoSlowdown）。
 *
 * <p>通过 Via 管线直接注入一个 <b>1.9 协议的 Use Item 包</b>
 * （client→server Play，packet id 29 = 0x1D，Type.VAR_INT 1 = 主手），
 * 从 ViaRewind 的 1.8↔1.9 转换点之后进入管线（skipCurrentPipeline=true），
 * 由后续的 ViaVersion/ViaBackwards 协议链翻译成目标服务器版本（如 1.12.2）的 Use Item 发出去。</p>
 *
 * <p>为什么只有跨版本有用：Grim 在服务器端按「客户端协议版本」选择 NoSlow/格挡判定分支，
 * 1.9+ 协议的「使用物品」语义和 1.8 不同 —— 每个移动 tick 刷新一次 Use Item，
 * 服务端认为你在持续使用物品（格挡/吃），但 1.9+ 分支的减速判定与 1.8 不同，
 * 从而绕过减速。直连 1.8 服务器时这条协议分支不存在，发了也没用。</p>
 *
 * <p>MoonLight 原版用的 ViaRewind 3.x 的 {@code PacketUtil.sendToServer}，
 * 本项目带的是 ViaRewind 4.0.4（类名改成 {@code v1_9to1_8.Protocol1_9To1_8}，
 * PacketUtil 已删除），等价 API 是 ViaVersion 5.x 的
 * {@code PacketWrapper.sendToServer(protocol, skipCurrentPipeline)}。
 * 注意本项目这份 ViaVersion 5.2.0 的类型常量在 {@code Types} 而不是 {@code Type}。</p>
 */
public final class ViaUseItem {

    private ViaUseItem() {
    }

    /**
     * 发一个 1.9 的 Use Item（主手）。成功返回 true；
     * 没有活动连接（还没进服 / 直连）或管线异常时返回 false。
     */
    public static boolean sendUseItem() {
        try {
            final java.util.Collection<UserConnection> connections =
                    Via.getManager().getConnectionManager().getConnections();
            if (connections == null || connections.isEmpty()) {
                return false;
            }
            final UserConnection user = connections.iterator().next();

            // PacketWrapper.create(29, null, user) —— 29 = 0x1D Use Item（1.9 C2S）
            final PacketWrapper useItem = PacketWrapper.create(29, null, user);
            // useItem.write(Types.VAR_INT, 1) —— 1 = main hand
            useItem.write((Type<Integer>) (Type<?>) Types.VAR_INT, 1);
            // 从 ViaRewind 转换点之后送入管线（跳过它自己的 1.8→1.9 转换）
            useItem.sendToServer(Protocol1_9To1_8.class, true);
            return true;
        } catch (final Throwable t) {
            return false;
        }
    }

    /**
     * 当前是否跨版本连接到 1.9+ 服务器（如先跨 1.12.2 再进目标服）。
     * 只有这时 Use Item 注入才有意义。
     */
    public static boolean isCrossVersionTo9Plus() {
        try {
            return ViaLoadingBase.getInstance().getTargetVersion()
                    .newerThanOrEqualTo(ProtocolVersion.v1_9);
        } catch (final Throwable t) {
            return false;
        }
    }
}
