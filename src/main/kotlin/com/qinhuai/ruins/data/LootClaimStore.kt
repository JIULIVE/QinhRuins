package com.qinhuai.ruins.data

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

object LootClaimStore {

    private lateinit var file: File
    private val map = HashMap<UUID, MutableSet<String>>()

    fun init(file: File) {
        this.file = file
        load()
    }

    fun has(player: UUID, anchorId: String, chestId: String): Boolean =
        map[player]?.contains(key(anchorId, chestId)) == true

    fun add(player: UUID, anchorId: String, chestId: String): Boolean {
        val set = map.getOrPut(player) { HashSet() }
        if (!set.add(key(anchorId, chestId))) return false
        save()
        return true
    }

    private fun key(anchorId: String, chestId: String): String = "$anchorId|$chestId"

    private fun load() {
        map.clear()
        if (!::file.isInitialized || !file.exists()) return
        val yml = YamlConfiguration.loadConfiguration(file)
        val section = yml.getConfigurationSection("claims") ?: return
        for (k in section.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(k) }.getOrNull() ?: continue
            map[uuid] = yml.getStringList("claims.$k").toMutableSet()
        }
    }

    private fun save() {
        if (!::file.isInitialized) return
        val yml = YamlConfiguration()
        for ((uuid, set) in map) {
            yml.set("claims.$uuid", set.toList())
        }
        runCatching { yml.save(file) }
    }
}
