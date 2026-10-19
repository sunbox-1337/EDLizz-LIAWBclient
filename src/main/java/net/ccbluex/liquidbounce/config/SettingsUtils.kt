package net.ccbluex.liquidbounce.config

import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.api.ClientApi
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.Targets
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.io.HttpClient
import net.ccbluex.liquidbounce.utils.io.get
import net.ccbluex.liquidbounce.utils.kotlin.StringUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.translateAlternateColorCodes
import org.lwjgl.input.Keyboard
import kotlin.reflect.KMutableProperty0

/**
 * Utility class for handling settings and scripts in LiquidBounce.
 */
object SettingsUtils {

    /**
     * Counters of the currently applied script, used to report the result back to the user.
     * Silent failures are the reason why people think their settings "aren't saved".
     */
    private var appliedCount = 0
    private var failedCount = 0
    private val failedEntries = ArrayList<String>()

    fun loadFromUrl(url: String) = if (url.startsWith("http")) {
        HttpClient.get(url).use {
            if (!it.isSuccessful) {
                error(it.message)
            }

            it.body.string()
        }
    } else {
        ClientApi.getSettingsScript(settingId = url)
    }

    /**
     * Execute settings script.
     * @param script The script to apply.
     */
    fun applyScript(script: String) {
        appliedCount = 0
        failedCount = 0
        failedEntries.clear()

        script.lineSequence().forEachIndexed { index, s ->
            if (s.isEmpty() || s.startsWith('#')) {
                return@forEachIndexed
            }

            val args = s.split(' ').toTypedArray()

            if (args.size <= 1) {
                chat("§7[§3§lAutoSettings§7] §cSyntax error at line '$index' in setting script.\n§8§lLine: §7$s")
                return@forEachIndexed
            }

            when (args[0]) {
                "chat" -> chat(
                    "§e${
                        translateAlternateColorCodes(
                            StringUtils.toCompleteString(
                                args,
                                1
                            )
                        )
                    }"
                )

                "unchat" -> chat(
                    translateAlternateColorCodes(
                        StringUtils.toCompleteString(
                            args,
                            1
                        )
                    )
                )

                "load" -> {
                    val url = StringUtils.toCompleteString(args, 1)
                    runCatching {
                        applyScript(loadFromUrl(url))
                    }.onSuccess {
                        chat("§7[§3§lAutoSettings§7] §7Loaded settings §a§l$url§7.")
                    }.onFailure {
                        chat("§7[§3§lAutoSettings§7] §7Failed to load settings §a§l$url§7.")
                    }
                }

                "targetPlayer", "targetPlayers" -> setTargetSetting(Targets::player, args)
                "targetMobs" -> setTargetSetting(Targets::mob, args)
                "targetAnimals" -> setTargetSetting(Targets::animal, args)
                "targetInvisible" -> setTargetSetting(Targets::invisible, args)
                "targetDead" -> setTargetSetting(Targets::dead, args)

                else -> {
                    if (args.size < 3) {
                        chat("§7[§3§lAutoSettings§7] §cSyntax error at line '$index' in setting script.\n§8§lLine: §7$s")
                        return@forEachIndexed
                    }

                    val moduleName = args[0]
                    val valueName = args[1]
                    // Value may contain spaces (e.g. font names) — rejoin everything after the value name
                    val value = StringUtils.toCompleteString(args, 2)
                    val module = moduleManager[moduleName]

                    if (module == null) {
                        chat("§7[§3§lAutoSettings§7] §cModule §a§l$moduleName§c does not exist!")
                        failedCount++
                        failedEntries.add("$moduleName (module missing)")
                        return@forEachIndexed
                    }

                    when (valueName) {
                        "toggle" -> setToggle(module, value)
                        "bind" -> setBind(module, value)
                        else -> setValue(module, valueName, value, args)
                    }
                }
            }
        }

        // Report what actually made it into the client instead of failing silently
        if (failedCount > 0) {
            chat("§7[§3§lAutoSettings§7] §c$failedCount §7entries failed: " +
                    failedEntries.take(5).joinToString(", ") { "§c$it§7" } +
                    if (failedEntries.size > 5) " §7and ${failedEntries.size - 5} more" else "")
            LOGGER.error("[SettingsUtils] Failed entries: ${failedEntries.joinToString(" | ")}")
        }
        LOGGER.info("[SettingsUtils] Applied $appliedCount entries, $failedCount failed.")
        chat("§7[§3§lAutoSettings§7] §a$appliedCount §7entries applied§7, §c$failedCount §cfailed§7.")

