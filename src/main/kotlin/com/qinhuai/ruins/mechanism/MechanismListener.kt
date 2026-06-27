package com.qinhuai.ruins.mechanism

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

object MechanismListener : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        MechanismService.onInteract(block, event.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        MechanismService.onBreak(event.block, event.player)
    }

    @EventHandler
    fun onRedstone(event: BlockRedstoneEvent) {
        if (event.oldCurrent > 0 || event.newCurrent <= 0) return
        if (!MechanismService.hasBlockTriggers()) return
        MechanismService.onRedstone(event.block)
    }
}
