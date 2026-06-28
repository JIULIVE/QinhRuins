package com.qinhuai.ruins.structure

import com.qinhuai.ruins.QinhRuins
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.util.Profiler
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.block.data.BlockData
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.structure.Structure
import org.bukkit.util.BlockVector
import java.io.File
import java.util.Random

class NativePasteEngine(
    private val templatesDir: File,
    private val spreadThreshold: Int = 30000,
    private val handleMarkers: Boolean = true,
    pasteMillisPerTick: Long = 8L,
) : PasteEngine {

    override val id: String = "native"

    private val pasteBudgetNanos = maxOf(1L, pasteMillisPerTick) * 1_000_000L

    private val waterlineCache = HashMap<String, Int>()
    private val markerCache = HashMap<String, Pair<Boolean, Boolean>>()
    private val NO_MARKERS = false to false

    override fun isAvailable(): Boolean = true

    override fun clearCache() {
        waterlineCache.clear()
        markerCache.clear()
    }

    private fun markersOf(template: RuinTemplate, structure: Structure): Pair<Boolean, Boolean> {
        markerCache[template.id]?.let { return it }
        var bar = false
        var bed = false
        outer@ for (palette in structure.palettes) {
            for (block in palette.blocks) {
                when (block.type) {
                    Material.BARRIER -> bar = true
                    Material.BEDROCK -> bed = true
                    else -> {}
                }
                if (bar && bed) break@outer
            }
        }
        val result = bar to bed
        markerCache[template.id] = result
        return result
    }

    override fun paste(template: RuinTemplate, origin: Location, rotation: StructureRotation, onComplete: () -> Unit): PasteResult {
        val file = File(templatesDir, "${template.id}/${template.structureFile}")
        if (!file.exists()) return PasteResult(false, Lang.get("structure.file-not-found", "name" to file.name))
        if (template.targetMask.isNotEmpty() || template.sourceSkip.isNotEmpty() || template.sourceMask.isNotEmpty()) {
            val result = maskedPlace(file, origin, template.targetMask, template.sourceSkip, template.sourceMask)
            if (result.success) onComplete()
            return result
        }
        return try {
            val structure = Bukkit.getStructureManager().loadStructure(file)
            val markers = if (handleMarkers) markersOf(template, structure) else NO_MARKERS
            if (rotation == StructureRotation.NONE) {
                val count = structure.palettes.sumOf { it.blocks.size }
                if (count > spreadThreshold) return spreadPlace(structure, origin, markers, onComplete)
            }
            val world = origin.world ?: return PasteResult(false, Lang.get("structure.world-null"))
            val size = structure.size
            val rotated = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90
            val w = if (rotated) size.blockZ else size.blockX
            val h = size.blockY
            val l = if (rotated) size.blockX else size.blockZ
            val originals = if (markers.second) captureOriginals(world, origin, w, h, l) else null
            Profiler.time("paste.place") { structure.place(origin, false, rotation, Mirror.NONE, 0, 1.0f, Random()) }
            if (markers.first || markers.second) runMarkerCleanup(world, origin, w, h, l, markers.first, markers.second, originals)
            onComplete()
            PasteResult(true, size = size)
        } catch (e: Exception) {
            PasteResult(false, Lang.get("structure.paste-failed", "error" to e.message))
        }
    }

    private fun captureOriginals(world: World, origin: Location, w: Int, h: Int, l: Int): Array<BlockData?> {
        val arr = arrayOfNulls<BlockData>(w * h * l)
        var i = 0
        for (y in 0 until h) {
            for (z in 0 until l) {
                for (x in 0 until w) {
                    arr[i] = world.getBlockAt(origin.blockX + x, origin.blockY + y, origin.blockZ + z).blockData
                    i++
                }
            }
        }
        return arr
    }

    private fun runMarkerCleanup(
        world: World, origin: Location, w: Int, h: Int, l: Int,
        hasBarrier: Boolean, hasBedrock: Boolean, originals: Array<BlockData?>?,
    ) {
        val total = w * h * l
        if (total <= 0) return
        val air = Material.AIR.createBlockData()
        val budget = pasteBudgetNanos
        object : BukkitRunnable() {
            private var i = 0
            override fun run() {
                val deadline = System.nanoTime() + budget
                while (i < total) {
                    val x = i % w
                    val z = (i / w) % l
                    val y = i / (w * l)
                    val block = world.getBlockAt(origin.blockX + x, origin.blockY + y, origin.blockZ + z)
                    when (block.type) {
                        Material.BARRIER -> if (hasBarrier) block.setBlockData(air, false)
                        Material.BEDROCK -> if (hasBedrock && originals != null) originals[i]?.let { block.setBlockData(it, false) }
                        else -> {}
                    }
                    i++
                    if ((i and 1023) == 0 && System.nanoTime() >= deadline) break
                }
                if (i >= total) cancel()
            }
        }.runTaskTimer(QinhRuins.instance, 1L, 1L)
    }

    private fun spreadPlace(structure: Structure, origin: Location, markers: Pair<Boolean, Boolean>, onComplete: () -> Unit): PasteResult {
        val world = origin.world ?: return PasteResult(false, Lang.get("structure.world-null"))
        val blocks = ArrayList<BlockState>()
        for (palette in structure.palettes) blocks.addAll(palette.blocks)
        val clearBarrier = markers.first
        val keepBedrock = markers.second
        val air = Material.AIR.createBlockData()
        val budget = pasteBudgetNanos
        object : BukkitRunnable() {
            private var i = 0
            override fun run() {
                val deadline = System.nanoTime() + budget
                while (i < blocks.size) {
                    val b = blocks[i]
                    i++
                    val type = b.type
                    if (keepBedrock && type == Material.BEDROCK) {
                        if ((i and 1023) == 0 && System.nanoTime() >= deadline) break
                        continue
                    }
                    val loc = b.location
                    val data = if (clearBarrier && type == Material.BARRIER) air else b.blockData
                    world.getBlockAt(origin.blockX + loc.blockX, origin.blockY + loc.blockY, origin.blockZ + loc.blockZ)
                        .setBlockData(data, false)
                    if ((i and 1023) == 0 && System.nanoTime() >= deadline) break
                }
                if (i >= blocks.size) {
                    cancel()
                    runCatching { onComplete() }
                }
            }
        }.runTaskTimer(QinhRuins.instance, 1L, 1L)
        return PasteResult(true, Lang.get("structure.spread-place", "count" to blocks.size), structure.size)
    }

    private fun maskedPlace(file: File, origin: Location, targetMask: List<String>, sourceSkip: List<String>, sourceMask: List<String>): PasteResult {
        val world = origin.world ?: return PasteResult(false, Lang.get("structure.world-null"))
        return try {
            val structure = Bukkit.getStructureManager().loadStructure(file)
            val explicit = HashSet<Material>()
            val negated = HashSet<Material>()
            var anyReplaceable = false
            for (raw in targetMask) {
                val trimmed = raw.trim()
                if (trimmed.startsWith("!")) {
                    when (val t = trimmed.substring(1).trim().uppercase()) {
                        "AIR" -> { negated.add(Material.AIR); negated.add(Material.CAVE_AIR); negated.add(Material.VOID_AIR) }
                        else -> Material.matchMaterial(t)?.let { negated.add(it) }
                    }
                    continue
                }
                when (val t = trimmed.uppercase()) {
                    "REPLACEABLE", "*" -> anyReplaceable = true
                    "AIR" -> {
                        explicit.add(Material.AIR); explicit.add(Material.CAVE_AIR); explicit.add(Material.VOID_AIR)
                    }
                    else -> Material.matchMaterial(t)?.let { explicit.add(it) }
                }
            }
            val skip = HashSet<Material>()
            for (raw in sourceSkip) addMaterials(skip, raw.trim().uppercase())
            val sourcePositive = HashSet<Material>()
            val sourceNegated = HashSet<Material>()
            for (raw in sourceMask) {
                val trimmed = raw.trim()
                if (trimmed.startsWith("!")) addMaterials(sourceNegated, trimmed.substring(1).trim().uppercase())
                else addMaterials(sourcePositive, trimmed.uppercase())
            }
            val hasSourcePositive = sourcePositive.isNotEmpty()
            val hasPositiveMask = explicit.isNotEmpty() || anyReplaceable
            val air = Material.AIR.createBlockData()
            var placed = 0
            for (palette in structure.palettes) {
                for (block in palette.blocks) {
                    if (block.type in skip) continue
                    if (block.type in sourceNegated) continue
                    if (hasSourcePositive && block.type !in sourcePositive) continue
                    val loc = block.location
                    val target = world.getBlockAt(
                        origin.blockX + loc.blockX,
                        origin.blockY + loc.blockY,
                        origin.blockZ + loc.blockZ,
                    )
                    if (handleMarkers && block.type == Material.BEDROCK) continue
                    if (handleMarkers && block.type == Material.BARRIER) {
                        target.setBlockData(air, false)
                        placed++
                        continue
                    }
                    val type = target.type
                    val multiPart = block.blockData is org.bukkit.block.data.Bisected || block.blockData is org.bukkit.block.data.type.Bed
                    val positiveOk = !hasPositiveMask || type in explicit || (anyReplaceable && isReplaceable(type))
                    val targetOk = multiPart || (positiveOk && type !in negated)
                    if (targetOk) {
                        target.setBlockData(block.blockData, false)
                        placed++
                    }
                }
            }
            PasteResult(true, Lang.get("structure.mask-place", "count" to placed), structure.size)
        } catch (e: Exception) {
            PasteResult(false, Lang.get("structure.mask-place-failed", "error" to e.message))
        }
    }

    private fun addMaterials(set: MutableSet<Material>, token: String) {
        when (token) {
            "AIR" -> { set.add(Material.AIR); set.add(Material.CAVE_AIR); set.add(Material.VOID_AIR) }
            else -> Material.matchMaterial(token)?.let { set.add(it) }
        }
    }

    private fun isReplaceable(material: Material): Boolean {
        if (material.isAir) return true
        if (material == Material.WATER) return true
        if (!material.isSolid && material != Material.LAVA) return true
        val name = material.name
        return name.endsWith("_LEAVES") || name == "SNOW" || name == "VINE"
    }

    override fun capture(file: File, origin: Location, size: BlockVector, includeEntities: Boolean): PasteResult {
        return Profiler.time("snapshot.capture") {
            try {
                file.parentFile?.mkdirs()
                val manager = Bukkit.getStructureManager()
                val structure = manager.createStructure()
                structure.fill(origin, size, includeEntities)
                manager.saveStructure(file, structure)
                PasteResult(true, size = size)
            } catch (e: Exception) {
                PasteResult(false, Lang.get("structure.save-failed", "error" to e.message))
            }
        }
    }

    override fun structureSize(template: RuinTemplate): BlockVector? {
        val file = File(templatesDir, "${template.id}/${template.structureFile}")
        if (!file.exists()) return null
        return try {
            Bukkit.getStructureManager().loadStructure(file).size
        } catch (e: Exception) {
            null
        }
    }

    override fun waterlineY(template: RuinTemplate): Int {
        waterlineCache[template.id]?.let { return it }
        val file = File(templatesDir, "${template.id}/${template.structureFile}")
        val result = if (!file.exists()) {
            -1
        } else {
            try {
                val structure = Bukkit.getStructureManager().loadStructure(file)
                val size = structure.size
                val footprint = maxOf(1, size.blockX * size.blockZ)
                val threshold = maxOf(1, footprint / 4)
                val perY = HashMap<Int, Int>()
                for (palette in structure.palettes) {
                    for (block in palette.blocks) {
                        if (block.type == Material.WATER) {
                            perY.merge(block.location.blockY, 1) { a, b -> a + b }
                        }
                    }
                }
                perY.filterValues { it >= threshold }.keys.maxOrNull() ?: -1
            } catch (e: Exception) {
                -1
            }
        }
        waterlineCache[template.id] = result
        return result
    }

    override fun previewBlocks(template: RuinTemplate, limit: Int): List<Pair<BlockVector, BlockData>>? {
        val file = File(templatesDir, "${template.id}/${template.structureFile}")
        if (!file.exists()) return null
        return try {
            val structure = Bukkit.getStructureManager().loadStructure(file)
            val palette = structure.palettes.firstOrNull() ?: return emptyList()
            val out = ArrayList<Pair<BlockVector, BlockData>>()
            for (block in palette.blocks) {
                val type = block.type
                if (type.isAir) continue
                if (handleMarkers && (type == Material.BARRIER || type == Material.BEDROCK)) continue
                val loc = block.location
                out.add(BlockVector(loc.blockX, loc.blockY, loc.blockZ) to block.blockData)
                if (out.size > limit) return null
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    override fun placeFile(file: File, origin: Location): PasteResult {
        if (!file.exists()) return PasteResult(false, Lang.get("structure.snapshot-not-found"))
        return Profiler.time("snapshot.restore") {
            try {
                val structure = Bukkit.getStructureManager().loadStructure(file)
                structure.place(origin, false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, Random())
                PasteResult(true, size = structure.size)
            } catch (e: Exception) {
                PasteResult(false, Lang.get("structure.restore-failed", "error" to e.message))
            }
        }
    }
}
