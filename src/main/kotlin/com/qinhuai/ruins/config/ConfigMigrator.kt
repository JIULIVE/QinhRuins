package com.qinhuai.ruins.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

object ConfigMigrator {

    private const val CURRENT_VERSION = 1
    private const val VERSION_KEY = "config-version"

    private val migrations: Map<Int, (org.bukkit.configuration.file.FileConfiguration) -> Unit> = emptyMap()

    fun migrate(plugin: JavaPlugin) {
        val file = File(plugin.dataFolder, "config.yml")
        if (!file.exists()) return
        val config = plugin.config
        val from = config.getInt(VERSION_KEY, 0)
        var changed = false

        if (from < CURRENT_VERSION) {
            for (step in (from + 1)..CURRENT_VERSION) {
                migrations[step]?.let {
                    it(config)
                    plugin.logger.info("[QinhRuins] 配置已迁移到版本 $step")
                    changed = true
                }
            }
        }

        if (backfillDefaults(plugin, config)) changed = true

        if (config.getInt(VERSION_KEY, 0) != CURRENT_VERSION) {
            config.set(VERSION_KEY, CURRENT_VERSION)
            changed = true
        }

        if (changed) {
            config.options().parseComments(true)
            runCatching { config.save(file) }.onFailure {
                plugin.logger.warning("[QinhRuins] 配置迁移落盘失败: ${it.message}")
            }
            if (from < CURRENT_VERSION) {
                plugin.logger.info("[QinhRuins] 配置版本 $from → $CURRENT_VERSION，已自动迁移（保留你的改动与注释）")
            }
        }
    }

    private fun backfillDefaults(plugin: JavaPlugin, config: org.bukkit.configuration.file.FileConfiguration): Boolean {
        val stream = plugin.getResource("config.yml") ?: return false
        val defaults = InputStreamReader(stream, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        var added = false
        for (key in defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue
            if (key == VERSION_KEY) continue
            if (!config.contains(key)) {
                config.set(key, defaults.get(key))
                added = true
            }
        }
        if (added) plugin.logger.info("[QinhRuins] 配置已回填新增的默认项（旧配置缺失的键，不覆盖你已设置的值）")
        return added
    }
}
