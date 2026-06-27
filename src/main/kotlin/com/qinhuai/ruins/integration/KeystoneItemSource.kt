package com.qinhuai.ruins.integration

import com.qinhuai.corelib.item.ItemSource
import com.qinhuai.ruins.affix.Keystone
import com.qinhuai.ruins.affix.TierConfig
import com.qinhuai.ruins.guide.GuideItem
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.inventory.ItemStack

object KeystoneItemSource : ItemSource {

    override val id: String = "qinhruins"

    override fun getItem(id: String, amount: Int): ItemStack? {
        val trimmed = id.trim()
        if (trimmed.startsWith("guide_", ignoreCase = true)) {
            val template = TemplateRegistry.get(trimmed.substring(6)) ?: return null
            return GuideItem.buildForTemplate(template, null).also { it.amount = amount.coerceAtLeast(1) }
        }
        val tier = trimmed.toIntOrNull()?.takeIf { it in 1..TierConfig.maxTier } ?: return null
        val item = Keystone.build(tier, null)
        item.amount = amount.coerceAtLeast(1)
        return item
    }

    override fun isAvailable(): Boolean = true
}
