package com.qinhuai.ruins.generation

import org.bukkit.Location

class SpatialIndex(private val bucketSize: Int) {

    private val worlds = HashMap<String, HashMap<Long, MutableSet<String>>>()

    private fun cellKey(bx: Int, bz: Int): Long =
        (bx.toLong() shl 32) xor (bz.toLong() and 0xffffffffL)

    private fun worldName(loc: Location): String = loc.world?.name ?: "world"

    fun add(id: String, loc: Location) {
        val cells = worlds.getOrPut(worldName(loc)) { HashMap() }
        val key = cellKey(Math.floorDiv(loc.blockX, bucketSize), Math.floorDiv(loc.blockZ, bucketSize))
        cells.getOrPut(key) { HashSet() }.add(id)
    }

    fun remove(id: String, loc: Location) {
        val cells = worlds[worldName(loc)] ?: return
        val key = cellKey(Math.floorDiv(loc.blockX, bucketSize), Math.floorDiv(loc.blockZ, bucketSize))
        cells[key]?.let {
            it.remove(id)
            if (it.isEmpty()) cells.remove(key)
        }
    }

    fun nearby(loc: Location, radius: Double): Set<String> {
        val cells = worlds[worldName(loc)] ?: return emptySet()
        val span = Math.floorDiv(radius.toInt(), bucketSize) + 1
        val cx = Math.floorDiv(loc.blockX, bucketSize)
        val cz = Math.floorDiv(loc.blockZ, bucketSize)
        val result = HashSet<String>()
        for (dx in -span..span) {
            for (dz in -span..span) {
                cells[cellKey(cx + dx, cz + dz)]?.let { result.addAll(it) }
            }
        }
        return result
    }

    fun clear() = worlds.clear()
}
