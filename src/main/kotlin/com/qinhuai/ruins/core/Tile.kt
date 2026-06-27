package com.qinhuai.ruins.core

enum class Side {
    NORTH, SOUTH, EAST, WEST;

    fun opposite(): Side = when (this) {
        NORTH -> SOUTH
        SOUTH -> NORTH
        EAST -> WEST
        WEST -> EAST
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
)

data class TilePalette(
    val id: String,
    val maxRooms: Int,
    val startTile: String?,
    val tiles: List<TileDef>,
) {
    fun tile(templateId: String): TileDef? = tiles.firstOrNull { it.templateId == templateId }
}
