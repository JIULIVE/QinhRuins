package com.qinhuai.ruins.realm

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent

object RealmAffixListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onRegainHealth(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        if (RealmManager.isUnderNoHeal(player)) event.isCancelled = true
    }
}
