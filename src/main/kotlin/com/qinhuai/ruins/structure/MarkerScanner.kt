package com.qinhuai.ruins.structure

import com.qinhuai.ruins.core.LootChest
import com.qinhuai.ruins.core.RelPos
import com.qinhuai.ruins.core.SpawnCommand
import com.qinhuai.ruins.core.SpawnPoint
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Sign
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object MarkerScanner {

    data class Result(
        val spawnPoints: List<SpawnPoint>,
        val lootChests: List<LootChest>,
        val cores: List<RelPos>,
        val commands: List<SpawnCommand>,
        val signs: List<Pair<Location, Material>>,
    ) {
        val total: Int get() = spawnPoints.size + lootChests.size + cores.size + commands.size
        fun isEmpty(): Boolean = total == 0
    }

    fun scan(world: World, ox: Int, oy: Int, oz: Int, sx: Int, sy: Int, sz: Int): Result {
        val spawns = ArrayList<SpawnPoint>()
        val chests = ArrayList<LootChest>()
        val cores = ArrayList<RelPos>()
        val commands = ArrayList<SpawnCommand>()
        val signs = ArrayList<Pair<Location, Material>>()
        var spawnSeq = 0
        var chestSeq = 0
        for (dx in 0 until sx) {
            for (dy in 0 until sy) {
                for (dz in 0 until sz) {
                    val block = world.getBlockAt(ox + dx, oy + dy, oz + dz)
                    if (!block.type.name.endsWith("SIGN")) continue
                    val state = block.state as? Sign ?: continue
                    val lines = readLines(state)
                    val tag = lines.getOrNull(0)?.trim()?.lowercase() ?: continue
                    val rel = RelPos(dx, dy, dz)
                    when (tag) {
                        "[mob]", "[spawn]", "[mythicmob]", "[mmob]" -> {
                            var mob = lines.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: "ZOMBIE"
                            if ((tag == "[mythicmob]" || tag == "[mmob]") && !mob.startsWith("mm-", ignoreCase = true)) mob = "mm-$mob"
                            val (count, level) = parseCountLevel(lines.getOrNull(2)?.trim() ?: "")
                            val stage = lines.getOrNull(3)?.trim()?.toIntOrNull() ?: 1
                            spawns.add(SpawnPoint("sp${++spawnSeq}", rel, mob, count, level, stage))
                            signs.add(block.location to Material.AIR)
                        }
                        "[chest]", "[loot]" -> {
                            val table = lines.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: "default"
                            val unlock = lines.getOrNull(2)?.trim()?.toIntOrNull() ?: 1
                            chests.add(LootChest("c${++chestSeq}", rel, table, true, true, unlock))
                            signs.add(block.location to Material.CHEST)
                        }
                        "[core]" -> {
                            cores.add(rel)
                            signs.add(block.location to Material.LODESTONE)
                        }
                        "[command]", "[cmd]" -> {
                            val cmd = (1..3).mapNotNull { lines.getOrNull(it)?.trim()?.takeIf { s -> s.isNotEmpty() } }.joinToString(" ")
                            if (cmd.isNotEmpty()) commands.add(SpawnCommand(rel, cmd))
                            signs.add(block.location to Material.AIR)
                        }
                    }
                }
            }
        }
        return Result(spawns, chests, cores, commands, signs)
    }

    fun applyMarkerBlocks(result: Result) {
        result.signs.forEach { (loc, mat) -> loc.block.type = mat }
    }

    fun writeBlueprint(file: File, result: Result) {
        val yml = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
        yml.set("spawn-points", result.spawnPoints.map { sp ->
            linkedMapOf<String, Any>(
                "id" to sp.id, "x" to sp.pos.x, "y" to sp.pos.y, "z" to sp.pos.z,
                "mob" to sp.mob, "count" to sp.count, "level" to sp.level, "stage" to sp.stage,
            )
        })
        yml.set("loot-chests", result.lootChests.map { c ->
            linkedMapOf<String, Any>(
                "id" to c.id, "x" to c.pos.x, "y" to c.pos.y, "z" to c.pos.z,
                "loot-table" to c.lootTable, "per-player-once" to c.perPlayerOnce,
                "growth-scaled" to c.growthScaled, "unlock-stage" to c.unlockStage,
            )
        })
        if (result.cores.isNotEmpty()) {
            yml.set("cores", result.cores.map { linkedMapOf("x" to it.x, "y" to it.y, "z" to it.z) })
        }
        if (result.commands.isNotEmpty()) {
            yml.set("spawn-commands", result.commands.map {
                linkedMapOf<String, Any>("x" to it.pos.x, "y" to it.pos.y, "z" to it.pos.z, "command" to it.command)
            })
        }
        file.parentFile?.mkdirs()
        runCatching { yml.save(file) }
    }

    private fun parseCountLevel(raw: String): Pair<Int, Int> {
        if (raw.isEmpty()) return 1 to 1
        val parts = raw.split('*', 'x', 'X')
        val count = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
        val level = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
        return count.coerceAtLeast(1) to level.coerceAtLeast(1)
    }

    @Suppress("DEPRECATION")
    private fun readLines(sign: Sign): List<String> {
        runCatching {
            return sign.getSide(org.bukkit.block.sign.Side.FRONT).lines.toList()
        }
        return runCatching { sign.lines.toList() }.getOrDefault(emptyList())
    }
}
