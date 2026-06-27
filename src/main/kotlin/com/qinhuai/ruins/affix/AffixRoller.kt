package com.qinhuai.ruins.affix

import java.util.Random

object AffixRoller {

    private val random = Random()

    fun roll(tier: Int): List<AffixDefinition> {
        val count = TierConfig.affixCount(tier)
        if (count <= 0) return emptyList()
        val budget = TierConfig.dangerBudget(tier)
        val pool = AffixRegistry.eligible(tier).toMutableList()
        val result = ArrayList<AffixDefinition>()
        var spent = 0
        repeat(count) {
            if (pool.isEmpty()) return@repeat
            val affordable = if (budget <= 0 || result.isEmpty()) pool
            else pool.filter { spent + it.danger <= budget }
            if (affordable.isEmpty()) return@repeat
            val picked = weightedPick(affordable) ?: return@repeat
            result.add(picked)
            spent += picked.danger
            pool.remove(picked)
            picked.group?.let { group -> pool.removeAll { it.group == group } }
        }
        return result
    }

    private fun weightedPick(pool: List<AffixDefinition>): AffixDefinition? {
        val total = pool.sumOf { (it.danger + it.reward).coerceAtLeast(1) }
        if (total <= 0) return pool.firstOrNull()
        var roll = random.nextInt(total)
        for (affix in pool) {
            roll -= (affix.danger + affix.reward).coerceAtLeast(1)
            if (roll < 0) return affix
        }
        return pool.lastOrNull()
    }
}
