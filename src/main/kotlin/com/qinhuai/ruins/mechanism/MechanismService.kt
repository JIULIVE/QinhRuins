package com.qinhuai.ruins.mechanism

import com.qinhuai.ruins.combat.ObjectiveTracker
import com.qinhuai.ruins.data.MechFiredStore
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.MechRegion
import com.qinhuai.ruins.core.MechTriggerType
import com.qinhuai.ruins.core.Mechanism
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.template.BlueprintRegistry
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object MechanismService {

    private class MechState {
        val cooldownUntil = HashMap<String, Long>()
        val inside = HashMap<String, MutableSet<UUID>>()
        val timerStart = HashMap<String, Long>()
    }

    private class BlockRef(val anchorId: String, val mechId: String, val type: MechTriggerType)

    private val states = HashMap<String, MechState>()
    private val blockIndex = HashMap<String, MutableList<BlockRef>>()
    private var task: BukkitTask? = null
    private var scanRadius = 48.0
    private var enabled = true
    private var hasTickMechanisms = false

    fun start(plugin: JavaPlugin, config: FileConfiguration) {
        stop()
        enabled = config.getBoolean("mechanisms.enabled", true)
        if (!enabled) return
        scanRadius = config.getDouble("mechanisms.scan-radius", 48.0)
        MechActions.maxFillVolume = config.getInt("mechanisms.max-fill-volume", 20000)
        val period = config.getLong("mechanisms.period-ticks", 10L).coerceAtLeast(2L)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, period, period)
    }

    fun stop() {
        task?.cancel()
        task = null
        states.clear()
    }

    fun rebuild(anchors: Collection<Anchor>) {
        blockIndex.clear()
        hasTickMechanisms = false
        for (anchor in anchors) {
            val blueprint = BlueprintRegistry.get(anchor.templateId) ?: continue
            for (mech in blueprint.mechanisms) {
                when (mech.trigger.type) {
                    MechTriggerType.INTERACT, MechTriggerType.BLOCK_BREAK, MechTriggerType.REDSTONE -> {
                        val pos = mech.trigger.pos ?: continue
                        val loc = anchor.location.clone().add(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                        blockIndex.getOrPut(key(loc)) { ArrayList() }.add(BlockRef(anchor.id, mech.id, mech.trigger.type))
                    }
                    MechTriggerType.REGION_ENTER, MechTriggerType.TIMER -> hasTickMechanisms = true
                    else -> {}
                }
            }
        }
    }

    fun clear(anchorId: String) {
        states.remove(anchorId)
    }

    fun purge(anchorId: String) {
        states.remove(anchorId)
        MechFiredStore.clear(anchorId)
    }

    fun onInteract(block: Block, player: Player) = dispatchBlock(block, player, MechTriggerType.INTERACT)

    fun onBreak(block: Block, player: Player) = dispatchBlock(block, player, MechTriggerType.BLOCK_BREAK)

    fun onRedstone(block: Block) = dispatchBlock(block, null, MechTriggerType.REDSTONE)

    fun hasBlockTriggers(): Boolean = blockIndex.isNotEmpty()

    fun isBreakTrigger(block: Block): Boolean =
        blockIndex[key(block.location)]?.any { it.type == MechTriggerType.BLOCK_BREAK } ?: false

    private fun dispatchBlock(block: Block, player: Player?, type: MechTriggerType) {
        if (!enabled) return
        val refs = blockIndex[key(block.location)] ?: return
        for (ref in refs) {
            if (ref.type != type) continue
            val anchor = AnchorManager.get(ref.anchorId) ?: continue
            val mech = BlueprintRegistry.get(anchor.templateId)?.mechanisms?.firstOrNull { it.id == ref.mechId } ?: continue
            fire(anchor, mech, player)
        }
    }

    fun onStage(anchorId: String, stage: Int) {
        if (!enabled) return
        val anchor = AnchorManager.get(anchorId) ?: return
        val blueprint = BlueprintRegistry.get(anchor.templateId) ?: return
        for (mech in blueprint.mechanisms) {
            if (mech.trigger.type == MechTriggerType.STAGE && mech.trigger.stage == stage) fire(anchor, mech, null)
        }
    }

    private fun tick() {
        if (!hasTickMechanisms) return
        val active = LinkedHashSet<String>()
        for (player in Bukkit.getOnlinePlayers()) {
            for (anchor in AnchorManager.nearby(player.location, scanRadius)) active.add(anchor.id)
        }
        val now = System.currentTimeMillis()
        for (anchorId in active) {
            val anchor = AnchorManager.get(anchorId) ?: continue
            val blueprint = BlueprintRegistry.get(anchor.templateId) ?: continue
            if (blueprint.mechanisms.isEmpty()) continue
            val nearby = anchor.location.world?.getNearbyPlayers(anchor.location, scanRadius) ?: continue
            for (mech in blueprint.mechanisms) {
                when (mech.trigger.type) {
                    MechTriggerType.REGION_ENTER -> regionTick(anchor, mech, nearby)
                    MechTriggerType.TIMER -> timerTick(anchor, mech, nearby, now)
                    else -> {}
                }
            }
        }
    }

    private fun regionTick(anchor: Anchor, mech: Mechanism, nearby: Collection<Player>) {
        val region = mech.trigger.region ?: return
        val insideNow = nearby.filter { inRegion(anchor.location, region, it.location) }
        val state = states.getOrPut(anchor.id) { MechState() }
        val previous = state.inside.getOrPut(mech.id) { HashSet() }
        for (player in insideNow) {
            if (previous.add(player.uniqueId)) fire(anchor, mech, player)
        }
        previous.retainAll(insideNow.map { it.uniqueId }.toSet())
    }

    private fun timerTick(anchor: Anchor, mech: Mechanism, nearby: Collection<Player>, now: Long) {
        if (nearby.isEmpty() || mech.trigger.intervalSeconds <= 0) return
        val state = states.getOrPut(anchor.id) { MechState() }
        val start = state.timerStart[mech.id]
        if (start == null) {
            state.timerStart[mech.id] = now
            return
        }
        if (now - start >= mech.trigger.intervalSeconds * 1000) {
            state.timerStart[mech.id] = now
            fire(anchor, mech, nearby.firstOrNull())
        }
    }

    private fun fire(anchor: Anchor, mech: Mechanism, player: Player?) {
        if (mech.requireStage > 0 && ObjectiveTracker.effectiveStage(anchor.id) < mech.requireStage) return
        if (mech.once && MechFiredStore.has(anchor.id, mech.id)) return
        val state = states.getOrPut(anchor.id) { MechState() }
        val now = System.currentTimeMillis()
        if (now < (state.cooldownUntil[mech.id] ?: 0L)) return
        for (action in mech.actions) MechActions.run(anchor, mech, action, player)
        if (mech.once) MechFiredStore.add(anchor.id, mech.id)
        if (mech.cooldownSeconds > 0) state.cooldownUntil[mech.id] = now + mech.cooldownSeconds * 1000
    }

    private fun inRegion(base: Location, region: MechRegion, loc: Location): Boolean {
        if (loc.world != base.world) return false
        val minX = base.blockX + minOf(region.from.x, region.to.x)
        val maxX = base.blockX + maxOf(region.from.x, region.to.x)
        val minY = base.blockY + minOf(region.from.y, region.to.y)
        val maxY = base.blockY + maxOf(region.from.y, region.to.y)
        val minZ = base.blockZ + minOf(region.from.z, region.to.z)
        val maxZ = base.blockZ + maxOf(region.from.z, region.to.z)
        return loc.blockX in minX..maxX && loc.blockY in minY..maxY && loc.blockZ in minZ..maxZ
    }

    private fun key(loc: Location): String = "${loc.world?.name}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"
}
