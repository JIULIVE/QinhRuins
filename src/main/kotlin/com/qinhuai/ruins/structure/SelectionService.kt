package com.qinhuai.ruins.structure

import org.bukkit.Location
import org.bukkit.util.BlockVector
import java.util.UUID

data class Selection(val origin: Location, val size: BlockVector)

object SelectionService {

    private val pos1 = HashMap<UUID, Location>()
    private val pos2 = HashMap<UUID, Location>()

    fun setPos1(player: UUID, loc: Location) {
        pos1[player] = loc.block.location
    }

    fun setPos2(player: UUID, loc: Location) {
        pos2[player] = loc.block.location
    }

    fun getPos1(player: UUID): Location? = pos1[player]

    fun getPos2(player: UUID): Location? = pos2[player]

    fun resolve(player: UUID): Selection? {
        val a = pos1[player] ?: return null
        val b = pos2[player] ?: return null
        if (a.world == null || b.world == null || a.world != b.world) return null
        val minX = minOf(a.blockX, b.blockX)
        val minY = minOf(a.blockY, b.blockY)
        val minZ = minOf(a.blockZ, b.blockZ)
        val maxX = maxOf(a.blockX, b.blockX)
        val maxY = maxOf(a.blockY, b.blockY)
        val maxZ = maxOf(a.blockZ, b.blockZ)
        val origin = Location(a.world, minX.toDouble(), minY.toDouble(), minZ.toDouble())
        val size = BlockVector(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1)
        return Selection(origin, size)
    }

    fun clear(player: UUID) {
        pos1.remove(player)
        pos2.remove(player)
    }
}
