package com.qinhuai.ruins.mechanism

import com.qinhuai.corelib.api.item.ItemManagerAPI
import com.qinhuai.corelib.util.ServerCompat
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.combat.MobSpawner
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.MechAction
import com.qinhuai.ruins.core.MechActionType
import com.qinhuai.ruins.core.MechRegion
import com.qinhuai.ruins.core.Mechanism
import com.qinhuai.ruins.core.RelPos
import com.qinhuai.ruins.loot.LootService
import com.qinhuai.ruins.loot.LootTableRegistry
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect

object MechActions {

    var maxFillVolume = 20000

    fun run(anchor: Anchor, mechanism: Mechanism, action: MechAction, player: Player?) {
        val base = anchor.location
        when (action.type) {
            MechActionType.FILL -> fill(base, action)
            MechActionType.SPAWN -> spawn(base, action)
            MechActionType.MESSAGE -> audience(base, mechanism.radius, player).forEach {
                TextUtil.sendColored(it, action.param("text"))
            }
            MechActionType.TITLE -> audience(base, mechanism.radius, player).forEach {
                TextUtil.showColoredTitle(it, action.param("title"), 10, 40, 10)
                action.params["subtitle"]?.let { sub -> TextUtil.sendColored(it, sub) }
            }
            MechActionType.SOUND -> sound(base, mechanism.radius, action, player)
            MechActionType.EFFECT -> effect(base, mechanism.radius, action, player)
            MechActionType.COMMAND -> command(action, player)
            MechActionType.LOOT -> loot(base, action, player)
            MechActionType.TELEPORT -> teleport(base, mechanism.radius, action, player)
            MechActionType.PARTICLE -> particle(base, action)
            MechActionType.GIVE -> give(action, player)
            MechActionType.NPC -> npc(anchor, action)
        }
    }

    private fun npc(anchor: Anchor, action: MechAction) {
        val pos = action.pos ?: RelPos(0, 0, 0)
        val loc = anchor.location.clone().add(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        action.params["yaw"]?.toFloatOrNull()?.let { loc.yaw = it }
        val name = action.param("name", "NPC")
        com.qinhuai.ruins.integration.CitizensBridge.spawnFor(anchor.id, loc, name, action.params["skin"])
    }

    private fun give(action: MechAction, player: Player?) {
        val target = player ?: return
        val ref = action.param("item").takeIf { it.isNotBlank() } ?: return
        val amount = action.intParam("amount", 1).coerceAtLeast(1)
        val item = ItemManagerAPI.getHookItem(ref, target) ?: return
        item.amount = amount.coerceAtMost(item.maxStackSize)
        val overflow = target.inventory.addItem(item)
        overflow.values.forEach { target.world.dropItemNaturally(target.location, it) }
    }

    private fun fill(base: Location, action: MechAction) {
        val region = action.region ?: return
        val material = Material.matchMaterial(action.param("material", "AIR").uppercase()) ?: return
        val world = base.world ?: return
        if (volume(region) > maxFillVolume) return
        val (minX, maxX) = order(region.from.x, region.to.x)
        val (minY, maxY) = order(region.from.y, region.to.y)
        val (minZ, maxZ) = order(region.from.z, region.to.z)
        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            world.getBlockAt(base.blockX + x, base.blockY + y, base.blockZ + z).type = material
        }
    }

    private fun spawn(base: Location, action: MechAction) {
        val pos = action.pos ?: RelPos(0, 0, 0)
        val loc = base.clone().add(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        val mob = action.param("mob").takeIf { it.isNotBlank() } ?: return
        val level = action.intParam("level", 1)
        repeat(action.intParam("count", 1).coerceAtLeast(1)) {
            MobSpawner.spawn(mob, loc, level)
        }
    }

    private fun sound(base: Location, radius: Double, action: MechAction, player: Player?) {
        val sound = ServerCompat.resolveSound(action.param("sound")) ?: return
        val volume = action.params["volume"]?.toFloatOrNull() ?: 1.0f
        val pitch = action.params["pitch"]?.toFloatOrNull() ?: 1.0f
        audience(base, radius, player).forEach { it.playSound(it.location, sound, volume, pitch) }
    }

    private fun effect(base: Location, radius: Double, action: MechAction, player: Player?) {
        val type = ServerCompat.resolvePotionEffectType(action.param("effect")) ?: return
        val amplifier = action.intParam("level", 1).coerceAtLeast(1) - 1
        val duration = action.intParam("seconds", 5).coerceAtLeast(1) * 20
        audience(base, radius, player).forEach {
            it.addPotionEffect(PotionEffect(type, duration, amplifier, true, true, true))
        }
    }

    private fun command(action: MechAction, player: Player?) {
        val raw = action.param("command").takeIf { it.isNotBlank() } ?: return
        val command = raw.replace("{player}", player?.name ?: "").removePrefix("/")
        if (action.param("as", "console").equals("player", true)) {
            player?.performCommand(command)
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        }
    }

    private fun loot(base: Location, action: MechAction, player: Player?) {
        val target = player ?: return
        val table = LootTableRegistry.get(action.param("table")) ?: return
        val pos = action.pos
        val dropAt = if (pos != null) base.clone().add(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5) else target.location
        val world = dropAt.world ?: return
        LootService.roll(table, target, action.param("growth-scaled", "true").toBoolean())
            .forEach { world.dropItemNaturally(dropAt, it) }
    }

    private fun teleport(base: Location, radius: Double, action: MechAction, player: Player?) {
        val pos = action.pos ?: return
        val dest = base.clone().add(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        action.params["yaw"]?.toFloatOrNull()?.let { dest.yaw = it }
        action.params["pitch"]?.toFloatOrNull()?.let { dest.pitch = it }
        val targets = if (action.param("target", "trigger").equals("all", true)) {
            audience(base, radius, player)
        } else {
            listOfNotNull(player)
        }
        targets.forEach { it.teleport(dest) }
    }

    private fun particle(base: Location, action: MechAction) {
        val pos = action.pos ?: RelPos(0, 0, 0)
        val loc = base.clone().add(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val world = loc.world ?: return
        val particle = ServerCompat.particle(action.param("particle", "FLAME"))
        val count = action.intParam("count", 20).coerceIn(1, 2000)
        val spread = action.params["spread"]?.toDoubleOrNull() ?: 0.5
        world.spawnParticle(particle, loc, count, spread, spread, spread, 0.0)
    }

    private fun audience(base: Location, radius: Double, player: Player?): Collection<Player> {
        val center = player?.location ?: base
        val nearby = center.world?.getNearbyPlayers(center, radius) ?: emptyList()
        if (player != null && player !in nearby) return nearby + player
        return nearby
    }

    private fun volume(region: MechRegion): Int {
        val (minX, maxX) = order(region.from.x, region.to.x)
        val (minY, maxY) = order(region.from.y, region.to.y)
        val (minZ, maxZ) = order(region.from.z, region.to.z)
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
    }

    private fun order(a: Int, b: Int): Pair<Int, Int> = if (a <= b) a to b else b to a
}
