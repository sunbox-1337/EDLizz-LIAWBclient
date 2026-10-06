package net.ccbluex.liquidbounce.chat

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.util.ResourceLocation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * IRC 头像解析。
 *
 * 只做「本地已知」这一层：
 *  - 玩家在同世界的玩家列表里 → 直接用 NetworkPlayerInfo 的皮肤（无需联网、不中转）；
 *  - 否则退回默认皮肤。
 *
 * 之所以没有走 SkinManager 的下载路径：本库里没有任何地方用过它
 * （只找到 MixinSkinManager / MixinAbstractClientPlayer 两个 mixin），
 * 方法名没有可靠依据，不能凭记忆写。跨世界抓取留到后面做。
 */
object IrcAvatars {
    private val cache = ConcurrentHashMap<String, ResourceLocation>()

    /** 没有任何信息时的兜底 */
    private val fallbackSkin = ResourceLocation("textures/entity/steve.png")

    /** 按 uuid / 玩家名解析头像贴图，永远返回一个可用贴图，不会是 null */
    fun resolve(uuid: String, name: String): ResourceLocation {
        val key = if (uuid.isNotBlank()) uuid else name
        if (key.isBlank()) return fallbackSkin

        cache[key]?.let { return it }

        val skin = runCatching { lookup(uuid, name) }.getOrNull() ?: fallbackSkin
        cache[key] = skin
        return skin
    }

    private fun lookup(uuid: String, name: String): ResourceLocation? {
        val targetUuid = runCatching { UUID.fromString(uuid) }.getOrNull()

        val info = Minecraft.getMinecraft().netHandler?.playerInfoMap?.firstOrNull { playerInfo ->
            val profile = playerInfo.gameProfile
            (targetUuid != null && profile.id == targetUuid) ||
                (name.isNotBlank() && profile.name.equals(name, ignoreCase = true))
        }

        // 玩家在列表里 → 用它自己的皮肤；不在就看能不能按 uuid 拿默认皮
        return info?.locationSkin ?: targetUuid?.let { DefaultPlayerSkin.getDefaultSkin(it) }
    }

    /** 换账号 / 登出时清掉，避免串头像 */
    fun clear() {
        cache.clear()
    }
}
