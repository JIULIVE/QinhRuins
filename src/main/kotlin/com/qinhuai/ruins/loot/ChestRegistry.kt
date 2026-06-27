package com.qinhuai.ruins.loot

import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.LootChest
import com.qinhuai.ruins.template.BlueprintRegistry
import org.bukkit.Location
import org.bukkit.block.Block

data class ChestRef(val anchorId: String, val templateId: String, val chest: LootChest)

object ChestRegistry {

    private val byBlock = HashMap<String, ChestRef>()

    fun rebuild(anchors: Collection<Anchor>) {
        byBlock.clear()
        for (anchor in anchors) {
            val blueprint = BlueprintRegistry.get(anchor.templateId) ?: continue
            val base = anchor.location
            for (chest in blueprint.lootChests) {
                val loc = base.clone().add(chest.pos.x.toDouble(), chest.pos.y.toDouble(), chest.pos.z.toDouble())
                byBlock[key(loc)] = ChestRef(anchor.id, anchor.templateId, chest)
            }
        }
    }

    fun at(block: Block): ChestRef? = byBlock[key(block.location)]

    private fun key(loc: Location): String = "${loc.world?.name}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"
}
