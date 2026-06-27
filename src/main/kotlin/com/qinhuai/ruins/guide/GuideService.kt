package com.qinhuai.ruins.guide

import com.qinhuai.corelib.util.LocationUtils
import com.qinhuai.corelib.util.ServerCompat
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.data.DiscoveryStore
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.template.TemplateRegistry
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.time.Duration
import java.util.UUID

object GuideService {

    private var task: BukkitTask? = null
    private var run = 0L
    private var sessionFile: File? = null

    private var display = "title"
    private var discoverRadius = 48.0
    private var searchRadius = 10000.0
    private var exitAction = "swap"

    private var titleFmt = "§e◈ {ruin}"
    private var subtitleFmt = "§f{distance}m  §7{direction}"
    private var actionbarFmt = "§e{ruin} §f{distance}m  §7{direction}"
    private var notFoundFmt = "§7Target ruin temporarily lost…"
    private var otherWorldFmt = "§e{ruin} §7is in another world"
    private var searchFailFmt = "§cNo such ruin within {radius} blocks"
    private var confirmTitle = "Start guidance?"
    private var confirmYes = "§a✔ Confirm"
    private var confirmNo = "§c✘ Cancel"

    private var particleEnabled = true
    private var particleTypeName = "SOUL_FIRE_FLAME"
    private var particleType: Particle = Particle.FLAME
    private var particleIsDust = false
    private var particlePoints = 6
    private var particleSize = 1.0f
    private var particleMinDistance = 2.0
    private var particleNearDistance = 32.0
    private var nearColor = Color.fromRGB(0xFFD700)
    private var farColor = Color.AQUA

    data class GuideSession(val targetIsTemplate: Boolean, val targetId: String, val returnItem: ItemStack, val world: String)
    private val sessions = HashMap<UUID, GuideSession>()

    data class GuideInfo(val ruin: String, val distance: Int, val direction: String, val world: String)
    private val guideState = HashMap<UUID, GuideInfo>()

    fun guideInfo(uuid: UUID): GuideInfo? = guideState[uuid]
    fun isGuiding(uuid: UUID): Boolean = sessions.containsKey(uuid)
    fun exitAction(): String = exitAction
    fun confirmTitle(): String = confirmTitle
    fun confirmYesName(): String = confirmYes
    fun confirmNoName(): String = confirmNo

