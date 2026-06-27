package com.qinhuai.ruins.data

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File

object VesselStore {

    data class Record(val openedAt: Long, val slots: Map<Int, ItemStack>)

    private lateinit var file: File
    private val records = HashMap<String, Record>()
    private var dirty = false

    fun init(file: File) {
        this.file = file
        load()
    }

    fun get(key: String): Record? = records[key]

    fun put(key: String, openedAt: Long, slots: Map<Int, ItemStack>) {
        records[key] = Record(openedAt, HashMap(slots))
        dirty = true
    }

    fun updateSlots(key: String, slots: Map<Int, ItemStack>) {
        val existing = records[key] ?: return
        records[key] = existing.copy(slots = HashMap(slots))
        dirty = true
    }

    fun clearAnchor(anchorId: String) {
        val removed = records.keys.filter { it.contains("|$anchorId|") }
        if (removed.isEmpty()) return
        removed.forEach { records.remove(it) }
        dirty = true
        save()
    }

    fun save() {
        if (!::file.isInitialized || !dirty) return
        val yml = YamlConfiguration()
        var i = 0
        for ((key, record) in records) {
            val path = "vessels.$i"
            yml.set("$path.key", key)
            yml.set("$path.opened", record.openedAt)
            for ((slot, item) in record.slots) yml.set("$path.slots.$slot", item)
            i++
        }
        runCatching { yml.save(file) }
        dirty = false
    }

    private fun load() {
        records.clear()
        if (!::file.isInitialized || !file.exists()) return
        val yml = YamlConfiguration.loadConfiguration(file)
        val section = yml.getConfigurationSection("vessels") ?: return
        for (index in section.getKeys(false)) {
            val key = yml.getString("vessels.$index.key") ?: continue
            val openedAt = yml.getLong("vessels.$index.opened")
            val slots = HashMap<Int, ItemStack>()
            yml.getConfigurationSection("vessels.$index.slots")?.getKeys(false)?.forEach { s ->
                val slot = s.toIntOrNull() ?: return@forEach
                val item = yml.getItemStack("vessels.$index.slots.$s") ?: return@forEach
                slots[slot] = item
            }
            records[key] = Record(openedAt, slots)
        }
    }
}
