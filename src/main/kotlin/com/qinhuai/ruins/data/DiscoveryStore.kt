package com.qinhuai.ruins.data

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

object DiscoveryStore {

    private lateinit var file: File
    private val map = HashMap<UUID, MutableSet<String>>()
    private val templateMap = HashMap<UUID, MutableSet<String>>()

    fun init(file: File) {
        this.file = file
        load()
    }

    fun has(player: UUID, anchorId: String): Boolean = map[player]?.contains(anchorId) == true

    fun add(player: UUID, anchorId: String): Boolean {
        val set = map.getOrPut(player) { HashSet() }
        if (!set.add(anchorId)) return false
        save()
        return true
    }

    fun hasTemplate(player: UUID, templateId: String): Boolean = templateMap[player]?.contains(templateId) == true

    fun addTemplate(player: UUID, templateId: String): Boolean {
        val set = templateMap.getOrPut(player) { HashSet() }
        if (!set.add(templateId)) return false
        if (RuinStorage.isDatabase()) {
            RuinStorage.runAsync { RuinStorage.addCodex(player, templateId) }
        } else {
            save()
        }
        return true
    }

    fun discoveredTemplates(player: UUID): Set<String> = templateMap[player] ?: emptySet()

    fun mergeTemplates(player: UUID, templates: Collection<String>) {
        if (templates.isEmpty()) return
        templateMap.getOrPut(player) { HashSet() }.addAll(templates)
    }

    fun unloadTemplates(player: UUID) {
        if (RuinStorage.isDatabase()) templateMap.remove(player)
    }

    private fun load() {
        map.clear()
        templateMap.clear()
        if (!::file.isInitialized || !file.exists()) return
        val yml = YamlConfiguration.loadConfiguration(file)
        yml.getConfigurationSection("discoveries")?.getKeys(false)?.forEach { key ->
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
            map[uuid] = yml.getStringList("discoveries.$key").toMutableSet()
        }
        if (!RuinStorage.isDatabase()) {
            yml.getConfigurationSection("template-discoveries")?.getKeys(false)?.forEach { key ->
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
                templateMap[uuid] = yml.getStringList("template-discoveries.$key").toMutableSet()
            }
        }
    }

    private fun save() {
        if (!::file.isInitialized) return
        val yml = YamlConfiguration()
        for ((uuid, set) in map) {
            yml.set("discoveries.$uuid", set.toList())
        }
        if (!RuinStorage.isDatabase()) {
            for ((uuid, set) in templateMap) {
                yml.set("template-discoveries.$uuid", set.toList())
            }
        }
        runCatching { yml.save(file) }
    }
}
