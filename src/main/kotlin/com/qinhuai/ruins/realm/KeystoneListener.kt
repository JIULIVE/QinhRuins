package com.qinhuai.ruins.realm

import com.qinhuai.ruins.affix.Keystone
import com.qinhuai.ruins.generation.AnchorManager
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

object KeystoneListener : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        if (Keystone.tierOf(item) == null) return

        if (event.action == Action.RIGHT_CLICK_AIR) {
            event.setUseItemInHand(Event.Result.DENY)
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        event.setUseItemInHand(Event.Result.DENY)

        val block = event.clickedBlock ?: return
        val anchorId = CoreRegistry.at(block) ?: return
        event.setUseInteractedBlock(Event.Result.DENY)
        val anchor = AnchorManager.get(anchorId) ?: return
        RealmManager.tryActivateAt(event.player, anchor)
    }
}
