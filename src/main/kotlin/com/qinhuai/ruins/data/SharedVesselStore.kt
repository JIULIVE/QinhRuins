package com.qinhuai.ruins.data

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object SharedVesselStore {

    private lateinit var file: File
    private val populated = HashSet<String>()
    private var dirty = false

    fun init(file: File) {
        this.file = file
        load()
    }

    fun isPopulated(key: String): Boolean = populated.contains(key)

    fun markPopulated(key: String) {
        if (populated.add(key)) dirty = true
    }

    fun clearAnchor(anchorId: String) {
        val removed = populated.filter { it.startsWith("$anchorId|") }
        if (removed.isEmpty()) return
        removed.forEach { populated.remove(it) }
        dirty = true
    }

    fun save() {
        if (!::file.isInitialized || !dirty) return
        val yml = YamlConfiguration()
        yml.set("populated", populated.toList())
        runCatching { yml.save(file) }
        dirty = false
    }

    private fun load() {
        populated.clear()
        if (!::file.isInitialized || !file.exists()) return
        val yml = YamlConfiguration.loadConfiguration(file)
        populated.addAll(yml.getStringList("populated"))
    }
}
