package com.qinhuai.ruins.affix

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

object RuinScriptBridge {

    private const val BRIDGE_CLASS = "com.qinhuai.corelib.script.QinhScriptBridge"

    private val instance: Any? by lazy {
        runCatching { Class.forName(BRIDGE_CLASS).getField("INSTANCE").get(null) }.getOrNull()
    }

    private val executeMethod by lazy {
        runCatching {
            instance?.javaClass?.getMethod(
                "execute",
                String::class.java,
                Player::class.java,
                Plugin::class.java,
                Map::class.java,
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull()
    }

    private val registerMethod by lazy {
        runCatching {
            instance?.javaClass?.getMethod("registerPluginScripts", Plugin::class.java, String::class.java)
        }.getOrNull()
    }

    private val isAvailableMethod by lazy {
        runCatching { instance?.javaClass?.getMethod("isAvailable") }.getOrNull()
    }

    fun register(plugin: Plugin) {
        val m = registerMethod ?: return
        runCatching { m.invoke(instance, plugin, "qinhruins") }
    }

    fun runtimeReady(): Boolean {
        val m = isAvailableMethod ?: return false
        return runCatching { m.invoke(instance) as? Boolean ?: false }.getOrDefault(false)
    }

    fun execute(reference: String, player: Player?, plugin: Plugin, variables: Map<String, Any>) {
        val m = executeMethod
        if (m == null || !runtimeReady()) {
            plugin.logger.warning("[QR-JS] GraalJS 未就绪，跳过词缀脚本 ref=$reference（需 CoreLib 拉到 GraalJS 且 javascript.enabled=true）")
            return
        }
        runCatching { m.invoke(instance, reference, player, plugin, variables, true) }
            .onFailure { plugin.logger.warning("[QR-JS] 词缀脚本异常 ref=$reference: ${it.message}") }
    }
}
