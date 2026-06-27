package com.qinhuai.ruins.affix

import com.qinhuai.corelib.util.ServerCompat
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object AffixRuntime {

    fun runScripts(affixes: List<AffixDefinition>, player: Player, plugin: Plugin, tier: Int) {
        for (affix in affixes) {
            if (affix.effect.type != "script") continue
            val ref = affix.effect.script ?: continue
            RuinScriptBridge.execute(
                ref,
                player,
                plugin,
                mapOf("tier" to tier, "affix" to affix.id, "danger" to affix.danger),
            )
        }
    }

    fun runCommands(affixes: List<AffixDefinition>, player: Player, tier: Int) {
        for (affix in affixes) {
            if (affix.effect.type != "command") continue
            for (raw in affix.effect.commands) {
                val command = raw
                    .replace("{player}", player.name)
                    .replace("{tier}", tier.toString())
                    .removePrefix("/")
                if (command.isNotBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                }
            }
        }
    }

    fun mobLevelBonus(affixes: List<AffixDefinition>): Int =
        affixes.filter { it.effect.type == "mob-level-bonus" }.sumOf { it.effect.param("bonus").toInt() }

    fun mobCountMultiplier(affixes: List<AffixDefinition>): Double =
        affixes.filter { it.effect.type == "mob-count-mult" }
            .fold(1.0) { acc, affix -> acc * affix.effect.param("mult", 1.0) }

    fun timeLimitSeconds(affixes: List<AffixDefinition>): Long =
        affixes.filter { it.effect.type == "time-limit" }
            .minOfOrNull { it.effect.param("seconds").toLong() } ?: 0L

    fun hasNoHeal(affixes: List<AffixDefinition>): Boolean =
        affixes.any { it.effect.type == "no-heal" }

    fun lootQuantityMultiplier(affixes: List<AffixDefinition>): Double =
        affixes.filter { it.effect.type == "loot-quantity-mult" }
            .fold(1.0) { acc, affix -> acc * affix.effect.param("mult", 1.0) }

    fun uniqueChanceMultiplier(affixes: List<AffixDefinition>): Double =
        affixes.filter { it.effect.type == "unique-chance-mult" }
            .fold(1.0) { acc, affix -> acc * affix.effect.param("mult", 1.0) }

    fun currencyMultiplier(affixes: List<AffixDefinition>): Double =
        affixes.filter { it.effect.type == "currency-mult" }
            .fold(1.0) { acc, affix -> acc * affix.effect.param("mult", 1.0) }

    fun broadcastMessages(affixes: List<AffixDefinition>, ruinName: String, tier: Int): List<String> =
        affixes.filter { it.effect.type == "broadcast" }
            .map { (it.effect.message ?: it.name).replace("{ruin}", ruinName).replace("{tier}", tier.toString()) }

    fun applyPlayerStatus(affixes: List<AffixDefinition>, player: Player, durationTicks: Int) {
        for (affix in affixes) {
            if (affix.effect.type != "player-status") continue
            for ((key, amplifier) in affix.effect.params) {
                val type = potionType(key) ?: continue
                player.addPotionEffect(PotionEffect(type, durationTicks, amplifier.toInt(), true, false, true))
            }
        }
    }

    private fun potionType(name: String): PotionEffectType? =
        ServerCompat.resolvePotionEffectType(name)
}