        // Ensure values.json saved after applying script (keeps JSON consistent)
        FileManager.saveConfig(FileManager.valuesConfig)
    }

    // Utility functions for setting target settings
    private fun setTargetSetting(setting: KMutableProperty0<Boolean>, args: Array<String>) {
        setting.set(args[1].toBoolean())
        chat("§7[§3§lAutoSettings§7] §a§l${args[0]}§7 set to §c§l${args[1]}§7.")
    }

    // Utility functions for setting toggles
    private fun setToggle(module: Module, value: String) {
        module.state = value.toBoolean()
        appliedCount++
        chat("§7[§3§lAutoSettings§7] §a§l${module.getName()} §7was toggled §c§l${if (module.state) "on" else "off"}§7.")
    }

    // Utility functions for setting binds
    private fun setBind(module: Module, value: String) {
        // value is the key name (e.g. "R"), convert to index
        module.keyBind = Keyboard.getKeyIndex(value)
        appliedCount++
        chat(
            "§7[§3§lAutoSettings§7] §a§l${module.getName()} §7was bound to §c§l${
                Keyboard.getKeyName(
                    module.keyBind
                )
            }§7."
        )
    }

    // Utility functions for setting values
    private fun setValue(module: Module, valueName: String, value: String, args: Array<String>) {
        val moduleValue = module[valueName]

        if (moduleValue == null) {
            chat("§7[§3§lAutoSettings§7] §cValue §a§l$valueName§c wasn't found in module §a§l${module.getName()}§c.")
            return
        }

        try {
            val before = moduleValue.toText()
            moduleValue.fromText(value)
            val after = moduleValue.toText()

            if (before == after && after != value) {
                // Parser rejected the text silently -> report it instead of claiming success
                failedCount++
                failedEntries.add("${module.getName()}.${moduleValue.name}")
                chat("§7[§3§lAutoSettings§7] §cValue §a§l${moduleValue.name}§c in §a§l${module.getName()}§c could not parse '§e$value§c' (stayed §7$after§c).")
            } else {
                appliedCount++
                chat("§7[§3§lAutoSettings§7] §a§l${module.getName()}§7 value §8§l${moduleValue.name}§7 set to §c§l$after§7.")
            }
        } catch (e: Exception) {
            failedCount++
            failedEntries.add("${module.getName()}.${moduleValue.name}")
            chat("§7[§3§lAutoSettings§7] §a§l${e.javaClass.name}§7(${e.message}) §cAn Exception occurred while setting §a§l$value§c to §a§l${moduleValue.name}§c in §a§l${module.getName()}§c.")
        }
    }

    /**
     * Generate settings script.
     * @param values Include values in the script.
     * @param binds Include key binds in the script.
     * @param states Include module states in the script.
     *
     * NOTE: Behavior:
     * - Export ALL modules EXCEPT modules in Category.RENDER.
     * - For remaining modules export ALL values (no filtering by subjective/shouldRender).
     * - Export ALL binds (key bindings) for those modules using key *names*.
     * - Export module states (toggle) for those modules.
     */
    fun generateScript(values: Boolean, binds: Boolean, states: Boolean): String {
        return buildString {
            for (module in moduleManager) {
                // Skip render modules entirely
                if (module.category == Category.RENDER) continue

                if (values) {
                    for (value in module.values) {
                        // Fault tolerant: a single bad value must not abort the whole save
                        val valueString = runCatching { "${module.name} ${value.name} ${value.toText()}" }
                            .onFailure {
                                LOGGER.error("[SettingsUtils] Failed to serialize ${module.name}.${value.name}, skipped.", it)
                            }
                            .getOrNull()
                        if (valueString != null && valueString.isNotBlank()) {
                            appendLine(valueString)
                        }
                    }
                }

                if (states) {
                    appendLine("${module.name} toggle ${module.state}")
                }

                if (binds) {
                    val keyName = Keyboard.getKeyName(module.keyBind)
                    appendLine("${module.name} bind $keyName")
                }
            }
        }.trimEnd()
    }

}
