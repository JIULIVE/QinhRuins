package com.qinhuai.ruins.affix

import com.qinhuai.corelib.expression.ExpressionEngine
import org.bukkit.configuration.ConfigurationSection

object TierConfig {

    var maxTier: Int = 16
        private set

    private var affixCountExpr = "1 + tier / 3"
    private var mobLevelExpr = "tier * 5"
    private var lootBonusExpr = "1 + tier * 0.1"
    private var dangerBudgetExpr = "40 + tier * 10"

    fun load(section: ConfigurationSection?) {
        if (section == null) return
        maxTier = section.getInt("max", 16).coerceAtLeast(1)
        affixCountExpr = section.getString("affix-count") ?: affixCountExpr
        mobLevelExpr = section.getString("mob-level") ?: mobLevelExpr
        lootBonusExpr = section.getString("loot-bonus") ?: lootBonusExpr
        dangerBudgetExpr = section.getString("danger-budget") ?: dangerBudgetExpr
    }

    fun affixCount(tier: Int): Int = eval(affixCountExpr, tier).toInt().coerceIn(0, 32)

    fun mobLevel(tier: Int): Int = eval(mobLevelExpr, tier).toInt().coerceAtLeast(1)

    fun lootBonus(tier: Int): Double = eval(lootBonusExpr, tier).coerceAtLeast(0.0)

    fun dangerBudget(tier: Int): Int = eval(dangerBudgetExpr, tier).toInt()

    private fun eval(expression: String, tier: Int): Double =
        runCatching { ExpressionEngine.evaluate(expression, mapOf("tier" to tier.toDouble())) }.getOrDefault(0.0)
}
