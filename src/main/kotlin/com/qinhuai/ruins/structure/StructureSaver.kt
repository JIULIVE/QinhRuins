package com.qinhuai.ruins.structure

import com.qinhuai.ruins.lang.Lang
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

object StructureSaver {

    private lateinit var templatesDir: File

    fun init(templatesDir: File) {
        this.templatesDir = templatesDir
    }

    fun save(player: Player, id: String): String {
        val selection = SelectionService.resolve(player.uniqueId)
            ?: return Lang.get("cmd.save-need-selection")
        val size = selection.size
        val origin = selection.origin
        val world = origin.world ?: return Lang.get("cmd.save-no-world")
        val scan = MarkerScanner.scan(world, origin.blockX, origin.blockY, origin.blockZ, size.blockX, size.blockY, size.blockZ)
        if (!scan.isEmpty()) {
            MarkerScanner.applyMarkerBlocks(scan)
            MarkerScanner.writeBlueprint(File(templatesDir, "$id/blueprint.yml"), scan)
        }
        val file = File(templatesDir, "$id/structure.nbt")
        val result = PasteEngines.active().capture(file, origin, size, false)
        if (!result.success) return "§c${result.message}"
        writeDefaultTemplate(id)
        val tail = if (!scan.isEmpty()) {
            Lang.get(
                "cmd.save-marker-tail",
                "spawns" to scan.spawnPoints.size,
                "chests" to scan.lootChests.size,
                "cores" to scan.cores.size,
                "commands" to scan.commands.size,
            )
        } else ""
        return Lang.get("cmd.save-done", "id" to id, "x" to size.blockX, "y" to size.blockY, "z" to size.blockZ, "tail" to tail)
    }

    private fun writeDefaultTemplate(id: String) {
        val file = File(templatesDir, "$id/template.yml")
        if (file.exists()) return
        val yml = YamlConfiguration()
        yml.set("id", id)
        yml.set("display", "&6$id")
        yml.set("structure.file", "structure.nbt")
        yml.set("structure.rotation", "none")
        yml.set("generation.enabled", false)
        yml.set("generation.worlds", emptyList<String>())
        yml.set("generation.biomes", emptyList<String>())
        yml.set("generation.probability.numerator", 1)
        yml.set("generation.probability.denominator", 1000)
        yml.set("generation.min-distance-others", 300)
        yml.set("generation.min-distance-same", 600)
        yml.set("generation.y", "surface")
        yml.set("generation.whitelist-ground", emptyList<String>())
        yml.set("generation.flatness.radius", 4)
        yml.set("generation.flatness.max-variance", 3)
        yml.set("guide-item.ref", "")
        yml.set("guide-item.material", "COMPASS")
        yml.set("guide-item.name", "&b$id·指引罗盘")
        yml.set("guide-item.lore", listOf("&7指向最近的此遗迹"))
        yml.set("guide-item.model-data", 0)
        yml.set("guide-item.consumable", false)
        yml.set("entry.min-players", 1)
        yml.set("entry.max-players", 10)
        yml.set("entry.required-classes", emptyList<String>())
        yml.set("entry.min-growth", 0)
        yml.set("entry.cost", "")
        yml.set("entry.cooldown", "0s")
        yml.set("session.mode", "shared-anchor")
        yml.set("session.time-limit", "30m")
        yml.set("session.cleanup-on-empty", "60s")
        yml.set("respawn", "never")
        yml.set("foundation.enabled", false)
        yml.set("foundation.max-depth", 16)
        yml.set("foundation.ignore-water", true)
        yml.set("foundation.materials.default", "STONE")
        runCatching { yml.save(file) }
        writeDefaultBlueprint(id)
    }

    private fun writeDefaultBlueprint(id: String) {
        val file = File(templatesDir, "$id/blueprint.yml")
        if (file.exists()) return
        val yml = YamlConfiguration()
        yml.set("spawn-points", emptyList<Map<String, Any>>())
        yml.set("loot-chests", emptyList<Map<String, Any>>())
        runCatching { yml.save(file) }
    }
}
