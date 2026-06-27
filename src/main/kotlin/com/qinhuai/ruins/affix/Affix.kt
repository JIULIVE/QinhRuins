package com.qinhuai.ruins.affix

enum class AffixCategory { MOB, PLAYER, ENV, LOOT }

data class AffixEffect(
    val type: String,
    val params: Map<String, Double>,
    val commands: List<String> = emptyList(),
    val script: String? = null,
    val message: String? = null,
) {
    fun param(key: String, default: Double = 0.0): Double = params[key] ?: default
}

data class AffixDefinition(
    val id: String,
    val name: String,
    val lore: List<String>,
    val category: AffixCategory,
    val danger: Int,
    val reward: Int,
    val minTier: Int,
    val maxTier: Int,
    val effect: AffixEffect,
    val group: String? = null,
)
