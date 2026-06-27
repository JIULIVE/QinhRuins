package com.qinhuai.ruins.editor

import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.core.MechAction
import com.qinhuai.ruins.core.MechActionType
import com.qinhuai.ruins.core.MechRegion
import com.qinhuai.ruins.core.MechTriggerType
import com.qinhuai.ruins.core.RelPos
import org.bukkit.entity.Player

object EditorMechCommands {

    private val coordKeys = setOf("fx", "fy", "fz", "tx", "ty", "tz", "x", "y", "z")

    fun handle(player: Player, raw: String) {
        val session = EditorManager.get(player) ?: return notEditing(player)
        val parts = raw.trim().split(" ", limit = 2)
        val sub = parts.getOrNull(0)?.lowercase().orEmpty()
        val rest = parts.getOrNull(1)?.trim().orEmpty()
        when (sub) {
            "new" -> newBuilder(player, session, rest)
            "trigger" -> trigger(player, session, rest)
            "action" -> action(player, session, rest)
            "flag" -> flag(player, session, rest)
            "points" -> points(player, session)
            "clearpoints" -> clearPoints(player, session)
            "done" -> done(player, session)
            "discard" -> discard(player, session)
            else -> help(player)
        }
    }

    private fun newBuilder(player: Player, session: EditorSession, id: String) {
        if (id.isBlank()) return Lang.send(player, "editor.mech-new-usage")
        session.screen = EditorScreen.MECH
        session.mode = EditorMode.MECH
        session.mechBuilder = MechBuilder(id)
        EditorTools.equip(player, session)
        Lang.send(player, "editor.mech-new-start", "id" to id)
        Lang.send(player, "editor.mech-new-trigger")
        Lang.send(player, "editor.mech-new-action")
        Lang.send(player, "editor.mech-new-done")
    }

    private fun trigger(player: Player, session: EditorSession, rest: String) {
        val builder = session.mechBuilder ?: return noBuilder(player)
        val args = rest.split(" ").filter { it.isNotBlank() }
        when (args.getOrNull(0)?.lowercase()) {
            "interact" -> {
                val pos = builder.points.lastOrNull()
                    ?: return Lang.send(player, "editor.cmd-need-interact-block")
                builder.triggerType = MechTriggerType.INTERACT
                builder.interactPos = pos
                Lang.send(player, "editor.cmd-trig-interact", "x" to pos.x, "y" to pos.y, "z" to pos.z)
            }
            "break" -> {
                val pos = builder.points.lastOrNull()
                    ?: return Lang.send(player, "editor.cmd-need-break-block")
                builder.triggerType = MechTriggerType.BLOCK_BREAK
                builder.interactPos = pos
                Lang.send(player, "editor.cmd-trig-break", "x" to pos.x, "y" to pos.y, "z" to pos.z)
            }
            "redstone" -> {
                val pos = builder.points.lastOrNull()
                    ?: return Lang.send(player, "editor.cmd-need-redstone-block")
                builder.triggerType = MechTriggerType.REDSTONE
                builder.interactPos = pos
                Lang.send(player, "editor.cmd-trig-redstone", "x" to pos.x, "y" to pos.y, "z" to pos.z)
            }
            "region" -> {
                if (builder.points.size < 2) return Lang.send(player, "editor.cmd-need-two-corners")
                builder.triggerType = MechTriggerType.REGION_ENTER
                builder.regionFrom = builder.points[builder.points.size - 2]
                builder.regionTo = builder.points.last()
                Lang.send(player, "editor.cmd-trig-region")
            }
            "stage" -> {
                val n = args.getOrNull(1)?.toIntOrNull() ?: return Lang.send(player, "editor.cmd-trigger-stage-usage")
                builder.triggerType = MechTriggerType.STAGE
                builder.stage = n
                Lang.send(player, "editor.cmd-trig-stage", "n" to n)
            }
            "timer" -> {
                val n = args.getOrNull(1)?.toLongOrNull() ?: return Lang.send(player, "editor.cmd-trigger-timer-usage")
                builder.triggerType = MechTriggerType.TIMER
                builder.interval = n
                Lang.send(player, "editor.cmd-trig-timer", "n" to n)
            }
            else -> Lang.send(player, "editor.cmd-trigger-types")
        }
    }

    private fun action(player: Player, session: EditorSession, rest: String) {
        val builder = session.mechBuilder ?: return noBuilder(player)
        val typeToken = rest.split(" ").firstOrNull()?.uppercase()?.replace('-', '_')
        val type = MechActionType.entries.firstOrNull { it.name == typeToken }
            ?: return Lang.send(player, "editor.cmd-action-types")
        val argString = rest.substringAfter(" ", "")
        val parsed = parse(argString)
        val region = resolveRegion(parsed, builder)
        val pos = resolvePos(parsed, builder)
        val params = parsed.params.filterKeys { it !in coordKeys }
        builder.actions.add(MechAction(type, params, region, pos))
        Lang.send(player, "editor.cmd-action-added", "type" to type.name.lowercase(), "count" to builder.actions.size)
    }

