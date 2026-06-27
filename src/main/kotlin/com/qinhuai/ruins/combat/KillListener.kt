package com.qinhuai.ruins.combat

import com.qinhuai.corelib.pdc.PdcServiceManager
import com.qinhuai.corelib.util.ServerCompat
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.mechanism.MechanismService
import com.qinhuai.ruins.template.BlueprintRegistry
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent

object KillListener : Listener {

    private val pdc = PdcServiceManager.get("qinhruins")

    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        val container = event.entity.persistentDataContainer
        val anchorId = pdc.getString(container, "mob_anchor") ?: return
        val mobKey = pdc.getString(container, "mob_key") ?: return
        val killer = event.entity.killer?.uniqueId

        if (pdc.getString(container, "mob_boss") == "true") {
            if (BossGateTracker.markCleared(anchorId)) {
                AnchorManager.setState(anchorId, com.qinhuai.ruins.core.AnchorState.CLEARED)
                announceComplete(anchorId)
                MechanismService.onStage(anchorId, ObjectiveTracker.effectiveStage(anchorId))
                com.qinhuai.ruins.loot.SlotMachine.grantOnClear(anchorId)
            }
            return
        }

        val outcome = ObjectiveTracker.onKill(anchorId, killer, mobKey)
        when {
            outcome.advancedTo != null -> {
                AnchorActivation.spawnStage(anchorId, outcome.advancedTo)
                announceStage(anchorId, outcome.advancedTo)
                MechanismService.onStage(anchorId, outcome.advancedTo)
            }
            outcome.completed -> {
                AnchorManager.setState(anchorId, com.qinhuai.ruins.core.AnchorState.CLEARED)
                announceComplete(anchorId)
                MechanismService.onStage(anchorId, ObjectiveTracker.effectiveStage(anchorId))
                com.qinhuai.ruins.loot.SlotMachine.grantOnClear(anchorId)
            }
        }

        val gate = BossGateTracker.onKill(anchorId, mobKey)
        if (gate.unlockedNow) {
            AnchorActivation.spawnBoss(anchorId)
            announceBossUnlock(anchorId)
        }
    }

    private fun announceBossUnlock(anchorId: String) {
        val anchor = AnchorManager.get(anchorId) ?: return
        if (BossGateTracker.progressFor(anchorId)?.gate?.announce == false) return
        val name = TemplateRegistry.get(anchor.templateId)?.display ?: anchor.templateId
        announce(
            anchor.location,
            Lang.get("messages.boss-unlock", "ruin" to name),
            Lang.get("messages.boss-unlock-sub", "ruin" to name),
            0.8f,
        )
    }

    private fun announceStage(anchorId: String, stage: Int) {
        val anchor = AnchorManager.get(anchorId) ?: return
        val stageName = BlueprintRegistry.get(anchor.templateId)?.objectives?.stage(stage)?.name
            ?: Lang.get("core.stage-fallback", "stage" to stage)
        announce(
            anchor.location,
            Lang.get("messages.stage-advance", "stage" to stageName),
            Lang.get("messages.stage-advance-sub", "stage" to stageName),
            1.4f,
        )
    }

    private fun announceComplete(anchorId: String) {
        val anchor = AnchorManager.get(anchorId) ?: return
        val template = TemplateRegistry.get(anchor.templateId)
        val name = template?.display ?: anchor.templateId
        announce(
            anchor.location,
            (template?.titles?.clear ?: Lang.get("core.clear-title", "ruin" to name)).replace("{ruin}", name),
            Lang.get("messages.complete-sub", "ruin" to name),
            1.6f,
        )
    }

    private fun announce(loc: Location, title: String, sub: String, pitch: Float) {
        val world = loc.world ?: return
        for (player in world.getNearbyPlayers(loc, 48.0)) {
            TextUtil.showColoredTitle(player, title, 10, 40, 10)
            TextUtil.sendColored(player, sub)
            ServerCompat.resolveSound("ENTITY_PLAYER_LEVELUP")?.let { player.playSound(player.location, it, 1.0f, pitch) }
        }
    }
}
