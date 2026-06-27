package com.qinhuai.ruins.combat

import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.BossGate
import com.qinhuai.ruins.generation.AnchorManager
import org.bukkit.Location

class BossGateProgress(
    val anchorId: String,
    val gate: BossGate,
) {
    var kills = 0
    var unlocked = false
    var cleared = false

    fun counts(mobKey: String): Boolean =
        gate.countMobs.isEmpty() || mobKey.lowercase() in gate.countMobs

    val remaining: Int get() = (gate.requiredKills - kills).coerceAtLeast(0)
}

data class GateKillOutcome(val counted: Boolean, val unlockedNow: Boolean)

object BossGateTracker {

    private val progress = HashMap<String, BossGateProgress>()
    private var radius = 48.0

    fun configure(radius: Double) {
        this.radius = radius
    }

    fun begin(anchor: Anchor, gate: BossGate) {
        progress[anchor.id] = BossGateProgress(anchor.id, gate)
    }

    fun progressFor(anchorId: String): BossGateProgress? = progress[anchorId]

    fun activeCount(): Int = progress.size

    fun clear(anchorId: String) {
        progress.remove(anchorId)
    }

    fun clearAll() {
        progress.clear()
    }

    fun onKill(anchorId: String, mobKey: String): GateKillOutcome {
        val p = progress[anchorId] ?: return GateKillOutcome(false, false)
        if (p.unlocked || p.cleared) return GateKillOutcome(false, false)
        if (!p.counts(mobKey)) return GateKillOutcome(false, false)
        p.kills++
        if (p.kills >= p.gate.requiredKills) {
            p.unlocked = true
            return GateKillOutcome(true, true)
        }
        return GateKillOutcome(true, false)
    }

    fun markCleared(anchorId: String): Boolean {
        val p = progress[anchorId] ?: return false
        if (p.cleared) return false
        p.cleared = true
        return true
    }

    fun nearestProgress(location: Location): BossGateProgress? =
        AnchorManager.nearby(location, radius).firstNotNullOfOrNull { progress[it.id] }
}
