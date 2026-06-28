package com.qinhuai.ruins.generation

import com.qinhuai.ruins.core.Connector
import com.qinhuai.ruins.core.RelPos
import com.qinhuai.ruins.core.Side
import com.qinhuai.ruins.core.TileDef
import com.qinhuai.ruins.core.TilePalette
import java.util.Random
import kotlin.math.ln

object TilePlanner {

    data class Placed(val def: TileDef, val offset: RelPos)

    private data class OpenConn(val side: Side, val worldPos: RelPos)

    private data class Option(val def: TileDef, val mc: Connector, val offset: RelPos)

    private class Frame(
        val open: OpenConn,
        val options: List<Option>,
        val afterPop: List<OpenConn>,
        var cursor: Int,
        var applied: Boolean,
    )

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

        var best = ArrayList(placed)
        val stack = ArrayDeque<Frame>()
        var stepBudget = (palette.maxRooms * 40).coerceIn(200, 50_000)
        var backtrackBudget = (palette.maxRooms * 4).coerceIn(16, 4_000)

        while (placed.size < palette.maxRooms && stepBudget-- > 0) {
            if (frontier.isNotEmpty()) {
                val open = popRandom(frontier, random)
                val afterPop = ArrayList(frontier)
                val options = buildOptions(palette, open, placed, counts, random)
                if (options.isEmpty()) {
                    stack.addLast(Frame(open, options, afterPop, 0, false))
                    continue
                }
                applyOption(options[0], placed, counts, frontier)
                stack.addLast(Frame(open, options, afterPop, 0, true))
                if (placed.size > best.size) best = ArrayList(placed)
            } else {
                if (backtrackBudget-- <= 0 || !backtrackOnce(stack, placed, counts, frontier)) break
                if (placed.size > best.size) best = ArrayList(placed)
            }
        }

        val bestCounts = HashMap<String, Int>()
        best.forEach { bestCounts.merge(it.def.templateId, 1) { a, b -> a + b } }
        capOpenConnectors(palette, best, bestCounts, random)
        return best
    }

    private fun applyOption(opt: Option, placed: MutableList<Placed>, counts: MutableMap<String, Int>, frontier: ArrayDeque<OpenConn>) {
        placed.add(Placed(opt.def, opt.offset))
        counts.merge(opt.def.templateId, 1) { a, b -> a + b }
        for (c in opt.def.connectors) {
            if (c != opt.mc) frontier.addLast(OpenConn(c.side, worldConn(opt.offset, c)))
        }
    }

    private fun backtrackOnce(
        stack: ArrayDeque<Frame>,
        placed: MutableList<Placed>,
        counts: MutableMap<String, Int>,
        frontier: ArrayDeque<OpenConn>,
    ): Boolean {
        while (stack.isNotEmpty()) {
            val f = stack.last()
            if (f.applied) {
                placed.removeAt(placed.lastIndex)
                counts.merge(f.options[f.cursor].def.templateId, -1) { a, b -> a + b }
                f.applied = false
            }
            if (f.cursor + 1 < f.options.size) {
                f.cursor++
                frontier.clear()
                frontier.addAll(f.afterPop)
                applyOption(f.options[f.cursor], placed, counts, frontier)
                f.applied = true
                return true
            }
            stack.removeLast()
        }
        return false
    }

    private fun buildOptions(
        palette: TilePalette,
        open: OpenConn,
        placed: List<Placed>,
        counts: Map<String, Int>,
        random: Random,
    ): List<Option> {
        val needed = open.side.opposite()
        val options = ArrayList<Option>()
        for (def in palette.tiles) {
            if ((counts[def.templateId] ?: 0) >= def.maxCount) continue
            for (mc in def.connectors) {
                if (mc.side != needed) continue
                val offset = computeOffset(open, def, mc)
                if (overlaps(offset, def.size, placed)) continue
                if (def.noRepeat && adjacentSameCount(def.templateId, offset, def.size, placed) > 0) continue
                options.add(Option(def, mc, offset))
            }
        }
        return weightedOrder(options, placed, random)
    }

    private fun weightedOrder(options: List<Option>, placed: List<Placed>, random: Random): List<Option> {
        if (options.size <= 1) return options
        return options
            .map { opt ->
                val penalty = if (opt.def.repetitionPenalty != 0)
                    opt.def.repetitionPenalty * adjacentSameCount(opt.def.templateId, opt.offset, opt.def.size, placed)
                else 0
                val w = (opt.def.weight + penalty).coerceAtLeast(1)
                val key = -ln(random.nextDouble().coerceIn(1e-12, 1.0)) / w
                opt to key
            }
            .sortedBy { it.second }
            .map { it.first }
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
                if (cap.noRepeat && adjacentSameCount(cap.templateId, offset, cap.size, placed) > 0) continue
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
        Side.UP -> RelPos(p.x, p.y + 1, p.z)
        Side.DOWN -> RelPos(p.x, p.y - 1, p.z)
    }

    private fun contains(tile: Placed, p: RelPos): Boolean =
        p.x >= tile.offset.x && p.x < tile.offset.x + tile.def.size.x &&
            p.y >= tile.offset.y && p.y < tile.offset.y + tile.def.size.y &&
            p.z >= tile.offset.z && p.z < tile.offset.z + tile.def.size.z

    private fun computeOffset(open: OpenConn, def: TileDef, mc: Connector): RelPos {
        val p = open.worldPos
        val s = def.size
        return when (open.side) {
            Side.NORTH -> RelPos(p.x - mc.pos.x, p.y - mc.pos.y, p.z - s.z)
            Side.SOUTH -> RelPos(p.x - mc.pos.x, p.y - mc.pos.y, p.z + 1)
            Side.EAST -> RelPos(p.x + 1, p.y - mc.pos.y, p.z - mc.pos.z)
            Side.WEST -> RelPos(p.x - s.x, p.y - mc.pos.y, p.z - mc.pos.z)
            Side.UP -> RelPos(p.x - mc.pos.x, p.y + 1, p.z - mc.pos.z)
            Side.DOWN -> RelPos(p.x - mc.pos.x, p.y - s.y, p.z - mc.pos.z)
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

    private fun adjacentSameCount(templateId: String, offset: RelPos, size: RelPos, placed: List<Placed>): Int {
        val ax1 = offset.x - 1
        val ax2 = offset.x + size.x + 1
        val ay1 = offset.y - 1
        val ay2 = offset.y + size.y + 1
        val az1 = offset.z - 1
        val az2 = offset.z + size.z + 1
        var count = 0
        for (other in placed) {
            if (other.def.templateId != templateId) continue
            val bx1 = other.offset.x
            val bx2 = other.offset.x + other.def.size.x
            val by1 = other.offset.y
            val by2 = other.offset.y + other.def.size.y
            val bz1 = other.offset.z
            val bz2 = other.offset.z + other.def.size.z
            if (ax1 < bx2 && ax2 > bx1 && ay1 < by2 && ay2 > by1 && az1 < bz2 && az2 > bz1) count++
        }
        return count
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
