package com.qinhuai.ruins.data

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

object SpinStore {

    private lateinit var file: File
    private val granted = HashSet<String>()

    fun init(file: File) {
        this.file = file
        load()
    }

    fun grant(player: UUID, anchorId: String) {
        if (granted.add(key(player, anchorId))) save()
    }

    fun canSpin(player: UUID, anchorId: String): Boolean = granted.contains(key(player, anchorId))

    fun consume(player: UUID, anchorId: String) {
        if (granted.remove(key(player, anchorId))) save()
    }

    fun anchorsFor(player: UUID): List<String> {
        val prefix = "$player|"
        return granted.filter { it.startsWith(prefix) }.map { it.substringAfter('|') }
    }

    private fun key(player: UUID, anchorId: String): String = "$player|$anchorId"

    private fun save() {
        if (!::file.isInitialized) return
        val yml = YamlConfiguration()
        yml.set("granted", granted.toList())
        runCatching { yml.save(file) }
    }

    private fun load() {
        granted.clear()
        if (!::file.isInitialized || !file.exists()) return
        val yml = YamlConfiguration.loadConfiguration(file)
        granted.addAll(yml.getStringList("granted"))
    }
}
