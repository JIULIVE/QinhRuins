package com.qinhuai.ruins.generation

import com.qinhuai.ruins.core.Connector
import com.qinhuai.ruins.core.RelPos
import com.qinhuai.ruins.core.Side
import com.qinhuai.ruins.core.TileDef
import com.qinhuai.ruins.core.TilePalette
import java.util.Random

object TilePlanner {

    data class Placed(val def: TileDef, val offset: RelPos)

    private data class OpenConn(val side: Side, val worldPos: RelPos)

    fun plan(palette: TilePalette, seed: Long): List<Placed> {
        val random = Random(seed)
        val start = palette.startTile?.let { palette.tile(it) }
            ?: weighted(palette.tiles, random)
            ?: return emptyList()

        val placed = ArrayList<Placed>()
        val counts = HashMap<String, Int>()
        placeTile(placed, counts, start, RelPos(0, 0, 0))

        val frontier = ArrayDeque<OpenConn>()
        start.connectors.forEach { frontier.add(OpenConn(it.side, worldConn(RelPos(0, 0, 0), it))) }

        while (placed.size < palette.maxRooms && frontier.isNotEmpty()) {
            val open = popRandom(frontier, random)
            attach(palette, open, placed, counts, frontier, random)
        }
        capOpenConnectors(palette, placed, counts, random)
        return placed
    }

    private fun capOpenConnectors(
        palette: TilePalette,
        placed: MutableList<Placed>,
        counts: MutableMap<String, Int>,
        random: Random,
    ) {
        val caps = palette.tiles.filter { it.role.equals("cap", true) && it.connectors.size == 1 }
        if (caps.isEmpty()) return
        for (open in openConnectors(placed)) {
            val needed = open.side.opposite()
            for (cap in caps.filter { (counts[it.templateId] ?: 0) < it.maxCount }.shuffled(random)) {
                val mc = cap.connectors.firstOrNull { it.side == needed } ?: continue
                val offset = computeOffset(open, cap, mc)
                if (overlaps(offset, cap.size, placed)) continue
                placeTile(placed, counts, cap, offset)
                break
            }
        }
    }

    private fun openConnectors(placed: List<Placed>): List<OpenConn> {
        val result = ArrayList<OpenConn>()
        for (tile in placed) {
            for (conn in tile.def.connectors) {
                val wp = worldConn(tile.offset, conn)
                val adj = adjacent(wp, conn.side)
                if (placed.none { contains(it, adj) }) result.add(OpenConn(conn.side, wp))
            }
        }
        return result
    }

    private fun adjacent(p: RelPos, side: Side): RelPos = when (side) {
        Side.NORTH -> RelPos(p.x, p.y, p.z - 1)
        Side.SOUTH -> RelPos(p.x, p.y, p.z + 1)
        Side.EAST -> RelPos(p.x + 1, p.y, p.z)
        Side.WEST -> RelPos(p.x - 1, p.y, p.z)
    }

    private fun contains(tile: Placed, p: RelPos): Boolean =
        p.x >= tile.offset.x && p.x < tile.offset.x + tile.def.size.x &&
            p.y >= tile.offset.y && p.y < tile.offset.y + tile.def.size.y &&
            p.z >= tile.offset.z && p.z < tile.offset.z + tile.def.size.z

    private fun attach(
        palette: TilePalette,
        open: OpenConn,
        placed: MutableList<Placed>,
        counts: MutableMap<String, Int>,
        frontier: ArrayDeque<OpenConn>,
        random: Random,
    ) {
        val needed = open.side.opposite()
        val candidates = palette.tiles
            .filter { (counts[it.templateId] ?: 0) < it.maxCount && it.connectors.any { c -> c.side == needed } }
            .shuffled(random)
        for (def in candidates) {
            val matches = def.connectors.filter { it.side == needed }.shuffled(random)
            for (mc in matches) {
                val offset = computeOffset(open, def, mc)
                if (overlaps(offset, def.size, placed)) continue
                placeTile(placed, counts, def, offset)
                def.connectors.filter { it != mc }.forEach {
                    frontier.add(OpenConn(it.side, worldConn(offset, it)))
                }
                return
            }
        }
    }

    private fun computeOffset(open: OpenConn, def: TileDef, mc: Connector): RelPos {
        val p = open.worldPos
        val s = def.size
        return when (open.side) {
            Side.NORTH -> RelPos(p.x - mc.pos.x, p.y - mc.pos.y, p.z - s.z)
            Side.SOUTH -> RelPos(p.x - mc.pos.x, p.y - mc.pos.y, p.z + 1)
            Side.EAST -> RelPos(p.x + 1, p.y - mc.pos.y, p.z - mc.pos.z)
            Side.WEST -> RelPos(p.x - s.x, p.y - mc.pos.y, p.z - mc.pos.z)
        }
    }

    private fun placeTile(placed: MutableList<Placed>, counts: MutableMap<String, Int>, def: TileDef, offset: RelPos) {
        placed.add(Placed(def, offset))
        counts[def.templateId] = (counts[def.templateId] ?: 0) + 1
    }

    private fun overlaps(offset: RelPos, size: RelPos, placed: List<Placed>): Boolean {
        val ax1 = offset.x
        val ax2 = offset.x + size.x
        val ay1 = offset.y
        val ay2 = offset.y + size.y
        val az1 = offset.z
        val az2 = offset.z + size.z
        for (other in placed) {
            val bx1 = other.offset.x
            val bx2 = other.offset.x + other.def.size.x
            val by1 = other.offset.y
            val by2 = other.offset.y + other.def.size.y
            val bz1 = other.offset.z
            val bz2 = other.offset.z + other.def.size.z
            if (ax1 < bx2 && ax2 > bx1 && ay1 < by2 && ay2 > by1 && az1 < bz2 && az2 > bz1) return true
        }
        return false
    }

    private fun worldConn(offset: RelPos, connector: Connector): RelPos =
        RelPos(offset.x + connector.pos.x, offset.y + connector.pos.y, offset.z + connector.pos.z)

    private fun popRandom(frontier: ArrayDeque<OpenConn>, random: Random): OpenConn {
        val index = random.nextInt(frontier.size)
        return frontier.removeAt(index)
    }

    private fun weighted(tiles: List<TileDef>, random: Random): TileDef? {
        if (tiles.isEmpty()) return null
        val total = tiles.sumOf { it.weight }
        if (total <= 0) return tiles.first()
        var roll = random.nextInt(total)
        for (tile in tiles) {
            roll -= tile.weight
            if (roll < 0) return tile
        }
        return tiles.last()
    }
}
