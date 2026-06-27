package com.qinhuai.ruins.structure

import org.bukkit.block.structure.StructureRotation
import java.util.Random

object Rotations {

    private val ALL = arrayOf(
        StructureRotation.NONE,
        StructureRotation.CLOCKWISE_90,
        StructureRotation.CLOCKWISE_180,
        StructureRotation.COUNTERCLOCKWISE_90,
    )

    fun resolve(mode: String, random: Random): StructureRotation {
        val m = mode.trim().lowercase()
        return when {
            m == "random" -> ALL[random.nextInt(ALL.size)]
            m.startsWith("fixed:") -> fromDegrees(m.removePrefix("fixed:").trim())
            else -> StructureRotation.NONE
        }
    }

    fun fromDegrees(deg: String): StructureRotation = when (deg.toIntOrNull() ?: 0) {
        90 -> StructureRotation.CLOCKWISE_90
        180 -> StructureRotation.CLOCKWISE_180
        270 -> StructureRotation.COUNTERCLOCKWISE_90
        else -> StructureRotation.NONE
    }

    fun parse(name: String?): StructureRotation =
        runCatching { StructureRotation.valueOf(name ?: "NONE") }.getOrDefault(StructureRotation.NONE)
}
