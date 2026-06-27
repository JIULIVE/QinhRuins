package com.qinhuai.ruins.structure

import com.qinhuai.ruins.QinhRuins
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BlockVector

object BlockReplacer {

    private const val PER_TICK = 4096

    fun apply(origin: Location, size: BlockVector, replacements: Map<String, String>) {
        if (replacements.isEmpty()) return
        val world = origin.world ?: return

        val map = HashMap<Material, BlockData>()
        for ((from, to) in replacements) {
            val fromMaterial = Material.matchMaterial(from.trim().uppercase()) ?: continue
            val toData = runCatching { Bukkit.createBlockData(to.trim().lowercase()) }.getOrNull()
                ?: Material.matchMaterial(to.trim().uppercase())?.createBlockData()
                ?: continue
            map[fromMaterial] = toData
        }
        if (map.isEmpty()) return

        val w = size.blockX
        val h = size.blockY
        val l = size.blockZ
        val total = w * h * l
        if (total <= 0) return

        object : BukkitRunnable() {
            private var i = 0
            override fun run() {
                var n = 0
                while (n < PER_TICK && i < total) {
                    val x = i % w
                    val z = (i / w) % l
                    val y = i / (w * l)
                    val block = world.getBlockAt(origin.blockX + x, origin.blockY + y, origin.blockZ + z)
                    map[block.type]?.let { block.setBlockData(it, false) }
                    i++
                    n++
                }
                if (i >= total) cancel()
            }
        }.runTaskTimer(QinhRuins.instance, 1L, 1L)
    }
}
