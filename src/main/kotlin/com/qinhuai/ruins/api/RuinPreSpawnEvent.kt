package com.qinhuai.ruins.api

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RuinPreSpawnEvent(
    val templateId: String,
    val location: Location,
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS

        fun fire(templateId: String, location: Location): Boolean {
            val event = RuinPreSpawnEvent(templateId, location.clone())
            Bukkit.getPluginManager().callEvent(event)
            return !event.isCancelled
        }
    }
}
