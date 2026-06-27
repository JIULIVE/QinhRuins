package com.qinhuai.ruins.affix

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object AffixRegistry {

    private val affixes = LinkedHashMap<String, AffixDefinition>()

    fun load(file: File): Int {
        affixes.clear()
        if (!file.exists()) return 0
        val yml = YamlConfiguration.loadConfiguration(file)
        val root = yml.getConfigurationSection("affixes") ?: return 0
        for (id in root.getKeys(false)) {
            val base = "affixes.$id"
            val category = runCatching {
                AffixCategory.valueOf((yml.getString("$base.category") ?: "MOB").uppercase())
            }.getOrDefault(AffixCategory.MOB)
            val params = HashMap<String, Double>()
            yml.getConfigurationSection("$base.effect.params")?.let { section ->
                section.getKeys(false).forEach { key -> params[key] = section.getDouble(key) }
            }
            affixes[id] = AffixDefinition(
                id = id,
                name = yml.getString("$base.name") ?: id,
                lore = yml.getStringList("$base.lore"),
                category = category,
                danger = yml.getInt("$base.danger", 10),
                reward = yml.getInt("$base.reward", 10),
                minTier = yml.getInt("$base.min-tier", 1),
                maxTier = yml.getInt("$base.max-tier", 999),
                effect = AffixEffect(
                    yml.getString("$base.effect.type") ?: "",
                    params,
                    yml.getStringList("$base.effect.commands"),
                    yml.getString("$base.effect.script")?.takeIf { it.isNotBlank() },
                    yml.getString("$base.effect.message")?.takeIf { it.isNotBlank() },
                ),
                group = yml.getString("$base.group")?.takeIf { it.isNotBlank() },
            )
        }
        return affixes.size
    }

    fun get(id: String): AffixDefinition? = affixes[id]

    fun all(): Collection<AffixDefinition> = affixes.values

    fun eligible(tier: Int): List<AffixDefinition> =
        affixes.values.filter { tier in it.minTier..it.maxTier }
}
