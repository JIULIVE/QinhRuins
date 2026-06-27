package com.qinhuai.ruins.core

import org.bukkit.Location
import org.bukkit.block.structure.StructureRotation

enum class AnchorState { DORMANT, ACTIVE, CLEARED, RECYCLED }

data class Anchor(
    val id: String,
    val templateId: String,
    val location: Location,
    var state: AnchorState,
    val rotation: StructureRotation,
    val width: Int = 0,
    val height: Int = 0,
    val depth: Int = 0,
    var clearedAt: Long = 0L,
) {
    fun center(): Location {
        val w = if (width > 0) width else 1
        val h = if (height > 0) height else 1
        val d = if (depth > 0) depth else 1
        return location.clone().add(w / 2.0, h / 2.0, d / 2.0)
    }

    fun contains(loc: Location): Boolean {
        if (width <= 0 || depth <= 0) return false
        if (loc.world != location.world) return false
        return loc.blockX >= location.blockX && loc.blockX < location.blockX + width &&
            loc.blockZ >= location.blockZ && loc.blockZ < location.blockZ + depth &&
            (height <= 0 || (loc.blockY >= location.blockY && loc.blockY < location.blockY + height))
    }
}
