package com.qinhuai.ruins.loot

import com.qinhuai.corelib.economy.EconomyBridge
import com.qinhuai.corelib.expression.ExpressionEngine
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.affix.AffixDefinition
import com.qinhuai.ruins.affix.AffixRuntime
import com.qinhuai.ruins.affix.TierConfig
import com.qinhuai.ruins.lang.Lang
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

object RealmLoot {

    private var tableName = "realm"
    private var perTierTable = false
    private var growthScaled = true
    private var currencyExpr = "tier * 100"
    private var currencyProvider: String? = null
    private var currencyId: String? = null

    fun load(section: ConfigurationSection?) {
        if (section == null) return
        tableName = section.getString("loot-table") ?: tableName
        perTierTable = section.getBoolean("per-tier-table", false)
        growthScaled = section.getBoolean("growth-scaled", true)
        currencyExpr = section.getString("currency-base") ?: currencyExpr
        currencyProvider = section.getString("currency-provider")?.takeIf { it.isNotBlank() }
        currencyId = section.getString("currency-id")?.takeIf { it.isNotBlank() }
    }

    fun grant(player: Player, tier: Int, affixes: List<AffixDefinition>) {
        grantItems(player, tier, affixes)
        grantCurrency(player, tier, affixes)
    }

    private fun grantItems(player: Player, tier: Int, affixes: List<AffixDefinition>) {
        val table = resolveTable(tier) ?: return
        val quantityMult = AffixRuntime.lootQuantityMultiplier(affixes) * TierConfig.lootBonus(tier)
        val uniqueMult = AffixRuntime.uniqueChanceMultiplier(affixes)
        val items = LootService.roll(table, player, growthScaled, quantityMult, uniqueMult)
        if (items.isEmpty()) return
        val overflow = player.inventory.addItem(*items.toTypedArray())
        overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }
        TextUtil.sendColored(player, Lang.get("realm.loot-granted", "count" to items.size))
    }

    private fun grantCurrency(player: Player, tier: Int, affixes: List<AffixDefinition>) {
        if (!EconomyBridge.isAvailable()) return
        val base = runCatching {
            ExpressionEngine.evaluate(currencyExpr, mapOf("tier" to tier.toDouble()))
        }.getOrDefault(0.0)
        val amount = base * AffixRuntime.currencyMultiplier(affixes)
        if (amount <= 0.0) return
        val result = EconomyBridge.deposit(player, amount, currencyProvider, currencyId)
        if (result.success) {
            TextUtil.sendColored(player, Lang.get("realm.currency-granted", "amount" to amount.toLong()))
        }
    }

    private fun resolveTable(tier: Int): LootTable? {
        if (perTierTable) {
            LootTableRegistry.get("${tableName}_t$tier")?.let { return it }
        }
        return LootTableRegistry.get(tableName)
    }
}
