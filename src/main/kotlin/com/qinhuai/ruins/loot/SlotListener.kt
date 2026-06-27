package com.qinhuai.ruins.loot

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

object SlotListener : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? SlotMachine.SlotHolder ?: return
        event.isCancelled = true
        if (event.clickedInventory != event.inventory) return
        val player = event.whoClicked as? Player ?: return
        if (event.rawSlot == SlotMachine.BUTTON_SLOT) SlotMachine.onButton(player, holder)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is SlotMachine.SlotHolder) event.isCancelled = true
    }
}
