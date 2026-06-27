package com.qinhuai.ruins.session

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

object SessionListener : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        SessionManager.handleQuit(event.player)
    }
}
