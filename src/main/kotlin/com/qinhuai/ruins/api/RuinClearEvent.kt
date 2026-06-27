package com.qinhuai.ruins.api

import com.qinhuai.ruins.core.Anchor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RuinClearEvent(
    val templateId: String,
    val anchorId: String,
    val location: Location,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS

        fun fire(anchor: Anchor) {
            Bukkit.getPluginManager().callEvent(
                RuinClearEvent(anchor.templateId, anchor.id, anchor.location.clone()),
            )
        }
    }
}
