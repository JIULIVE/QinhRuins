package com.qinhuai.ruins.realm

import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.template.BlueprintRegistry
import org.bukkit.Location
import org.bukkit.block.Block

object CoreRegistry {

    private val byBlock = HashMap<String, String>()

    fun rebuild(anchors: Collection<Anchor>) {
        byBlock.clear()
        for (anchor in anchors) {
            val blueprint = BlueprintRegistry.get(anchor.templateId) ?: continue
            val base = anchor.location
            for (core in blueprint.cores) {
                val loc = base.clone().add(core.x.toDouble(), core.y.toDouble(), core.z.toDouble())
                byBlock[key(loc)] = anchor.id
            }
        }
    }

    fun at(block: Block): String? = byBlock[key(block.location)]

    private fun key(loc: Location): String = "${loc.world?.name}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"
}
