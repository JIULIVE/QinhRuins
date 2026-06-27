package com.qinhuai.ruins.data

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object MechFiredStore {

    private lateinit var file: File
    private val map = HashMap<String, MutableSet<String>>()

    fun init(file: File) {
        this.file = file
        load()
    }

    fun has(anchorId: String, mechId: String): Boolean =
        map[anchorId]?.contains(mechId) == true

    fun add(anchorId: String, mechId: String) {
        val set = map.getOrPut(anchorId) { HashSet() }
        if (set.add(mechId)) save()
    }

    fun clear(anchorId: String) {
        if (map.remove(anchorId) != null) save()
    }

    private fun load() {
        map.clear()
        if (!::file.isInitialized || !file.exists()) return
        val yml = YamlConfiguration.loadConfiguration(file)
        val section = yml.getConfigurationSection("fired") ?: return
        for (anchorId in section.getKeys(false)) {
            map[anchorId] = yml.getStringList("fired.$anchorId").toMutableSet()
        }
    }

    private fun save() {
        if (!::file.isInitialized) return
        val yml = YamlConfiguration()
        for ((anchorId, set) in map) {
            if (set.isNotEmpty()) yml.set("fired.$anchorId", set.toList())
        }
        runCatching { yml.save(file) }
    }
}
