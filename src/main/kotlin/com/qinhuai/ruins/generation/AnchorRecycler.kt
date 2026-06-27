package com.qinhuai.ruins.generation

import com.qinhuai.ruins.combat.AnchorActivation
import com.qinhuai.ruins.data.SnapshotStore
import com.qinhuai.ruins.data.VesselStore
import com.qinhuai.ruins.loot.ChestRegistry
import com.qinhuai.ruins.mechanism.MechanismService
import com.qinhuai.ruins.realm.CoreRegistry

object AnchorRecycler {

    data class Result(val found: Boolean, val restored: Boolean)

    fun recycle(id: String): Result {
        val anchor = AnchorManager.remove(id) ?: return Result(false, false)
        val world = anchor.location.world
        val forced = ArrayList<Long>()
        if (world != null) {
            val w = if (anchor.width > 0) anchor.width else 64
            val d = if (anchor.depth > 0) anchor.depth else 64
            val minCX = anchor.location.blockX shr 4
            val minCZ = anchor.location.blockZ shr 4
            val maxCX = (anchor.location.blockX + w) shr 4
            val maxCZ = (anchor.location.blockZ + d) shr 4
            for (cx in minCX..maxCX) for (cz in minCZ..maxCZ) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz)
                    forced.add((cx.toLong() shl 32) or (cz.toLong() and 0xffffffffL))
                }
            }
        }
        AnchorActivation.despawn(id)
        MechanismService.purge(id)
        com.qinhuai.ruins.integration.CitizensBridge.despawnAnchor(id)
        VesselStore.clearAnchor(id)
        com.qinhuai.ruins.data.SharedVesselStore.clearAnchor(id)
        val restored = SnapshotStore.restore(id, anchor.location)
        ChestRegistry.rebuild(AnchorManager.all())
        MechanismService.rebuild(AnchorManager.all())
        CoreRegistry.rebuild(AnchorManager.all())
        if (world != null) {
            for (packed in forced) {
                val cx = (packed shr 32).toInt()
                val cz = packed.toInt()
                world.unloadChunk(cx, cz, true)
            }
        }
        return Result(true, restored)
    }
}
