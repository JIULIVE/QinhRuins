package com.qinhuai.ruins.structure

import com.qinhuai.ruins.QinhRuins
import com.qinhuai.ruins.core.FoundationConfig
import org.bukkit.Keyed
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BlockVector
import java.util.ArrayDeque

object FoundationFiller {

    fun fill(origin: Location, size: BlockVector, config: FoundationConfig) {
        if (!config.enabled) return
        val world = origin.world ?: return
        val baseY = origin.blockY
        val blend = config.blendRadius.coerceAtLeast(0)
        val minX = origin.blockX
        val maxX = origin.blockX + size.blockX - 1
        val minZ = origin.blockZ
        val maxZ = origin.blockZ + size.blockZ - 1

        val columns = ArrayDeque<IntArray>()
        for (x in (minX - blend)..(maxX + blend)) {
            for (z in (minZ - blend)..(maxZ + blend)) {
                val edgeDist = maxOf(maxOf(minX - x, x - maxX, 0), maxOf(minZ - z, z - maxZ, 0))
                if (edgeDist == 0) {
                    if (world.getBlockAt(x, baseY, z).type.isSolid) columns.add(intArrayOf(x, baseY - 1, z))
                } else if (edgeDist <= blend) {
                    columns.add(intArrayOf(x, baseY - 1 - edgeDist, z))
                }
            }
        }
        if (columns.isEmpty()) return

        object : BukkitRunnable() {
            override fun run() {
                var processed = 0
                while (processed < 16) {
                    val col = columns.poll() ?: run {
                        cancel()
                        return
                    }
                    fillColumn(world, col[0], col[1], col[2], config)
                    processed++
                }
            }
        }.runTaskTimer(QinhRuins.instance, 1L, 1L)
    }

    private fun fillColumn(world: World, x: Int, startY: Int, z: Int, config: FoundationConfig) {
        if (startY <= world.minHeight) return
        val material = materialFor(world, x, startY, z, config) ?: return
        var y = startY
        var depth = 0
        while (depth < config.maxDepth && y > world.minHeight) {
            val block = world.getBlockAt(x, y, z)
            val type = block.type
            val fillable = block.isEmpty ||
                isFoliage(type) ||
                (config.ignoreWater && type == Material.WATER)
            if (!fillable) break
            block.type = material
            y--
            depth++
        }
    }

    private fun materialFor(world: World, x: Int, y: Int, z: Int, config: FoundationConfig): Material? {
        val key = biomeKey(world, x, y, z)
        val name = config.biomeMaterials[key] ?: config.defaultMaterial ?: return null
        return Material.matchMaterial(name)
    }

    private fun biomeKey(world: World, x: Int, y: Int, z: Int): String {
        val biome = world.getBiome(x, y, z)
        return runCatching { (biome as Keyed).key.value().uppercase() }
            .getOrElse { biome.toString().substringAfterLast('.').substringAfterLast(':').uppercase() }
    }

    private fun isFoliage(material: Material): Boolean {
        if (material.isAir) return true
        val name = material.name
        if (name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_SAPLING")) return true
        if (material == Material.SNOW || material == Material.BAMBOO || material == Material.VINE) return true
        return !material.isSolid && material != Material.WATER && material != Material.LAVA
    }
}
