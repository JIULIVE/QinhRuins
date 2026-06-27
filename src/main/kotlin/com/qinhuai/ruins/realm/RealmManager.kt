package com.qinhuai.ruins.realm

import com.qinhuai.corelib.util.ServerCompat
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.affix.AffixDefinition
import com.qinhuai.ruins.affix.AffixRegistry
import com.qinhuai.ruins.affix.AffixRoller
import com.qinhuai.ruins.affix.AffixRuntime
import com.qinhuai.ruins.affix.Keystone
import com.qinhuai.ruins.affix.TierConfig
import com.qinhuai.ruins.combat.AnchorActivation
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.AnchorState
import com.qinhuai.ruins.core.Blueprint
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.loot.RealmLoot
import com.qinhuai.ruins.template.BlueprintRegistry
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object RealmManager {

    private val realms = HashMap<String, ActiveRealm>()
    private var task: BukkitTask? = null

    private var activateRadius = 48.0
    private var abandonSeconds = 120L
    private var timeLimitSeconds = 0L
    private var nextTierChance = 0.2
    private var keystoneChance = 0.5
    private var consumeOnFail = false
    private var barColor = BarColor.PURPLE
    private var barStyle = BarStyle.SOLID

    fun start(plugin: JavaPlugin, config: FileConfiguration) {
        stop()
        activateRadius = config.getDouble("realm.activate-radius", 48.0)
        abandonSeconds = config.getLong("realm.abandon-seconds", 120L)
        timeLimitSeconds = com.qinhuai.ruins.core.Durations.seconds(config.getString("realm.time-limit"))
        nextTierChance = config.getDouble("realm.next-tier-chance", 0.2)
        keystoneChance = config.getDouble("realm.keystone-chance", 0.5)
        consumeOnFail = config.getBoolean("realm.failure.consume-keystone", false)
        barColor = runCatching { BarColor.valueOf((config.getString("realm.bossbar-color") ?: "PURPLE").uppercase()) }.getOrDefault(BarColor.PURPLE)
        barStyle = runCatching { BarStyle.valueOf((config.getString("realm.bossbar-style") ?: "SEGMENTED_10").uppercase()) }.getOrDefault(BarStyle.SEGMENTED_10)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
    }

    fun stop() {
        task?.cancel()
        task = null
        for (realm in realms.values.toList()) end(realm, cleared = false, announce = false)
        realms.clear()
    }

    fun isRealm(anchorId: String): Boolean = realms.containsKey(anchorId)

    fun tierOf(anchorId: String): Int? = realms[anchorId]?.tier

    fun realmNear(location: org.bukkit.Location): ActiveRealm? {
        if (realms.isEmpty()) return null
        for (realm in realms.values) {
            val world = realm.zoneCenter.world ?: continue
            if (location.world == world && location.distanceSquared(realm.zoneCenter) <= realm.zoneRadius * realm.zoneRadius) return realm
        }
        return null
    }

    fun aliveMobs(realm: ActiveRealm): Int =
        realm.mobs.count { Bukkit.getEntity(it)?.let { e -> !e.isDead } == true }

    fun remainingSeconds(realm: ActiveRealm): Long {
        if (realm.timeLimitSeconds <= 0) return -1L
        val elapsed = (System.currentTimeMillis() - realm.startedAtMillis) / 1000
        return (realm.timeLimitSeconds - elapsed).coerceAtLeast(0L)
    }

    fun isUnderNoHeal(player: Player): Boolean {
        if (realms.isEmpty()) return false
        for (realm in realms.values) {
            if (!AffixRuntime.hasNoHeal(realm.affixes)) continue
            if (player.world == realm.zoneCenter.world &&
                player.location.distanceSquared(realm.zoneCenter) <= realm.zoneRadius * realm.zoneRadius
            ) return true
        }
        return false
    }

    fun tryActivate(player: Player) {
        if (findKeystone(player) == null) {
            TextUtil.sendColored(player, Lang.get("realm.need-keystone"))
            return
        }
        val anchor = AnchorManager.containing(player.location)?.takeIf { !isRealm(it.id) }
            ?: AnchorManager.nearby(player.location, activateRadius).firstOrNull { !isRealm(it.id) }
        if (anchor == null) {
            val nearest = AnchorManager.nearby(player.location, activateRadius * 8).firstOrNull { !isRealm(it.id) }
            if (nearest != null) {
                val d = nearest.location.distance(player.location).toInt()
                TextUtil.sendColored(player, Lang.get("realm.nearest-ruin", "dist" to d, "radius" to activateRadius.toInt()))
            } else {
                TextUtil.sendColored(player, Lang.get("realm.no-ruin-nearby"))
            }
            return
        }
        tryActivateAt(player, anchor)
    }

    fun tryActivateAt(player: Player, anchor: Anchor) {
        val keystone = findKeystone(player)
        if (keystone == null) {
            TextUtil.sendColored(player, Lang.get("realm.need-keystone"))
            return
        }
        if (isRealm(anchor.id)) {
            TextUtil.sendColored(player, Lang.get("realm.already-realm"))
            return
        }
        if (anchor.state == AnchorState.CLEARED) {
            TextUtil.sendColored(player, Lang.get("realm.already-explored"))
            return
        }
        val tier = Keystone.tierOf(keystone) ?: return
        val blueprint = BlueprintRegistry.get(anchor.templateId)
        if (blueprint == null || blueprint.spawnPoints.isEmpty()) {
            TextUtil.sendColored(player, Lang.get("realm.no-spawn-points"))
            return
        }
        val preset = Keystone.affixIdsOf(keystone).mapNotNull { AffixRegistry.get(it) }
        consumeKeystone(player)
        activate(anchor, blueprint, tier, preset)
    }

    private fun activate(anchor: Anchor, blueprint: Blueprint, tier: Int, presetAffixes: List<AffixDefinition>) {
        AnchorActivation.despawn(anchor.id)
        val affixes = presetAffixes.ifEmpty { AffixRoller.roll(tier) }
        val level = TierConfig.mobLevel(tier) + AffixRuntime.mobLevelBonus(affixes)
        val countMult = AffixRuntime.mobCountMultiplier(affixes)
        val mobs = spawnRealmMobs(anchor, blueprint, level, countMult)
        val bar = createBossBar(anchor.templateId, tier, affixes)
        val now = System.currentTimeMillis()
        val limit = AffixRuntime.timeLimitSeconds(affixes).takeIf { it > 0 } ?: timeLimitSeconds
        realms[anchor.id] = ActiveRealm(
            anchorId = anchor.id,
            templateId = anchor.templateId,
            tier = tier,
            affixes = affixes,
            startedAtMillis = now,
            mobs = mobs,
            totalMobs = mobs.size,
            bossBar = bar,
            timeLimitSeconds = limit,
            lastSeenPlayersAtMillis = now,
            zoneCenter = realmCenter(anchor),
            zoneRadius = realmRadius(anchor),
        )
        AnchorManager.setState(anchor.id, AnchorState.ACTIVE)
        announceActivate(anchor, tier, affixes)
        val ruinName = TemplateRegistry.get(anchor.templateId)?.display ?: anchor.templateId
        AffixRuntime.broadcastMessages(affixes, ruinName, tier).forEach { msg ->
            Bukkit.getOnlinePlayers().forEach { TextUtil.sendColored(it, msg) }
        }
    }

    private fun realmCenter(anchor: Anchor): org.bukkit.Location =
        if (anchor.width > 0 && anchor.depth > 0) {
            anchor.location.clone().add(anchor.width / 2.0, (anchor.height / 2.0).coerceAtLeast(1.0), anchor.depth / 2.0)
        } else {
            anchor.location.clone()
        }

    private fun realmRadius(anchor: Anchor): Double {
        if (anchor.width <= 0 || anchor.depth <= 0) return activateRadius
        val half = Math.hypot(anchor.width.toDouble(), anchor.depth.toDouble()) / 2.0
        return (half + 16.0).coerceAtLeast(activateRadius)
    }

    private fun tick() {
        if (realms.isEmpty()) return
        val now = System.currentTimeMillis()
        val toEnd = ArrayList<Pair<ActiveRealm, Boolean>>()
        for (realm in realms.values) {
            val world = realm.zoneCenter.world ?: continue
            val nearby = world.getNearbyPlayers(realm.zoneCenter, realm.zoneRadius)
            refreshBossBar(realm, nearby)
            nearby.forEach {
                AffixRuntime.applyPlayerStatus(realm.affixes, it, 40)
                realm.participants.add(it.uniqueId)
            }
            if (nearby.isNotEmpty()) realm.lastSeenPlayersAtMillis = now

            val alive = realm.mobs.count { Bukkit.getEntity(it)?.let { e -> !e.isDead } == true }
            updateBossProgress(realm, alive)

            when {
                realm.totalMobs > 0 && alive == 0 -> toEnd.add(realm to true)
                realm.timeLimitSeconds > 0 && (now - realm.startedAtMillis) >= realm.timeLimitSeconds * 1000 -> toEnd.add(realm to false)
                now - realm.lastSeenPlayersAtMillis >= abandonSeconds * 1000 -> toEnd.add(realm to false)
            }
        }
        toEnd.forEach { (realm, cleared) -> end(realm, cleared, announce = true) }
    }

    private fun end(realm: ActiveRealm, cleared: Boolean, announce: Boolean) {
        realms.remove(realm.anchorId)
        for (uuid in realm.mobs) Bukkit.getEntity(uuid)?.remove()
        realm.bossBar?.removeAll()
        AnchorManager.setState(realm.anchorId, if (cleared) AnchorState.CLEARED else AnchorState.DORMANT)
        if (cleared) reward(realm)
        if (announce && !cleared && !consumeOnFail) {
            realm.zoneCenter.world?.dropItemNaturally(realm.zoneCenter, Keystone.build(realm.tier, null))
        }
        if (announce) {
            val template = TemplateRegistry.get(realm.templateId)
            val name = template?.display ?: realm.templateId
            realm.zoneCenter.world?.getNearbyPlayers(realm.zoneCenter, realm.zoneRadius)?.forEach { player ->
                if (cleared) {
                    val title = (template?.titles?.clear ?: Lang.get("realm.clear-title", "ruin" to name)).replace("{ruin}", name)
                    TextUtil.showColoredTitle(player, title, 10, 50, 10)
                    TextUtil.sendColored(player, Lang.get("realm.purified", "name" to name, "tier" to realm.tier))
                } else {
                    TextUtil.sendColored(player, Lang.get("realm.barrier-faded", "name" to name))
                }
            }
        }
    }

    private fun reward(realm: ActiveRealm) {
        for (uuid in realm.participants) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            RealmLoot.grant(player, realm.tier, realm.affixes)
            if (Math.random() < keystoneChance) giveKeystone(player, realm.tier)
            if (realm.tier < TierConfig.maxTier && Math.random() < nextTierChance) giveKeystone(player, realm.tier + 1)
        }
    }

    private fun giveKeystone(player: Player, tier: Int) {
        val overflow = player.inventory.addItem(Keystone.build(tier, player))
        overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }
        TextUtil.sendColored(player, Lang.get("realm.keystone-gained", "tier" to tier))
    }

    private fun spawnRealmMobs(anchor: Anchor, blueprint: Blueprint, level: Int, countMult: Double): MutableSet<UUID> {
        val base = anchor.location
        val set = HashSet<UUID>()
        for (sp in blueprint.spawnPoints) {
            val loc = base.clone().add(sp.pos.x + 0.5, sp.pos.y + 1.0, sp.pos.z + 0.5)
            val count = Math.round(sp.count * countMult).toInt().coerceAtLeast(1)
            repeat(count) {
                val entity = com.qinhuai.ruins.combat.MobSpawner.spawn(sp.mob, loc, level) ?: return@repeat
                (entity as? LivingEntity)?.removeWhenFarAway = false
                set.add(entity.uniqueId)
            }
        }
        return set
    }

    private fun createBossBar(templateId: String, tier: Int, affixes: List<com.qinhuai.ruins.affix.AffixDefinition>): BossBar {
        val name = TemplateRegistry.get(templateId)?.display ?: templateId
        val affixText = affixes.joinToString(" ") { it.name }
        val title = TextUtil.colored(Lang.get("realm.bossbar-title", "tier" to tier, "name" to name, "affixes" to affixText))
        return Bukkit.createBossBar(title, barColor, barStyle).also { it.progress = 1.0 }
    }

    private fun refreshBossBar(realm: ActiveRealm, nearby: Collection<Player>) {
        val bar = realm.bossBar ?: return
        val current = bar.players
        nearby.forEach { if (it !in current) bar.addPlayer(it) }
        current.forEach { if (it !in nearby) bar.removePlayer(it) }
    }

    private fun updateBossProgress(realm: ActiveRealm, alive: Int) {
        val bar = realm.bossBar ?: return
        if (realm.totalMobs > 0) bar.progress = (alive.toDouble() / realm.totalMobs).coerceIn(0.0, 1.0)
    }

    private fun announceActivate(anchor: Anchor, tier: Int, affixes: List<com.qinhuai.ruins.affix.AffixDefinition>) {
        val name = TemplateRegistry.get(anchor.templateId)?.display ?: anchor.templateId
        val center = realmCenter(anchor)
        center.world?.getNearbyPlayers(center, realmRadius(anchor))?.forEach { player ->
            TextUtil.showColoredTitle(player, Lang.get("realm.awaken-title", "tier" to tier), 10, 50, 10)
            TextUtil.sendColored(player, Lang.get("realm.infused", "name" to name))
            affixes.forEach { TextUtil.sendColored(player, "  §8» ${it.name}") }
            ServerCompat.resolveSound("ENTITY_WITHER_SPAWN")?.let { player.playSound(player.location, it, 0.6f, 1.4f) }
            AffixRuntime.runCommands(affixes, player, tier)
            AffixRuntime.runScripts(affixes, player, com.qinhuai.ruins.QinhRuins.instance, tier)
        }
    }

    private fun findKeystone(player: Player): ItemStack? {
        val main = player.inventory.itemInMainHand
        if (Keystone.tierOf(main) != null) return main
        val off = player.inventory.itemInOffHand
        if (Keystone.tierOf(off) != null) return off
        return null
    }

    private fun consumeKeystone(player: Player) {
        val inventory = player.inventory
        val main = inventory.itemInMainHand
        if (Keystone.tierOf(main) != null) {
            inventory.setItemInMainHand(shrink(main))
            return
        }
        val off = inventory.itemInOffHand
        if (Keystone.tierOf(off) != null) inventory.setItemInOffHand(shrink(off))
    }

    private fun shrink(item: ItemStack): ItemStack? =
        if (item.amount <= 1) null else item.clone().also { it.amount = item.amount - 1 }
}
