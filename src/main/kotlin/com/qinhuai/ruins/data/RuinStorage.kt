package com.qinhuai.ruins.data

import com.qinhuai.corelib.database.DatabaseManager
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.Plugin
import java.util.UUID

object RuinStorage {

    private lateinit var plugin: Plugin
    private var enabled = false

    private const val TABLE = "qr_codex"

    fun init(plugin: Plugin, config: FileConfiguration) {
        this.plugin = plugin
        enabled = false
        val type = (config.getString("storage.type") ?: "yaml").trim().lowercase()
        if (type == "yaml") return
        if (!DatabaseManager.isReady()) {
            plugin.logger.warning("[QinhRuins] storage.type=$type，但 QinhCoreLib 数据库不可用，图鉴回退本地 YAML")
            return
        }
        try {
            createTable()
            enabled = true
            val backend = if (DatabaseManager.isMySQL()) "MySQL（跨服共享）" else "SQLite（本地）"
            plugin.logger.info("[QinhRuins] 图鉴存储后端：QinhCoreLib 数据库 — $backend")
        } catch (e: Exception) {
            enabled = false
            plugin.logger.warning("[QinhRuins] 图鉴表初始化失败，回退本地 YAML：${e.message}")
        }
    }

    fun isDatabase(): Boolean = enabled

    private fun createTable() {
        DatabaseManager.update(
            "CREATE TABLE IF NOT EXISTS $TABLE (player VARCHAR(36) NOT NULL, template VARCHAR(64) NOT NULL, PRIMARY KEY(player, template))",
        )
    }

    fun loadCodex(uuid: UUID): Set<String> {
        if (!enabled) return emptySet()
        return runCatching {
            DatabaseManager.queryRows("SELECT template FROM $TABLE WHERE player=?", listOf(uuid.toString()))
                .mapNotNull { it["template"] as? String }.toSet()
        }.onFailure { plugin.logger.warning("[QinhRuins] 读图鉴失败：${it.message}") }.getOrDefault(emptySet())
    }

    fun addCodex(uuid: UUID, templateId: String) {
        if (!enabled) return
        val sql = if (DatabaseManager.isMySQL()) {
            "INSERT IGNORE INTO $TABLE(player,template) VALUES(?,?)"
        } else {
            "INSERT OR IGNORE INTO $TABLE(player,template) VALUES(?,?)"
        }
        runCatching {
            DatabaseManager.update(sql, uuid.toString(), templateId)
        }.onFailure { plugin.logger.warning("[QinhRuins] 写图鉴失败：${it.message}") }
    }

    fun runAsync(task: () -> Unit) {
        if (!::plugin.isInitialized) return
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { task() })
    }

    fun close() {
        enabled = false
    }
}
