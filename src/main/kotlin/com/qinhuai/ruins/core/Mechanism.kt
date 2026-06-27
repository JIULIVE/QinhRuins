package com.qinhuai.ruins.core

enum class MechTriggerType { REGION_ENTER, INTERACT, TIMER, STAGE, BLOCK_BREAK, REDSTONE }

enum class MechActionType { FILL, SPAWN, MESSAGE, TITLE, SOUND, EFFECT, COMMAND, LOOT, TELEPORT, PARTICLE, GIVE, NPC }

data class MechRegion(val from: RelPos, val to: RelPos)

data class MechTrigger(
    val type: MechTriggerType,
    val pos: RelPos? = null,
    val region: MechRegion? = null,
    val intervalSeconds: Long = 0L,
    val stage: Int = 0,
)

data class MechAction(
    val type: MechActionType,
    val params: Map<String, String> = emptyMap(),
    val region: MechRegion? = null,
    val pos: RelPos? = null,
) {
    fun param(key: String, default: String = ""): String = params[key] ?: default
    fun intParam(key: String, default: Int): Int = params[key]?.toIntOrNull() ?: default
}

data class Mechanism(
    val id: String,
    val trigger: MechTrigger,
    val actions: List<MechAction>,
    val once: Boolean = false,
    val cooldownSeconds: Long = 0L,
    val requireStage: Int = 0,
    val radius: Double = 24.0,
)
