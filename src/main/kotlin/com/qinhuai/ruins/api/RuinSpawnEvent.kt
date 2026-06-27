package com.qinhuai.ruins.api

import com.qinhuai.ruins.core.Anchor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RuinSpawnEvent(
    val templateId: String,
    val anchorId: String,
    val location: Location,
    val width: Int = 0,
    val height: Int = 0,
    val depth: Int = 0,
) : Event() {

    fun minPoint(): Location = location.clone()

    fun maxPoint(): Location = location.clone().add(
        (if (width > 0) width - 1 else 0).toDouble(),
        (if (height > 0) height - 1 else 0).toDouble(),
        (if (depth > 0) depth - 1 else 0).toDouble(),
    )

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS

        fun fire(anchor: Anchor) {
            Bukkit.getPluginManager().callEvent(
                RuinSpawnEvent(anchor.templateId, anchor.id, anchor.location.clone(), anchor.width, anchor.height, anchor.depth),
            )
        }
    }
}
