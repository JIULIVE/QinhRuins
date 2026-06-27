package com.qinhuai.ruins.generation

import com.qinhuai.corelib.util.ServerCompat
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.structure.PasteEngines
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object StagingService {

    private class Staged(val template: RuinTemplate, var origin: Location, var rotation: StructureRotation) {
        var ghost: List<Location> = emptyList()
    }

    private val staged = HashMap<UUID, Staged>()
    private var task: BukkitTask? = null
    private lateinit var plugin: JavaPlugin
    private var previewCap = 12000
    private val rotationCycle = listOf(
        StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
        StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90,
    )

    fun init(plugin: JavaPlugin, previewCap: Int = 12000) {
        this.plugin = plugin
        this.previewCap = previewCap
    }

    fun stage(player: Player, template: RuinTemplate) {
        clearStaged(player)
        val target = player.getTargetBlockExact(100)?.location ?: player.location.block.location
        val s = Staged(template, target.clone(), StructureRotation.NONE)
        staged[player.uniqueId] = s
        renderGhost(player, s)
        ensureTask()
        info(player, Lang.get("core.stage-staged", "ruin" to template.display))
    }

    fun move(player: Player, dx: Int, dy: Int, dz: Int) {
        val s = staged[player.uniqueId] ?: return notStaged(player)
        clearGhost(player, s)
        s.origin.add(dx.toDouble(), dy.toDouble(), dz.toDouble())
        renderGhost(player, s)
        info(player, Lang.get("core.stage-pos", "x" to s.origin.blockX, "y" to s.origin.blockY, "z" to s.origin.blockZ))
    }

    fun toHere(player: Player) {
        val s = staged[player.uniqueId] ?: return notStaged(player)
        clearGhost(player, s)
        s.origin = (player.getTargetBlockExact(100)?.location ?: player.location.block.location).clone()
        renderGhost(player, s)
        info(player, Lang.get("core.stage-pos-crosshair", "x" to s.origin.blockX, "y" to s.origin.blockY, "z" to s.origin.blockZ))
    }

    fun rotate(player: Player) {
        val s = staged[player.uniqueId] ?: return notStaged(player)
        clearGhost(player, s)
        s.rotation = rotationCycle[(rotationCycle.indexOf(s.rotation) + 1) % rotationCycle.size]
        renderGhost(player, s)
        info(player, Lang.get("core.stage-rotation", "rotation" to s.rotation.name))
    }

    fun confirm(player: Player) {
        val s = staged.remove(player.uniqueId) ?: return notStaged(player)
        clearGhost(player, s)
        cleanupTask()
        val outcome = AnchorManager.spawn(s.template, s.origin, s.rotation)
        if (outcome.anchor == null) {
            info(player, Lang.get("core.stage-place-failed", "reason" to outcome.result.message))
            return
        }
        info(player, Lang.get("core.stage-placed", "ruin" to s.template.display, "id" to outcome.anchor.id))
    }

    fun cancel(player: Player) {
        val s = staged.remove(player.uniqueId) ?: return notStaged(player)
        clearGhost(player, s)
        cleanupTask()
        info(player, Lang.get("core.stage-cancelled"))
    }

    private fun clearStaged(player: Player) {
        staged.remove(player.uniqueId)?.let { clearGhost(player, it) }
    }

    private fun renderGhost(player: Player, s: Staged) {
        val world = s.origin.world ?: return
        if (player.world != world) return
        val blocks = PasteEngines.active().previewBlocks(s.template, previewCap)
        if (blocks == null || blocks.isEmpty()) {
            s.ghost = emptyList()
            return
        }
        val raw = PasteEngines.active().structureSize(s.template) ?: return
        val sx = raw.blockX
        val sz = raw.blockZ
        val ox = s.origin.blockX
        val oy = s.origin.blockY
        val oz = s.origin.blockZ
        val sent = ArrayList<Location>(blocks.size)
        for ((pos, data) in blocks) {
            val (rx, rz) = rotateXZ(pos.blockX, pos.blockZ, sx, sz, s.rotation)
            val bd = data.clone()
            if (s.rotation != StructureRotation.NONE) bd.rotate(s.rotation)
            val loc = Location(world, (ox + rx).toDouble(), (oy + pos.blockY).toDouble(), (oz + rz).toDouble())
            runCatching { player.sendBlockChange(loc, bd) }
            sent.add(loc)
        }
        s.ghost = sent
    }

    private fun clearGhost(player: Player, s: Staged) {
        if (s.ghost.isEmpty()) return
        for (loc in s.ghost) {
            runCatching { player.sendBlockChange(loc, loc.block.blockData) }
        }
        s.ghost = emptyList()
    }

    private fun rotateXZ(x: Int, z: Int, sx: Int, sz: Int, rot: StructureRotation): Pair<Int, Int> = when (rot) {
        StructureRotation.CLOCKWISE_90 -> (sz - 1 - z) to x
        StructureRotation.CLOCKWISE_180 -> (sx - 1 - x) to (sz - 1 - z)
        StructureRotation.COUNTERCLOCKWISE_90 -> z to (sx - 1 - x)
        else -> x to z
    }

    private fun ensureTask() {
        if (task != null) return
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 0L, 10L)
    }

    private fun cleanupTask() {
        if (staged.isEmpty()) {
            task?.cancel()
            task = null
        }
    }

    private fun tick() {
        if (staged.isEmpty()) {
            cleanupTask()
            return
        }
        for ((uuid, s) in staged) {
            val player = plugin.server.getPlayer(uuid) ?: continue
            val size = AnchorManager.previewSize(s.template, s.rotation) ?: continue
            drawBox(player, s.origin, size.blockX, size.blockY, size.blockZ)
        }
    }

    private fun drawBox(player: Player, origin: Location, w: Int, h: Int, d: Int) {
        val world = origin.world ?: return
        if (player.world != world) return
        val particle = ServerCompat.particle("HAPPY_VILLAGER", "VILLAGER_HAPPY")
        val x0 = origin.blockX.toDouble()
        val y0 = origin.blockY.toDouble()
        val z0 = origin.blockZ.toDouble()
        val x1 = x0 + w
        val y1 = y0 + h
        val z1 = z0 + d
        val stepX = (w / 16.0).coerceAtLeast(1.0)
        val stepY = (h / 16.0).coerceAtLeast(1.0)
        val stepZ = (d / 16.0).coerceAtLeast(1.0)
        var x = x0
        while (x <= x1) {
            spark(player, particle, x, y0, z0); spark(player, particle, x, y1, z0)
            spark(player, particle, x, y0, z1); spark(player, particle, x, y1, z1)
            x += stepX
        }
        var y = y0
        while (y <= y1) {
            spark(player, particle, x0, y, z0); spark(player, particle, x1, y, z0)
            spark(player, particle, x0, y, z1); spark(player, particle, x1, y, z1)
            y += stepY
        }
        var z = z0
        while (z <= z1) {
            spark(player, particle, x0, y0, z); spark(player, particle, x1, y0, z)
            spark(player, particle, x0, y1, z); spark(player, particle, x1, y1, z)
            z += stepZ
        }
    }

    private fun spark(player: Player, particle: Particle, x: Double, y: Double, z: Double) {
        player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
    }

    private fun notStaged(player: Player) = info(player, Lang.get("core.stage-none"))

    private fun info(player: Player, msg: String) = TextUtil.sendColored(player, msg)
}
