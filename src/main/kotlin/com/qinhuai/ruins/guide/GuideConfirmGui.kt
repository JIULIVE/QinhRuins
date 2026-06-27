package com.qinhuai.ruins.guide

import com.qinhuai.corelib.util.ItemUtils
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.lang.Lang
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class GuideConfirmHolder(val sourceItem: ItemStack) : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}

object GuideConfirmGui {

    const val YES_SLOT = 11
    const val NO_SLOT = 15

    fun open(player: Player, item: ItemStack) {
        val holder = GuideConfirmHolder(item.clone())
        val inv = Bukkit.createInventory(holder, 27, TextUtil.toComponent(GuideService.confirmTitle()))
        holder.inv = inv
        inv.setItem(YES_SLOT, ItemUtils.createItem(Material.GREEN_STAINED_GLASS_PANE, 1, GuideService.confirmYesName(), listOf(Lang.get("gui.confirm-yes-lore"))))
        inv.setItem(NO_SLOT, ItemUtils.createItem(Material.RED_STAINED_GLASS_PANE, 1, GuideService.confirmNoName(), listOf(Lang.get("gui.confirm-no-lore"))))
        player.openInventory(inv)
    }
}
