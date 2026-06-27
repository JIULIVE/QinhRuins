package com.qinhuai.ruins.combat

import com.qinhuai.corelib.pdc.PdcServiceManager
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.Blueprint
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.core.SpawnPoint
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.template.BlueprintRegistry
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object AnchorActivation {

    private val pdc = PdcServiceManager.get("qinhruins")
    private val populatedAt = HashMap<String, Long>()
    private val spawnedMobs = HashMap<String, MutableSet<UUID>>()
    private var task: BukkitTask? = null
    private var activationRadius = 48.0

    fun start(plugin: JavaPlugin, config: FileConfiguration) {
        stop()
        activationRadius = config.getDouble("combat.activation-radius", 48.0)
        ObjectiveTracker.configure(activationRadius)
        BossGateTracker.configure(activationRadius)
        val period = config.getLong("combat.spawn-period-ticks", 40L).coerceAtLeast(10L)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, period, period)
    }

    fun stop() {
        task?.cancel()
        task = null
        for (id in spawnedMobs.values) {
            for (uuid in id) Bukkit.getEntity(uuid)?.remove()
        }
        spawnedMobs.clear()
        populatedAt.clear()
        ObjectiveTracker.clearAll()
        BossGateTracker.clearAll()
    }

    private fun tick() {
        val seen = HashSet<String>()
        for (player in Bukkit.getOnlinePlayers()) {
            for (anchor in AnchorManager.nearby(player.location, activationRadius)) {
                if (com.qinhuai.ruins.data.DiscoveryStore.addTemplate(player.uniqueId, anchor.templateId)) {
                    val display = TemplateRegistry.get(anchor.templateId)?.display ?: anchor.templateId
                    com.qinhuai.corelib.util.TextUtil.sendColored(
                        player,
                        com.qinhuai.ruins.lang.Lang.get("messages.discover", "ruin" to display),
                    )
                }
                if (seen.add(anchor.id)) maybePopulate(anchor)
            }
        }
    }

    private fun maybePopulate(anchor: Anchor) {
        if (com.qinhuai.ruins.realm.RealmManager.isRealm(anchor.id)) return
        val template = TemplateRegistry.get(anchor.templateId) ?: return
        val blueprint = BlueprintRegistry.get(anchor.templateId) ?: return
        if (blueprint.spawnPoints.isEmpty()) return

        val now = System.currentTimeMillis()
        val last = populatedAt[anchor.id]
        if (last != null) {
            if (template.respawnSeconds <= 0) return
            val alive = spawnedMobs[anchor.id]?.count { Bukkit.getEntity(it)?.isDead == false } ?: 0
            if (alive > 0) return
            if (now - last < template.respawnSeconds * 1000) return
        }
        spawn(anchor, template, blueprint)
        populatedAt[anchor.id] = now
    }

    private fun spawn(anchor: Anchor, template: RuinTemplate, blueprint: Blueprint) {
        val staged = ObjectiveTracker.begin(anchor, template, blueprint.objectives)
        val points = if (staged) blueprint.spawnPoints.filter { it.stage == 1 } else blueprint.spawnPoints
        val set = HashSet<UUID>()
        spawnPoints(anchor, points, set)
        spawnedMobs[anchor.id] = set
        blueprint.bossGate?.let { BossGateTracker.begin(anchor, it) }
    }

    fun spawnBoss(anchorId: String) {
        val anchor = AnchorManager.get(anchorId) ?: return
        val gate = BlueprintRegistry.get(anchor.templateId)?.bossGate ?: return
        val set = spawnedMobs.getOrPut(anchorId) { HashSet() }
        val base = anchor.location
        val sp = gate.boss
        val loc = base.clone().add(sp.pos.x + 0.5, sp.pos.y + 1.0, sp.pos.z + 0.5)
        repeat(sp.count.coerceAtMost(64)) {
            val entity = MobSpawner.spawn(sp.mob, loc, sp.level) ?: return@repeat
            (entity as? org.bukkit.entity.LivingEntity)?.removeWhenFarAway = false
            tag(entity, anchorId, sp.id, sp.mob)
            pdc.setString(entity.persistentDataContainer, "mob_boss", "true")
            set.add(entity.uniqueId)
        }
    }

    fun despawn(anchorId: String) {
        spawnedMobs.remove(anchorId)?.forEach { Bukkit.getEntity(it)?.remove() }
        populatedAt.remove(anchorId)
        ObjectiveTracker.clear(anchorId)
        BossGateTracker.clear(anchorId)
        com.qinhuai.ruins.mechanism.MechanismService.clear(anchorId)
    }

    fun spawnStage(anchorId: String, stage: Int) {
        val anchor = AnchorManager.get(anchorId) ?: return
        val blueprint = BlueprintRegistry.get(anchor.templateId) ?: return
        val set = spawnedMobs.getOrPut(anchorId) { HashSet() }
        spawnPoints(anchor, blueprint.spawnPoints.filter { it.stage == stage }, set)
    }

    private fun spawnPoints(anchor: Anchor, points: List<SpawnPoint>, set: MutableSet<UUID>) {
        val base = anchor.location
        for (sp in points) {
            val loc = base.clone().add(sp.pos.x + 0.5, sp.pos.y + 1.0, sp.pos.z + 0.5)
            repeat(sp.count.coerceAtMost(64)) {
                val entity = MobSpawner.spawn(sp.mob, loc, sp.level) ?: return@repeat
                (entity as? org.bukkit.entity.LivingEntity)?.removeWhenFarAway = false
                tag(entity, anchor.id, sp.id, sp.mob)
                set.add(entity.uniqueId)
            }
        }
    }

    private fun tag(entity: Entity, anchorId: String, spawnId: String, mobKey: String) {
        pdc.setString(entity.persistentDataContainer, "mob_anchor", anchorId)
        pdc.setString(entity.persistentDataContainer, "mob_spawn", spawnId)
        pdc.setString(entity.persistentDataContainer, "mob_key", mobKey)
    }
}
