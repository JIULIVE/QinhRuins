package com.qinhuai.ruins.combat

import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.mechanism.MechanismService
import com.qinhuai.ruins.realm.RealmManager
import com.qinhuai.ruins.template.BlueprintRegistry
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

object RuinProtectionListener : Listener {

    private var enabled = true

    fun configure(config: FileConfiguration) {
        enabled = config.getBoolean("protection.enabled", true)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (!enabled) return
        if (event.player.hasPermission("qinhruins.admin")) return
        val anchor = AnchorManager.containing(event.block.location) ?: return
        if (!isLocked(anchor)) return
        if (MechanismService.isBreakTrigger(event.block)) return
        event.isCancelled = true
        TextUtil.sendColored(event.player, Lang.get("core.protect-no-break"))
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (!enabled) return
        if (event.player.hasPermission("qinhruins.admin")) return
        val anchor = AnchorManager.containing(event.block.location) ?: return
        if (!isLocked(anchor)) return
        event.isCancelled = true
    }

    private fun isLocked(anchor: Anchor): Boolean {
        if (RealmManager.isRealm(anchor.id)) return true
        val blueprint = BlueprintRegistry.get(anchor.templateId) ?: return false
        if (blueprint.objectives.isEmpty()) return false
        return !ObjectiveTracker.isCompleted(anchor.id)
    }
}
