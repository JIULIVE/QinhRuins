package com.qinhuai.ruins.loot

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object LootTableRegistry {

    private val tables = HashMap<String, LootTable>()

    fun load(dir: File): Int {
        tables.clear()
        if (!dir.exists()) {
            dir.mkdirs()
            return 0
        }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return 0
        for (file in files) {
            runCatching { parse(file) }.onSuccess { tables[file.nameWithoutExtension] = it }
        }
        return tables.size
    }

    fun get(name: String): LootTable? = tables[name]

    fun ids(): List<String> = tables.keys.sorted()

    private fun parse(file: File): LootTable {
        val yml = YamlConfiguration.loadConfiguration(file)
        val rolls = yml.getInt("rolls", 1).coerceAtLeast(1)
        val entries = parseEntries(yml.getMapList("entries"))
        val groups = yml.getConfigurationSection("groups")?.let { section ->
            section.getKeys(false).map { name ->
                LootGroup(
                    condition = section.getString("$name.condition")?.takeIf { it.isNotBlank() },
                    rolls = section.getInt("$name.rolls", 1).coerceAtLeast(1),
                    entries = parseEntries(section.getMapList("$name.entries")),
                )
            }
        } ?: emptyList()
        val containers = yml.getStringList("containers").map { it.uppercase() }
        val vanilla = yml.getString("vanilla")?.takeIf { it.isNotBlank() }
        return LootTable(rolls, entries, groups, containers, vanilla)
    }

    private fun parseEntries(list: List<Map<*, *>>): List<LootEntry> = list.mapNotNull { raw ->
        @Suppress("UNCHECKED_CAST")
        val m = raw as Map<String, Any?>
        val item = m["item"]?.toString() ?: return@mapNotNull null
        val (min, max) = parseAmount(m["amount"])
        LootEntry(
            item = item,
            weight = (m["weight"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
            minAmount = min,
            maxAmount = max,
            minGrowth = (m["min-growth"] as? Number)?.toDouble() ?: 0.0,
            unique = m["unique"] as? Boolean ?: false,
        )
    }

    private fun parseAmount(value: Any?): Pair<Int, Int> {
        if (value is Number) return value.toInt() to value.toInt()
        val text = value?.toString() ?: "1"
        if (text.contains('-')) {
            val parts = text.split('-')
            val min = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
            val max = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: min
            return minOf(min, max) to maxOf(min, max)
        }
        val n = text.trim().toIntOrNull() ?: 1
        return n to n
    }
}
