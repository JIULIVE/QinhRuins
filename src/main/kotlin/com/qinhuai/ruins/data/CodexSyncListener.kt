package com.qinhuai.ruins.data

import com.qinhuai.ruins.QinhRuins
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object CodexSyncListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!RuinStorage.isDatabase()) return
        val uuid = event.player.uniqueId
        RuinStorage.runAsync {
            val set = RuinStorage.loadCodex(uuid)
            if (set.isNotEmpty()) {
                QinhRuins.instance.server.scheduler.runTask(
                    QinhRuins.instance,
                    Runnable { DiscoveryStore.mergeTemplates(uuid, set) },
                )
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        DiscoveryStore.unloadTemplates(event.player.uniqueId)
    }
}
