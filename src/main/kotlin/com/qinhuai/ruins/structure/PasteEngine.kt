package com.qinhuai.ruins.structure

import com.qinhuai.ruins.core.RuinTemplate
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.block.structure.StructureRotation
import org.bukkit.util.BlockVector
import java.io.File

data class PasteResult(
    val success: Boolean,
    val message: String = "",
    val size: BlockVector? = null,
)

interface PasteEngine {
    val id: String
    fun isAvailable(): Boolean
    fun paste(template: RuinTemplate, origin: Location, rotation: StructureRotation, onComplete: () -> Unit = {}): PasteResult
    fun capture(file: File, origin: Location, size: BlockVector, includeEntities: Boolean): PasteResult
    fun structureSize(template: RuinTemplate): BlockVector?
    fun placeFile(file: File, origin: Location): PasteResult

    fun waterlineY(template: RuinTemplate): Int = -1

    fun previewBlocks(template: RuinTemplate, limit: Int): List<Pair<BlockVector, BlockData>>? = null

    fun clearCache() {}
}
