package com.qinhuai.ruins.session

import com.qinhuai.corelib.util.ServerCompat
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.AnchorState
import com.qinhuai.ruins.core.Durations
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object SessionManager {

    private val byAnchor = HashMap<String, Session>()
    private val byPlayer = HashMap<UUID, String>()
    private var task: BukkitTask? = null

    private var bossBarEnabled = true
    private var barColor = BarColor.PURPLE
    private var barStyle = BarStyle.SOLID

    fun start(plugin: JavaPlugin, config: FileConfiguration) {
        stop()
        bossBarEnabled = config.getBoolean("session.bossbar", true)
        barColor = runCatching { BarColor.valueOf((config.getString("session.bossbar-color") ?: "PURPLE").uppercase()) }
            .getOrDefault(BarColor.PURPLE)
        barStyle = runCatching { BarStyle.valueOf((config.getString("session.bossbar-style") ?: "SOLID").uppercase()) }
            .getOrDefault(BarStyle.SOLID)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
    }

    fun stop() {
        task?.cancel()
        task = null
        for (session in byAnchor.values.toList()) {
            end(session, completed = false, recordState = false)
        }
        byAnchor.clear()
        byPlayer.clear()
    }

    fun isAnchorBusy(anchorId: String): Boolean = byAnchor.containsKey(anchorId)

    fun activeCount(): Int = byAnchor.size

    fun sessionOf(player: Player): Session? = byPlayer[player.uniqueId]?.let { byAnchor[it] }

    fun create(template: RuinTemplate, anchor: Anchor, leader: UUID, members: Collection<Player>): Session {
        val bar = if (bossBarEnabled) {
            Bukkit.createBossBar(TextUtil.colored(template.display), barColor, barStyle).also { it.progress = 1.0 }
        } else null

        val session = Session(
            id = "${anchor.id}#${System.currentTimeMillis().toString().takeLast(5)}",
            anchorId = anchor.id,
            templateId = template.id,
            worldName = anchor.location.world?.name ?: "world",
            members = members.map { it.uniqueId }.toMutableSet(),
            leader = leader,
            startedAtMillis = System.currentTimeMillis(),
            timeLimitSeconds = template.session.timeLimitSeconds,
            cleanupOnEmptySeconds = template.session.cleanupOnEmptySeconds,
            bossBar = bar,
        )
        byAnchor[anchor.id] = session
        AnchorManager.setState(anchor.id, AnchorState.ACTIVE)

        val timeText = Durations.format(session.timeLimitSeconds)
        for (player in members) {
            byPlayer[player.uniqueId] = anchor.id
            bar?.addPlayer(player)
            TextUtil.showColoredTitle(
                player,
                Lang.get("messages.enter-title", "ruin" to template.display),
                10, 40, 10,
            )
            TextUtil.sendColored(
                player,
                Lang.get(
                    "messages.enter-subtitle",
                    "players" to members.size.toString(), "time" to timeText,
                ),
            )
            playSound(player, "ENTITY_PLAYER_LEVELUP", 1.0f)
        }
        return session
    }

    fun leave(player: Player) {
        val anchorId = byPlayer.remove(player.uniqueId)
        if (anchorId == null) {
            TextUtil.sendColored(player, Lang.get("core.not-in-ruin"))
            return
        }
        val session = byAnchor[anchorId] ?: return
        session.members.remove(player.uniqueId)
        session.bossBar?.removePlayer(player)
        TextUtil.sendColored(player, Lang.get("messages.exit"))
        if (session.members.isEmpty()) session.emptySinceMillis = System.currentTimeMillis()
    }

    fun handleQuit(player: Player) {
        val anchorId = byPlayer.remove(player.uniqueId) ?: return
        val session = byAnchor[anchorId] ?: return
        session.members.remove(player.uniqueId)
        session.bossBar?.removePlayer(player)
        if (session.members.isEmpty()) session.emptySinceMillis = System.currentTimeMillis()
    }

    private fun end(session: Session, completed: Boolean, recordState: Boolean) {
        byAnchor.remove(session.anchorId)
        for (uuid in session.members.toList()) {
            byPlayer.remove(uuid)
            Bukkit.getPlayer(uuid)?.let { player ->
                session.bossBar?.removePlayer(player)
                val msg = if (completed) Lang.get("messages.complete")
                else Lang.get("messages.exit")
                TextUtil.sendColored(player, msg)
            }
        }
        session.members.clear()
        session.bossBar?.removeAll()
        if (recordState) AnchorManager.setState(session.anchorId, AnchorState.DORMANT)
    }

    private fun tick() {
        if (byAnchor.isEmpty()) return
        val now = System.currentTimeMillis()
        val toEnd = ArrayList<Pair<Session, Boolean>>()
        for (session in byAnchor.values) {
            if (session.timeLimitSeconds > 0 && (now - session.startedAtMillis) >= session.timeLimitSeconds * 1000) {
                toEnd.add(session to false)
                continue
            }
            val emptySince = session.emptySinceMillis
            if (session.members.isEmpty() && emptySince != null &&
                now - emptySince >= session.cleanupOnEmptySeconds * 1000
            ) {
                toEnd.add(session to false)
                continue
            }
            if (session.timeLimitSeconds > 0) {
                val elapsed = (now - session.startedAtMillis) / 1000.0
                val fraction = (1.0 - elapsed / session.timeLimitSeconds).coerceIn(0.0, 1.0)
                session.bossBar?.progress = fraction
                session.bossBar?.setTitle(bossTitle(session))
            }
        }
        toEnd.forEach { (session, completed) -> end(session, completed, recordState = true) }
    }

    private fun bossTitle(session: Session): String {
        val name = TemplateRegistry.get(session.templateId)?.display ?: session.templateId
        val remain = (session.timeLimitSeconds - (System.currentTimeMillis() - session.startedAtMillis) / 1000).coerceAtLeast(0)
        return TextUtil.colored("$name §7- §f${Durations.format(remain)}")
    }

    private fun playSound(player: Player, name: String, pitch: Float) {
        ServerCompat.resolveSound(name)?.let { player.playSound(player.location, it, 1.0f, pitch) }
    }
}