    private fun flag(player: Player, session: EditorSession, rest: String) {
        val builder = session.mechBuilder ?: return noBuilder(player)
        val args = rest.split(" ").filter { it.isNotBlank() }
        val value = args.getOrNull(1)
        when (args.getOrNull(0)?.lowercase()) {
            "once" -> builder.once = value?.toBoolean() ?: true
            "cooldown" -> builder.cooldown = value?.toLongOrNull() ?: return invalid(player)
            "require-stage" -> builder.requireStage = value?.toIntOrNull() ?: return invalid(player)
            "radius" -> builder.radius = value?.toDoubleOrNull() ?: return invalid(player)
            else -> return Lang.send(player, "editor.cmd-flag-usage")
        }
        Lang.send(player, "editor.cmd-flag-set", "flag" to args[0], "value" to (value ?: "true"))
    }

    private fun points(player: Player, session: EditorSession) {
        val builder = session.mechBuilder ?: return noBuilder(player)
        if (builder.points.isEmpty()) return Lang.send(player, "editor.cmd-no-points")
        Lang.send(player, "editor.cmd-points-header", "count" to builder.points.size)
        builder.points.forEachIndexed { i, p -> Lang.send(player, "editor.cmd-point-entry", "n" to (i + 1), "x" to p.x, "y" to p.y, "z" to p.z) }
    }

    private fun clearPoints(player: Player, session: EditorSession) {
        val builder = session.mechBuilder ?: return noBuilder(player)
        builder.points.clear()
        Lang.send(player, "editor.points-cleared")
    }

    private fun done(player: Player, session: EditorSession) {
        val builder = session.mechBuilder ?: return noBuilder(player)
        val mechanism = builder.build()
        if (mechanism == null) {
            val reason = when {
                builder.triggerType == null -> Lang.get("editor.reason-no-trigger-cmd")
                builder.actions.isEmpty() -> Lang.get("editor.reason-no-action-cmd")
                builder.triggerType in listOf(MechTriggerType.INTERACT, MechTriggerType.BLOCK_BREAK, MechTriggerType.REDSTONE)
                    && builder.interactPos == null -> Lang.get("editor.reason-no-trigger-block")
                else -> Lang.get("editor.reason-incomplete-region")
            }
            Lang.send(player, "editor.mech-incomplete", "reason" to reason)
            return
        }
        session.mechanisms.removeIf { it.id == mechanism.id }
        session.mechanisms.add(mechanism)
        session.mechBuilder = null
        EditorManager.refreshTools(player)
        Lang.send(player, "editor.mech-added-cmd", "id" to mechanism.id)
    }

    private fun discard(player: Player, session: EditorSession) {
        session.mechBuilder = null
        EditorManager.refreshTools(player)
        Lang.send(player, "editor.mech-discarded")
    }

    private data class Parsed(val params: Map<String, String>, val flags: Set<String>)

    private fun parse(raw: String): Parsed {
        val params = LinkedHashMap<String, String>()
        val flags = HashSet<String>()
        var key: String? = null
        val sb = StringBuilder()
        fun flush() {
            val k = key ?: return
            params[k] = sb.toString().trim()
            key = null
            sb.setLength(0)
        }
        for (token in raw.split(" ").filter { it.isNotBlank() }) {
            val eq = token.indexOf('=')
            if (eq > 0 && token.substring(0, eq).all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                flush()
                key = token.substring(0, eq)
                sb.append(token.substring(eq + 1))
            } else if (key == null) {
                flags.add(token.lowercase())
            } else {
                sb.append(' ').append(token)
            }
        }
        flush()
        return Parsed(params, flags)
    }

    private fun resolveRegion(parsed: Parsed, builder: MechBuilder): MechRegion? {
        if ("region" in parsed.flags && builder.points.size >= 2) {
            return MechRegion(builder.points[builder.points.size - 2], builder.points.last())
        }
        val p = parsed.params
        if (p.keys.containsAll(listOf("fx", "fy", "fz", "tx", "ty", "tz"))) {
            return MechRegion(
                RelPos(p.getValue("fx").toInt(), p.getValue("fy").toInt(), p.getValue("fz").toInt()),
                RelPos(p.getValue("tx").toInt(), p.getValue("ty").toInt(), p.getValue("tz").toInt()),
            )
        }
        return null
    }

    private fun resolvePos(parsed: Parsed, builder: MechBuilder): RelPos? {
        if ("pos" in parsed.flags && builder.points.isNotEmpty()) return builder.points.last()
        val p = parsed.params
        if (p.keys.containsAll(listOf("x", "y", "z"))) {
            return RelPos(p.getValue("x").toInt(), p.getValue("y").toInt(), p.getValue("z").toInt())
        }
        return null
    }

    private fun help(player: Player) {
        listOf(
            Lang.get("editor.help-title"),
            Lang.get("editor.help-new"),
            Lang.get("editor.help-trigger-interact"),
            Lang.get("editor.help-trigger-break"),
            Lang.get("editor.help-trigger-redstone"),
            Lang.get("editor.help-trigger-region"),
            Lang.get("editor.help-trigger-stage-timer"),
            Lang.get("editor.help-action"),
            Lang.get("editor.help-action-coords"),
            Lang.get("editor.help-flag"),
            Lang.get("editor.help-points"),
        ).forEach { TextUtil.sendColored(player, it) }
    }

    private fun noBuilder(player: Player) =
        Lang.send(player, "editor.cmd-no-builder")

    private fun notEditing(player: Player) =
        Lang.send(player, "editor.not-editing")

    private fun invalid(player: Player) =
        Lang.send(player, "editor.invalid-value")
}
