package com.qinhuai.ruins.loot

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent

object VesselListener : Listener {

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? VesselService.VesselHolder ?: return
        VesselService.saveOnClose(holder, event.inventory)
    }
}
