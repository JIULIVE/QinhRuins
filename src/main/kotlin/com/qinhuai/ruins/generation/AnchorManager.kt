package com.qinhuai.ruins.generation

import com.qinhuai.corelib.util.LocationUtils
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.AnchorState
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.data.AnchorStore
import com.qinhuai.ruins.data.SnapshotStore
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.structure.FoundationFiller
import com.qinhuai.ruins.structure.PasteEngines
import com.qinhuai.ruins.structure.PasteResult
import com.qinhuai.ruins.structure.Rotations
import com.qinhuai.ruins.template.BlueprintRegistry
import com.qinhuai.ruins.template.TemplateRegistry
import com.qinhuai.ruins.template.TilePaletteRegistry
import org.bukkit.Location
import org.bukkit.block.structure.StructureRotation
import org.bukkit.util.BlockVector
import java.util.Random
import java.util.UUID

data class SpawnOutcome(val anchor: Anchor?, val result: PasteResult)

object AnchorManager {

    private val anchors = LinkedHashMap<String, Anchor>()
    private lateinit var index: SpatialIndex
    private val random = Random()

    fun init(bucketSize: Int) {
        index = SpatialIndex(bucketSize)
    }

    fun loadAll(loaded: Collection<Anchor>) {
        anchors.clear()
        index.clear()
        for (anchor in loaded) {
            anchors[anchor.id] = anchor
            index.add(anchor.id, anchor.location)
        }
    }

    fun spawn(template: RuinTemplate, origin: Location, rotationOverride: StructureRotation? = null): SpawnOutcome {
        val palette = template.palette?.let { TilePaletteRegistry.get(it) }
        if (palette != null) return spawnProcedural(template, origin, palette)

        val blueprint = BlueprintRegistry.get(template.id)
        val hasMarkers = blueprint?.let {
            it.spawnPoints.isNotEmpty() || it.lootChests.isNotEmpty() || it.variants.isNotEmpty() ||
                it.cores.isNotEmpty() || it.spawnCommands.isNotEmpty()
        } ?: false
        val maskedPaste = template.targetMask.isNotEmpty() || template.sourceSkip.isNotEmpty() || template.sourceMask.isNotEmpty()
        val rotation = if (hasMarkers || maskedPaste) StructureRotation.NONE else (rotationOverride ?: Rotations.resolve(template.rotationMode, random))
        val id = "${template.id}_${UUID.randomUUID().toString().substring(0, 8)}"
        val rawSize = PasteEngines.active().structureSize(template)
        rawSize?.let { SnapshotStore.capture(id, origin, it) }
        val worldSize = worldSize(rawSize, rotation)
        val anchor = Anchor(
            id, template.id, origin.clone(), AnchorState.DORMANT, rotation,
            worldSize?.blockX ?: 0, worldSize?.blockY ?: 0, worldSize?.blockZ ?: 0,
        )
        anchors[id] = anchor
        index.add(id, anchor.location)

        val result = PasteEngines.active().paste(template, origin, rotation) {
            blueprint?.variants?.takeIf { it.isNotEmpty() }?.let { pasteVariants(origin, it) }
            rawSize?.let { com.qinhuai.ruins.structure.BlockReplacer.apply(origin, it, template.replaceBlocks) }
            rawSize?.let { FoundationFiller.fill(origin, it, template.foundation) }
            blueprint?.spawnCommands?.takeIf { it.isNotEmpty() }?.let { runSpawnCommands(origin, it, template.display, worldSize) }
            com.qinhuai.ruins.api.RuinSpawnEvent.fire(anchor)
        }
        if (!result.success) {
            anchors.remove(id)
            index.remove(id, anchor.location)
            SnapshotStore.discard(id)
            return SpawnOutcome(null, result)
        }
        persist()
        return SpawnOutcome(anchor, result)
    }

    private fun runSpawnCommands(origin: Location, commands: List<com.qinhuai.ruins.core.SpawnCommand>, ruinName: String, size: BlockVector?) {
        val console = org.bukkit.Bukkit.getConsoleSender()
        val maxX = origin.blockX + (size?.blockX ?: 1) - 1
        val maxY = origin.blockY + (size?.blockY ?: 1) - 1
        val maxZ = origin.blockZ + (size?.blockZ ?: 1) - 1
        for (sc in commands) {
            val cmd = sc.command
                .replace("<x>", (origin.blockX + sc.pos.x).toString())
                .replace("<y>", (origin.blockY + sc.pos.y).toString())
                .replace("<z>", (origin.blockZ + sc.pos.z).toString())
                .replace("<world>", origin.world?.name ?: "")
                .replace("<ruin>", ruinName)
                .replace("<structName>", ruinName)
                .replace("<minX>", origin.blockX.toString())
                .replace("<minY>", origin.blockY.toString())
                .replace("<minZ>", origin.blockZ.toString())
                .replace("<maxX>", maxX.toString())
                .replace("<maxY>", maxY.toString())
                .replace("<maxZ>", maxZ.toString())
                .replace("<uuid>", java.util.UUID.randomUUID().toString())
            runCatching { org.bukkit.Bukkit.dispatchCommand(console, cmd) }
        }
    }

    fun previewSize(template: RuinTemplate, rotation: StructureRotation): BlockVector? =
        worldSize(PasteEngines.active().structureSize(template), rotation)

    private fun worldSize(size: BlockVector?, rotation: StructureRotation): BlockVector? {
        if (size == null) return null
        return when (rotation) {
            StructureRotation.CLOCKWISE_90, StructureRotation.COUNTERCLOCKWISE_90 ->
                BlockVector(size.blockZ, size.blockY, size.blockX)
            else -> size
        }
    }

    fun containing(loc: Location): Anchor? = anchors.values.firstOrNull { it.contains(loc) }

