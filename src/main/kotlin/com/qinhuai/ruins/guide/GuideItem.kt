package com.qinhuai.ruins.guide

import com.qinhuai.corelib.api.item.ItemManagerAPI
import com.qinhuai.corelib.item.ItemMetadataManager
import com.qinhuai.corelib.util.ItemUtils
import com.qinhuai.ruins.core.RuinTemplate
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

sealed interface GuideTarget {
    data class AnchorRef(val anchorId: String) : GuideTarget
    data class TemplateRef(val templateId: String) : GuideTarget
}

object GuideItem {

    private const val KEY_TEMPLATE = "guide_template"
    private const val KEY_ANCHOR = "guide_anchor"
    private val meta = ItemMetadataManager.get("qinhruins")

    fun buildForTemplate(template: RuinTemplate, player: Player?): ItemStack {
        val cfg = template.guideItem
        val base = cfg.ref?.let { ItemManagerAPI.getHookItem(it, player) }
            ?: ItemUtils.createItem(materialOf(cfg.material), 1, cfg.name, cfg.lore, cfg.modelData)
        meta.setString(base, KEY_TEMPLATE, template.id)
        return base
    }

    fun bindAnchor(item: ItemStack, anchorId: String) {
        meta.setString(item, KEY_ANCHOR, anchorId)
    }

    fun readTarget(item: ItemStack?): GuideTarget? {
        if (item == null || item.type == Material.AIR) return null
        meta.getString(item, KEY_ANCHOR)?.let { return GuideTarget.AnchorRef(it) }
        meta.getString(item, KEY_TEMPLATE)?.let { return GuideTarget.TemplateRef(it) }
        return null
    }

    private fun materialOf(name: String): Material = Material.matchMaterial(name) ?: Material.COMPASS
}
