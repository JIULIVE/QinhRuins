package com.qinhuai.ruins.data

import com.qinhuai.ruins.structure.PasteEngines
import org.bukkit.Location
import org.bukkit.util.BlockVector
import java.io.File

object SnapshotStore {

    private lateinit var dir: File
    private var enabled = true
    private var maxVolume = 500_000L

    fun init(dir: File, enabled: Boolean, maxVolume: Long) {
        this.dir = dir
        this.enabled = enabled
        this.maxVolume = maxVolume
        if (enabled && !dir.exists()) dir.mkdirs()
    }

    fun capture(anchorId: String, origin: Location, size: BlockVector) {
        if (!enabled || !::dir.isInitialized) return
        val bound = maxOf(size.blockX, size.blockZ)
        val volume = bound.toLong() * bound.toLong() * size.blockY.toLong()
        if (maxVolume > 0 && volume > maxVolume) {
            org.bukkit.Bukkit.getLogger().warning("[QinhRuins] 结构 $anchorId 快照体积 $volume 超过上限 $maxVolume，已跳过快照（移除时不还原地形，避免主线程卡服）")
            return
        }
        val region = BlockVector(bound, size.blockY, bound)
        PasteEngines.active().capture(file(anchorId), origin, region, false)
    }

    fun restore(anchorId: String, origin: Location): Boolean {
        if (!::dir.isInitialized) return false
        val snapshot = file(anchorId)
        if (!snapshot.exists()) return false
        val result = PasteEngines.active().placeFile(snapshot, origin)
        if (result.success) snapshot.delete()
        return result.success
    }

    fun discard(anchorId: String) {
        if (!::dir.isInitialized) return
        file(anchorId).delete()
    }

    private fun file(anchorId: String): File = File(dir, "$anchorId.nbt")
}
