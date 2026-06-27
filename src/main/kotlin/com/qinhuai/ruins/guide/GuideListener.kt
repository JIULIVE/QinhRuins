package com.qinhuai.ruins.guide

import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.lang.Lang
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot

object GuideListener : Listener {

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (GuideItem.readTarget(item) == null) return
        event.isCancelled = true
        val player = event.player
        if (GuideService.isGuiding(player.uniqueId)) {
            TextUtil.sendColored(player, Lang.get("gui.already-guiding"))
            return
        }
        GuideConfirmGui.open(player, item)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is GuideConfirmHolder) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        when (event.rawSlot) {
            GuideConfirmGui.YES_SLOT -> {
                player.closeInventory()
                GuideService.startGuide(player, holder.sourceItem)
            }
            GuideConfirmGui.NO_SLOT -> player.closeInventory()
        }
    }

    @EventHandler
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        if (GuideService.exitAction() != "swap") return
        if (GuideService.isGuiding(event.player.uniqueId)) {
            event.isCancelled = true
            GuideService.stopGuide(event.player.uniqueId, returnItem = true)
        }
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        if (GuideService.exitAction() != "drop") return
        if (GuideService.isGuiding(event.player.uniqueId)) {
            event.isCancelled = true
            GuideService.stopGuide(event.player.uniqueId, returnItem = true)
        }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (GuideService.isGuiding(event.player.uniqueId)) {
            GuideService.stopGuide(event.player.uniqueId, returnItem = true)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (GuideService.isGuiding(event.player.uniqueId)) {
            GuideService.stopGuide(event.player.uniqueId, returnItem = true)
        }
    }
}
