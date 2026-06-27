package com.qinhuai.ruins.loot

import com.qinhuai.corelib.api.item.ItemManagerAPI
import com.qinhuai.ruins.integration.GrowthProviders
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import java.util.concurrent.ThreadLocalRandom

object LootService {

    fun roll(
        table: LootTable,
        player: Player,
        growthScaled: Boolean,
        quantityMult: Double = 1.0,
        uniqueWeightMult: Double = 1.0,
        ignoreGrowth: Boolean = false,
    ): List<ItemStack> {
        val growth = if (ignoreGrowth) Double.MAX_VALUE else GrowthProviders.active().getGrowth(player)
        val scaled = growthScaled && !ignoreGrowth
        val result = ArrayList<ItemStack>()
        table.vanilla?.let { result += rollVanilla(it, player) }
        if (table.entries.isNotEmpty()) {
            result += rollEntries(table.entries, table.rolls, player, growth, scaled, quantityMult, uniqueWeightMult)
        }
        for (group in table.groups) {
            if (!LootCondition.eval(player, group.condition)) continue
            result += rollEntries(group.entries, group.rolls, player, growth, scaled, quantityMult, uniqueWeightMult)
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun rollVanilla(ref: String, player: Player): List<ItemStack> = runCatching {
        val key = NamespacedKey.fromString(ref.trim().lowercase()) ?: return emptyList()
        val table = Bukkit.getLootTable(key) ?: return emptyList()
        val context = LootContext.Builder(player.location).build()
        table.populateLoot(ThreadLocalRandom.current(), context).toList()
    }.getOrDefault(emptyList())

    private fun rollEntries(
        entries: List<LootEntry>,
        baseRolls: Int,
        player: Player,
        growth: Double,
        growthScaled: Boolean,
        quantityMult: Double,
        uniqueWeightMult: Double,
    ): List<ItemStack> {
        val eligible = entries.filter { growth >= it.minGrowth }
        if (eligible.isEmpty()) return emptyList()
        val weights = eligible.map { weightOf(it, uniqueWeightMult) }
        val totalWeight = weights.sum()
        if (totalWeight <= 0) return emptyList()

        val rolls = (baseRolls * quantityMult).toInt().coerceAtLeast(baseRolls)
        val result = ArrayList<ItemStack>()
        repeat(rolls) {
            val entry = weightedPick(eligible, weights, totalWeight) ?: return@repeat
            val base = randomBetween(entry.minAmount, entry.maxAmount)
            val amount = if (growthScaled) GrowthScaler.scaleAmount(base, growth) else base
            val item = ItemManagerAPI.getHookItem(entry.item, player) ?: return@repeat
            item.amount = amount.coerceIn(1, item.maxStackSize)
            result.add(item)
        }
        return result
    }

    fun pool(table: LootTable, player: Player): List<LootEntry> {
        val growth = GrowthProviders.active().getGrowth(player)
        val result = ArrayList<LootEntry>()
        result += table.entries.filter { growth >= it.minGrowth }
        for (group in table.groups) {
            if (LootCondition.eval(player, group.condition)) {
                result += group.entries.filter { growth >= it.minGrowth }
            }
        }
        return result
    }

    fun pickOne(player: Player, entries: List<LootEntry>, growthScaled: Boolean): ItemStack? {
        if (entries.isEmpty()) return null
        val weights = entries.map { weightOf(it, 1.0) }
        val total = weights.sum()
        if (total <= 0) return null
        val entry = weightedPick(entries, weights, total) ?: return null
        return build(entry, player, growthScaled)
    }

    fun build(entry: LootEntry, player: Player, growthScaled: Boolean): ItemStack? {
        val growth = GrowthProviders.active().getGrowth(player)
        val base = randomBetween(entry.minAmount, entry.maxAmount)
        val amount = if (growthScaled) GrowthScaler.scaleAmount(base, growth) else base
        val item = ItemManagerAPI.getHookItem(entry.item, player) ?: return null
        item.amount = amount.coerceIn(1, item.maxStackSize)
        return item
    }

    private fun weightOf(entry: LootEntry, uniqueWeightMult: Double): Int =
        if (entry.unique) (entry.weight * uniqueWeightMult).toInt().coerceAtLeast(1) else entry.weight

    private fun weightedPick(entries: List<LootEntry>, weights: List<Int>, totalWeight: Int): LootEntry? {
        var roll = ThreadLocalRandom.current().nextInt(totalWeight)
        for (i in entries.indices) {
            roll -= weights[i]
            if (roll < 0) return entries[i]
        }
        return entries.lastOrNull()
    }

    private fun randomBetween(min: Int, max: Int): Int {
        if (max <= min) return min
        return ThreadLocalRandom.current().nextInt(min, max + 1)
    }
}
