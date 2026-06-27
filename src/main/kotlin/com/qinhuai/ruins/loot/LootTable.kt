package com.qinhuai.ruins.loot

data class LootEntry(
    val item: String,
    val weight: Int,
    val minAmount: Int,
    val maxAmount: Int,
    val minGrowth: Double,
    val unique: Boolean = false,
)

data class LootGroup(
    val condition: String?,
    val rolls: Int,
    val entries: List<LootEntry>,
)

data class LootTable(
    val rolls: Int,
    val entries: List<LootEntry>,
    val groups: List<LootGroup> = emptyList(),
    val containers: List<String> = emptyList(),
    val vanilla: String? = null,
)
