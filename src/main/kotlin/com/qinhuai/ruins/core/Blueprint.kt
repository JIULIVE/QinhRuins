package com.qinhuai.ruins.core

data class RelPos(val x: Int, val y: Int, val z: Int)

data class SpawnPoint(
    val id: String,
    val pos: RelPos,
    val mob: String,
    val count: Int,
    val level: Int,
    val stage: Int,
)

data class LootChest(
    val id: String,
    val pos: RelPos,
    val lootTable: String,
    val perPlayerOnce: Boolean,
    val growthScaled: Boolean,
    val unlockStage: Int,
)

data class SpawnCommand(val pos: RelPos, val command: String)

data class VariantOption(val templateId: String, val weight: Int)

data class VariantSlot(
    val id: String,
    val pos: RelPos,
    val options: List<VariantOption>,
    val surface: Boolean = false,
    val yMin: Int = 0,
    val yMax: Int = 0,
)

data class BossGate(
    val requiredKills: Int,
    val countMobs: Set<String>,
    val announce: Boolean,
    val boss: SpawnPoint,
)

data class KillRequirement(val mobKey: String, val amount: Int)

data class ObjectiveStage(val stage: Int, val name: String, val kills: List<KillRequirement>)

data class Objectives(val stages: List<ObjectiveStage>) {
    fun isEmpty(): Boolean = stages.isEmpty()
    fun isNotEmpty(): Boolean = stages.isNotEmpty()
    fun stage(n: Int): ObjectiveStage? = stages.firstOrNull { it.stage == n }
    val maxStage: Int get() = stages.maxOfOrNull { it.stage } ?: 0
}

data class Blueprint(
    val spawnPoints: List<SpawnPoint>,
    val lootChests: List<LootChest>,
    val objectives: Objectives,
    val mechanisms: List<Mechanism> = emptyList(),
    val cores: List<RelPos> = emptyList(),
    val variants: List<VariantSlot> = emptyList(),
    val spawnCommands: List<SpawnCommand> = emptyList(),
    val bossGate: BossGate? = null,
) {
    fun isEmpty(): Boolean = spawnPoints.isEmpty() && lootChests.isEmpty() && mechanisms.isEmpty()
}