    fun start(plugin: JavaPlugin, config: FileConfiguration) {
        stop()
        sessionFile = File(plugin.dataFolder, "guide-sessions.yml")
        display = config.getString("guide.display") ?: "title"
        discoverRadius = config.getDouble("guide.discover-radius", 48.0)
        searchRadius = config.getDouble("guide.search-radius", config.getDouble("guide.max-search-radius", 10000.0))
        exitAction = (config.getString("guide.exit-action") ?: "swap").trim().lowercase()
        titleFmt = config.getString("guide.title") ?: titleFmt
        subtitleFmt = config.getString("guide.subtitle") ?: subtitleFmt
        actionbarFmt = config.getString("guide.actionbar") ?: actionbarFmt
        notFoundFmt = config.getString("guide.not-found") ?: notFoundFmt
        otherWorldFmt = config.getString("guide.other-world") ?: otherWorldFmt
        searchFailFmt = config.getString("guide.search-fail") ?: searchFailFmt
        confirmTitle = config.getString("guide.confirm.title") ?: confirmTitle
        confirmYes = config.getString("guide.confirm.yes-name") ?: confirmYes
        confirmNo = config.getString("guide.confirm.no-name") ?: confirmNo
        val refreshTicks = config.getLong("guide.refresh-ticks", 10L).coerceAtLeast(1L)

        particleEnabled = config.getBoolean("guide.particle.enabled", true)
        particleTypeName = config.getString("guide.particle.type") ?: "SOUL_FIRE_FLAME"
        resolveParticle()
        particlePoints = config.getInt("guide.particle.points", 6).coerceIn(1, 32)
        particleSize = config.getDouble("guide.particle.size", 1.0).toFloat()
        particleMinDistance = config.getDouble("guide.particle.min-distance", 2.0)
        particleNearDistance = config.getDouble("guide.particle.near-distance", 32.0)
        nearColor = colorOf(config.getString("guide.particle.near-color") ?: "GOLD")
        farColor = colorOf(config.getString("guide.particle.far-color") ?: "AQUA")

        loadSessions()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, refreshTicks, refreshTicks)
    }

    private fun resolveParticle() {
        val upper = particleTypeName.uppercase()
        particleIsDust = upper == "DUST" || upper == "REDSTONE" || upper == "DUST_COLOR_TRANSITION"
        particleType = ServerCompat.particle(upper, "FLAME")
    }

    fun stop() {
        task?.cancel()
        task = null
        guideState.clear()
    }

    fun startGuide(player: Player, item: ItemStack): Boolean {
        if (isGuiding(player.uniqueId)) {
            TextUtil.sendColored(player, Lang.get("gui.already-guiding"))
            return false
        }
        val target = GuideItem.readTarget(item) ?: return false
        val isTemplate = target is GuideTarget.TemplateRef
        val targetId = when (target) {
            is GuideTarget.TemplateRef -> target.templateId
            is GuideTarget.AnchorRef -> target.anchorId
        }
        val anchor = if (isTemplate) {
            val n = AnchorManager.nearestOfTemplate(player.location, targetId)
            if (n != null && LocationUtils.distance(n.location, player.location) <= searchRadius) n else null
        } else {
            AnchorManager.get(targetId)
        }
        if (anchor == null) {
            TextUtil.sendColored(player, searchFailFmt.replace("{radius}", searchRadius.toInt().toString()))
            return false
        }
        val one = item.clone().also { it.amount = 1 }
        if (player.inventory.removeItem(one.clone()).isNotEmpty()) {
            TextUtil.sendColored(player, Lang.get("gui.item-gone"))
            return false
        }
        sessions[player.uniqueId] = GuideSession(isTemplate, targetId, one, player.world.name)
        saveSessions()
        TextUtil.sendColored(player, Lang.get("gui.guide-started"))
        return true
    }

    fun stopGuide(uuid: UUID, returnItem: Boolean) {
        val session = sessions.remove(uuid) ?: return
        guideState.remove(uuid)
        saveSessions()
        val player = Bukkit.getPlayer(uuid) ?: return
        player.clearTitle()
        if (returnItem) {
            val leftover = player.inventory.addItem(session.returnItem)
            leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
            TextUtil.sendColored(player, Lang.get("gui.guide-cancelled"))
        }
    }

    fun adminClear(uuid: UUID): Boolean {
        if (!sessions.containsKey(uuid)) return false
        if (Bukkit.getPlayer(uuid) != null) {
            stopGuide(uuid, returnItem = true)
        } else {
            sessions.remove(uuid)
            saveSessions()
        }
        return true
    }

    private fun tick() {
        run++
        if (sessions.isEmpty()) return
        for ((uuid, session) in sessions.toList()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            val anchor = resolveSession(player, session)
            if (anchor == null) {
                guideState.remove(uuid)
                line(player, notFoundFmt)
                continue
            }
            update(player, anchor)
        }
    }

    private fun resolveSession(player: Player, session: GuideSession): Anchor? =
        if (session.targetIsTemplate) {
            val n = AnchorManager.nearestOfTemplate(player.location, session.targetId)
            if (n != null && LocationUtils.distance(n.location, player.location) <= searchRadius) n else null
        } else {
            AnchorManager.get(session.targetId)
        }

    private fun update(player: Player, anchor: Anchor) {
        val loc = anchor.center()
        val template = TemplateRegistry.get(anchor.templateId)
        val name = template?.display ?: anchor.templateId
        val world = loc.world?.name ?: ""
        if (player.world != loc.world) {
            guideState[player.uniqueId] = GuideInfo(name, 0, "", world)
            line(player, fmt(otherWorldFmt, name, 0.0, "", world))
            return
        }
        val dist = LocationUtils.distance(loc, player.location)
        val dir = Direction.compass(player.location, loc)
        guideState[player.uniqueId] = GuideInfo(name, dist.toInt(), dir, world)
        if (dist <= discoverRadius) {
            arrive(player, anchor, name)
            return
        }
        when {
            display.equals("none", ignoreCase = true) -> {}
            display.equals("title", ignoreCase = true) ->
                sendTitle(player, fmt(titleFmt, name, dist, dir, world), fmt(subtitleFmt, name, dist, dir, world))
            else -> player.sendActionBar(TextUtil.toComponent(fmt(actionbarFmt, name, dist, dir, world)))
        }
        if (particleEnabled && dist >= particleMinDistance) drawParticles(player, loc, dist)
    }

    private fun arrive(player: Player, anchor: Anchor, name: String) {
        DiscoveryStore.addTemplate(player.uniqueId, anchor.templateId)
        DiscoveryStore.add(player.uniqueId, anchor.id)
        stopGuide(player.uniqueId, returnItem = false)
        TextUtil.showColoredTitle(player, Lang.get("gui.arrived", "name" to name), 10, 40, 10)
        TextUtil.sendColored(player, Lang.get("gui.guide-ended"))
        ServerCompat.resolveSound("ENTITY_PLAYER_LEVELUP")?.let {
            player.playSound(player.location, it, 1.0f, 1.2f)
        }
    }

    private fun fmt(template: String, ruin: String, distance: Double, direction: String, world: String): String =
        template.replace("{ruin}", ruin)
            .replace("{distance}", distance.toInt().toString())
            .replace("{direction}", direction)
            .replace("{world}", world)

    private fun line(player: Player, text: String) {
        when {
            display.equals("none", ignoreCase = true) -> {}
            display.equals("title", ignoreCase = true) -> sendTitle(player, text, "")
            else -> player.sendActionBar(TextUtil.toComponent(text))
        }
    }

    private fun sendTitle(player: Player, title: String, subtitle: String) {
        player.showTitle(
            Title.title(
                TextUtil.toComponent(title),
                TextUtil.toComponent(subtitle),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200)),
            ),
        )
    }

    private fun drawParticles(player: Player, target: org.bukkit.Location, dist: Double) {
        val eye = player.eyeLocation
        val dir = target.toVector().subtract(eye.toVector())
        if (dir.lengthSquared() < 1.0E-6) return
        dir.normalize()
        val dust = if (particleIsDust) {
            Particle.DustOptions(if (dist <= particleNearDistance) nearColor else farColor, particleSize)
        } else {
            null
        }
        var step = 1.0
        var i = 0
        while (i < particlePoints) {
            val point = eye.clone().add(dir.clone().multiply(step))
            if (dust != null) {
                player.spawnParticle(particleType, point, 1, 0.0, 0.0, 0.0, 0.0, dust)
            } else {
                player.spawnParticle(particleType, point, 1, 0.0, 0.0, 0.0, 0.0)
            }
            step += 1.0
            i++
        }
    }

    private fun saveSessions() {
        val file = sessionFile ?: return
        val yml = YamlConfiguration()
        for ((uuid, s) in sessions) {
            val b = "sessions.$uuid"
            yml.set("$b.template", s.targetIsTemplate)
            yml.set("$b.target", s.targetId)
            yml.set("$b.world", s.world)
            yml.set("$b.item", s.returnItem)
        }
        runCatching { yml.save(file) }
    }

    private fun loadSessions() {
        sessions.clear()
        val file = sessionFile ?: return
        if (!file.exists()) return
        val yml = YamlConfiguration.loadConfiguration(file)
        yml.getConfigurationSection("sessions")?.getKeys(false)?.forEach { key ->
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
            val b = "sessions.$key"
            val item = yml.getItemStack("$b.item") ?: return@forEach
            val target = yml.getString("$b.target") ?: return@forEach
            sessions[uuid] = GuideSession(yml.getBoolean("$b.template"), target, item, yml.getString("$b.world") ?: "")
        }
    }

    private fun colorOf(name: String): Color = when (name.uppercase()) {
        "GOLD" -> Color.fromRGB(0xFFD700)
        "AQUA" -> Color.AQUA
        "RED" -> Color.RED
        "GREEN" -> Color.GREEN
        "BLUE" -> Color.BLUE
        "YELLOW" -> Color.YELLOW
        "ORANGE" -> Color.ORANGE
        "PURPLE" -> Color.PURPLE
        "LIME" -> Color.LIME
        "WHITE" -> Color.WHITE
        else -> Color.WHITE
    }
}
