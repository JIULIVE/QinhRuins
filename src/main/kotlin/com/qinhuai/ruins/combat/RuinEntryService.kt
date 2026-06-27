package com.qinhuai.ruins.combat

import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.AnchorState
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object RuinEntryService {

    private val current = HashMap<UUID, String>()
    private var task: BukkitTask? = null

    fun start(plugin: JavaPlugin) {
        stop()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
    }

    fun stop() {
        task?.cancel()
        task = null
        current.clear()
    }

    private fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            val anchor = AnchorManager.containing(player.location)
            val prev = current[player.uniqueId]
            if (anchor == null) {
                if (prev != null) current.remove(player.uniqueId)
                continue
            }
            if (anchor.id == prev) continue
            current[player.uniqueId] = anchor.id
            onEnter(player, anchor)
        }
    }

    private fun onEnter(player: Player, anchor: Anchor) {
        val template = TemplateRegistry.get(anchor.templateId) ?: return
        val title = if (anchor.state == AnchorState.CLEARED) template.titles.enterExplored else template.titles.enterNew
        if (title.isBlank()) return
        TextUtil.showColoredTitle(player, title.replace("{ruin}", template.display), 10, 50, 10)
    }
}
