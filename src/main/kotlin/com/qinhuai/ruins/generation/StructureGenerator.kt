package com.qinhuai.ruins.generation

import com.qinhuai.ruins.core.RelPos
import com.qinhuai.ruins.core.TilePalette
import com.qinhuai.ruins.structure.PasteEngines
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Location
import org.bukkit.block.structure.StructureRotation
import org.bukkit.util.BlockVector

object StructureGenerator {

    data class Result(val pasted: Int, val planned: Int, val plan: List<TilePlanner.Placed>)

    data class Footprint(val min: RelPos, val size: BlockVector)

    fun plan(palette: TilePalette, seed: Long): List<TilePlanner.Placed> = TilePlanner.plan(palette, seed)

    fun footprint(plan: List<TilePlanner.Placed>): Footprint {
        val minX = plan.minOf { it.offset.x }
        val minY = plan.minOf { it.offset.y }
        val minZ = plan.minOf { it.offset.z }
        val maxX = plan.maxOf { it.offset.x + it.def.size.x }
        val maxY = plan.maxOf { it.offset.y + it.def.size.y }
        val maxZ = plan.maxOf { it.offset.z + it.def.size.z }
        return Footprint(RelPos(minX, minY, minZ), BlockVector(maxX - minX, maxY - minY, maxZ - minZ))
    }

    fun generate(palette: TilePalette, base: Location, seed: Long): Result {
        val plan = TilePlanner.plan(palette, seed)
        var pasted = 0
        for (tile in plan) {
            val template = TemplateRegistry.get(tile.def.templateId) ?: continue
            val origin = base.clone().add(tile.offset.x.toDouble(), tile.offset.y.toDouble(), tile.offset.z.toDouble())
            val result = PasteEngines.active().paste(template, origin, StructureRotation.NONE)
            if (result.success) pasted++
        }
        return Result(pasted, plan.size, plan)
    }
}
