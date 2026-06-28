package com.qinhuai.ruins.core

enum class Side {
    NORTH, SOUTH, EAST, WEST, UP, DOWN;

    fun opposite(): Side = when (this) {
        NORTH -> SOUTH
        SOUTH -> NORTH
        EAST -> WEST
        WEST -> EAST
        UP -> DOWN
        DOWN -> UP
    }
}

data class Connector(val side: Side, val pos: RelPos)

data class TileDef(
    val templateId: String,
    val size: RelPos,
    val connectors: List<Connector>,
    val weight: Int,
    val maxCount: Int,
    val role: String,
    val repetitionPenalty: Int = 0,
    val noRepeat: Boolean = false,
)

data class TilePalette(
    val id: String,
    val maxRooms: Int,
    val startTile: String?,
    val tiles: List<TileDef>,
) {
    fun tile(templateId: String): TileDef? = tiles.firstOrNull { it.templateId == templateId }
}
