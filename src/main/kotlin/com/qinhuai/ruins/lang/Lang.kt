package com.qinhuai.ruins.lang

import com.qinhuai.ruins.QinhRuins
import com.qinhuai.corelib.util.TextUtil
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.jar.JarFile

object Lang {

    private const val FALLBACK_LOCALE = "en_US"
    private const val DEFAULT_LOCALE = "zh_cn"
    private val LOCALE_ENTRY = Regex("^lang/[A-Za-z]{2}_[A-Za-z]{2}/[^/]+\\.yml$")

    @Volatile
    private var locale = DEFAULT_LOCALE
    private val active = HashMap<String, String>()
    private val fallback = HashMap<String, String>()

    fun load() {
        val plugin = QinhRuins.instance
        locale = plugin.config.getString("language", DEFAULT_LOCALE)
            ?.trim()
            ?.ifBlank { DEFAULT_LOCALE }
            ?: DEFAULT_LOCALE
        saveBundledDefaults(plugin)
        active.clear()
        fallback.clear()
        loadLocaleInto(plugin, FALLBACK_LOCALE, fallback)
        loadLocaleInto(plugin, locale, active)
        if (active.isEmpty() && locale != FALLBACK_LOCALE) {
            plugin.logger.warning("[QinhRuins] 语言 '$locale' 在 lang/$locale/ 下没有任何文件，已回退到 $FALLBACK_LOCALE")
        }
    }

    fun get(key: String, vararg args: Pair<String, Any?>): String {
        var text = active[key] ?: fallback[key] ?: return key
        if (args.isNotEmpty()) {
            for ((name, value) in args) {
                text = text.replace("{$name}", value?.toString() ?: "")
            }
        }
        return text
    }

    fun has(key: String): Boolean = active.containsKey(key) || fallback.containsKey(key)

    fun activeLocale(): String = locale

    fun send(sender: CommandSender, key: String, vararg args: Pair<String, Any?>) {
        TextUtil.sendColored(sender, get(key, *args))
    }

    fun log(message: String) {
        TextUtil.logColored(QinhRuins.instance, message)
    }

    private fun loadLocaleInto(plugin: QinhRuins, loc: String, into: MutableMap<String, String>) {
        loadBundledLocaleInto(plugin, loc, into)
        val dir = File(plugin.dataFolder, "lang/$loc")
        if (!dir.isDirectory) return
        dir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val yaml = YamlConfiguration.loadConfiguration(file)
                flatten("", yaml, into)
            }
    }

    private fun loadBundledLocaleInto(plugin: QinhRuins, loc: String, into: MutableMap<String, String>) {
        val location = plugin.javaClass.protectionDomain.codeSource?.location ?: return
        val prefix = "lang/$loc/"
        runCatching {
            JarFile(File(location.toURI())).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".yml") }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        jar.getInputStream(entry).use { input ->
                            val yaml = YamlConfiguration.loadConfiguration(input.reader(Charsets.UTF_8))
                            flatten("", yaml, into)
                        }
                    }
            }
        }
    }

    private fun flatten(prefix: String, section: ConfigurationSection, into: MutableMap<String, String>) {
        for (key in section.getKeys(false)) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val child = section.getConfigurationSection(key)
            if (child != null) {
                flatten(path, child, into)
            } else {
                val value = section.getString(key) ?: continue
                into[path] = value
            }
        }
    }

    private fun saveBundledDefaults(plugin: QinhRuins) {
        val location = plugin.javaClass.protectionDomain.codeSource?.location ?: return
        val names = runCatching {
            JarFile(File(location.toURI())).use { jar ->
                jar.entries().asSequence()
                    .map { it.name }
                    .filter { LOCALE_ENTRY.matches(it) }
                    .toList()
            }
        }.getOrDefault(emptyList())
        for (name in names) {
            val out = File(plugin.dataFolder, name)
            if (!out.exists()) {
                runCatching { plugin.saveResource(name, false) }
            }
        }
    }
}