    private fun spawnProcedural(template: RuinTemplate, origin: Location, palette: com.qinhuai.ruins.core.TilePalette): SpawnOutcome {
        val plan = StructureGenerator.plan(palette, random.nextLong())
        if (plan.isEmpty()) return SpawnOutcome(null, PasteResult(false, Lang.get("structure.palette-no-layout", "palette" to palette.id)))
        val id = "${template.id}_${UUID.randomUUID().toString().substring(0, 8)}"
        val footprint = StructureGenerator.footprint(plan)
        val corner = origin.clone().add(footprint.min.x.toDouble(), footprint.min.y.toDouble(), footprint.min.z.toDouble())
        SnapshotStore.capture(id, corner, footprint.size)
        var pasted = 0
        for (tile in plan) {
            val tileTemplate = TemplateRegistry.get(tile.def.templateId) ?: continue
            val tileOrigin = origin.clone().add(tile.offset.x.toDouble(), tile.offset.y.toDouble(), tile.offset.z.toDouble())
            if (PasteEngines.active().paste(tileTemplate, tileOrigin, StructureRotation.NONE).success) pasted++
        }
        if (pasted == 0) {
            SnapshotStore.discard(id)
            return SpawnOutcome(null, PasteResult(false, Lang.get("structure.palette-all-failed")))
        }
        val anchor = Anchor(
            id, template.id, corner, AnchorState.DORMANT, StructureRotation.NONE,
            footprint.size.blockX, footprint.size.blockY, footprint.size.blockZ,
        )
        anchors[id] = anchor
        index.add(id, anchor.location)
        persist()
        com.qinhuai.ruins.api.RuinSpawnEvent.fire(anchor)
        return SpawnOutcome(anchor, PasteResult(true, Lang.get("structure.procedural-done", "pasted" to pasted, "total" to plan.size), footprint.size))
    }

    fun remove(id: String): Anchor? {
        val anchor = anchors.remove(id) ?: return null
        index.remove(id, anchor.location)
        persist()
        return anchor
    }

    fun setState(id: String, state: AnchorState) {
        val anchor = anchors[id] ?: return
        if (anchor.state == state) return
        anchor.state = state
        if (state == AnchorState.CLEARED) {
            anchor.clearedAt = System.currentTimeMillis()
            com.qinhuai.ruins.api.RuinClearEvent.fire(anchor)
        }
        persist()
    }

    fun get(id: String): Anchor? = anchors[id]

    fun all(): Collection<Anchor> = anchors.values

    fun ids(): List<String> = anchors.keys.toList()

    fun nearby(loc: Location, radius: Double): List<Anchor> {
        return index.nearby(loc, radius)
            .mapNotNull { anchors[it] }
            .filter { it.location.world == loc.world && LocationUtils.distance(it.location, loc) <= radius }
            .sortedBy { LocationUtils.distance(it.location, loc) }
    }

    fun nearestOfTemplate(loc: Location, templateId: String): Anchor? {
        return anchors.values
            .filter {
                it.templateId == templateId &&
                    it.location.world == loc.world &&
                    it.state != AnchorState.CLEARED &&
                    it.state != AnchorState.RECYCLED
            }
            .minByOrNull { LocationUtils.distance(it.location, loc) }
    }

    private const val VARIANT_DEPTH_LIMIT = 3
    private const val VARIANT_TOTAL_LIMIT = 256

    private fun pasteVariants(origin: Location, slots: List<com.qinhuai.ruins.core.VariantSlot>) {
        pasteVariantsRec(origin, slots, 0, intArrayOf(0))
    }

    private fun pasteVariantsRec(origin: Location, slots: List<com.qinhuai.ruins.core.VariantSlot>, depth: Int, count: IntArray) {
        if (depth >= VARIANT_DEPTH_LIMIT) return
        for (slot in slots) {
            if (count[0] >= VARIANT_TOTAL_LIMIT) {
                com.qinhuai.ruins.QinhRuins.instance.logger.warning("变体子结构超过上限 $VARIANT_TOTAL_LIMIT 个，已截断（请检查变体是否互相引用导致膨胀）")
                return
            }
            val weightSum = slot.options.sumOf { it.weight }
            if (weightSum <= 0) continue
            var roll = random.nextInt(weightSum)
            var chosen = slot.options.last()
            for (opt in slot.options) {
                if (roll < opt.weight) {
                    chosen = opt
                    break
                }
                roll -= opt.weight
            }
            val subTemplate = TemplateRegistry.get(chosen.templateId) ?: continue
            val subOrigin = if (slot.surface) {
                val wx = origin.blockX + slot.pos.x
                val wz = origin.blockZ + slot.pos.z
                val world = origin.world
                val sy = world?.let { AnchorPlacer.groundY(it, wx, wz) } ?: (origin.blockY + slot.pos.y)
                if (world != null && sy <= world.minHeight + 1) continue
                Location(world, wx.toDouble(), sy.toDouble(), wz.toDouble())
            } else {
                origin.clone().add(slot.pos.x.toDouble(), slot.pos.y.toDouble(), slot.pos.z.toDouble())
            }
            if (slot.yMax > slot.yMin) subOrigin.add(0.0, (slot.yMin + random.nextInt(slot.yMax - slot.yMin + 1)).toDouble(), 0.0)
            PasteEngines.active().paste(subTemplate, subOrigin, StructureRotation.NONE)
            count[0]++
            val subBlueprint = BlueprintRegistry.get(subTemplate.id)
            if (subBlueprint != null && subBlueprint.variants.isNotEmpty()) {
                pasteVariantsRec(subOrigin, subBlueprint.variants, depth + 1, count)
            }
        }
    }

    private fun persist() {
        AnchorStore.saveAll(anchors.values)
    }
}
