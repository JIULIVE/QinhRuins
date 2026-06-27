package com.qinhuai.ruins.combat

import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.Objectives
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.generation.AnchorManager
import org.bukkit.Location
import java.util.UUID

class AnchorProgress(
    val anchorId: String,
    val templateId: String,
    val objectives: Objectives,
) {
    var currentStage = 1
    var completed = false
    val stageKills = HashMap<String, Int>()
    val playerKills = HashMap<UUID, HashMap<String, Int>>()

    fun onKill(killer: UUID?, mobKey: String) {
        stageKills[mobKey] = (stageKills[mobKey] ?: 0) + 1
        if (killer != null) {
            val perPlayer = playerKills.getOrPut(killer) { HashMap() }
            perPlayer[mobKey] = (perPlayer[mobKey] ?: 0) + 1
        }
    }

    fun stageRequirementsMet(): Boolean {
        val stage = objectives.stage(currentStage) ?: return false
        if (stage.kills.isEmpty()) return false
        return stage.kills.all { (stageKills[it.mobKey] ?: 0) >= it.amount }
    }

    fun isLastStage(): Boolean = currentStage >= objectives.maxStage

    fun advance() {
        currentStage++
        stageKills.clear()
    }
}

data class KillOutcome(val advancedTo: Int?, val completed: Boolean)

object ObjectiveTracker {

    private val progress = HashMap<String, AnchorProgress>()
    private var radius = 48.0

    fun configure(radius: Double) {
        this.radius = radius
    }

    fun begin(anchor: Anchor, template: RuinTemplate, objectives: Objectives): Boolean {
        if (objectives.isEmpty()) return false
        progress[anchor.id] = AnchorProgress(anchor.id, template.id, objectives)
        return true
    }

    fun progressFor(anchorId: String): AnchorProgress? = progress[anchorId]

    fun isCompleted(anchorId: String): Boolean = progress[anchorId]?.completed == true

    fun activeCount(): Int = progress.size

    fun effectiveStage(anchorId: String): Int {
        val p = progress[anchorId] ?: return 1
        return if (p.completed) p.currentStage + 1 else p.currentStage
    }

    fun clear(anchorId: String) {
        progress.remove(anchorId)
    }

    fun clearAll() {
        progress.clear()
    }

    fun onKill(anchorId: String, killer: UUID?, mobKey: String): KillOutcome {
        val p = progress[anchorId] ?: return KillOutcome(null, false)
        if (p.completed) return KillOutcome(null, false)
        p.onKill(killer, mobKey)
        if (!p.stageRequirementsMet()) return KillOutcome(null, false)
        if (p.isLastStage()) {
            p.completed = true
            return KillOutcome(null, true)
        }
        p.advance()
        return KillOutcome(p.currentStage, false)
    }

    fun nearestProgress(location: Location): AnchorProgress? =
        AnchorManager.nearby(location, radius).firstNotNullOfOrNull { progress[it.id] }
}
