package com.qinhuai.ruins.editor

import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.core.MechAction
import com.qinhuai.ruins.core.MechActionType
import com.qinhuai.ruins.core.MechRegion
import com.qinhuai.ruins.core.MechTriggerType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent

object EditorGuiListener : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? EditorHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val session = EditorManager.get(player) ?: return
        val clicked = event.currentItem ?: return
        val inv = holder.inv
        when (holder.kind) {
            EditorGuiKind.LIST -> handleList(player, session, clicked, inv)
            EditorGuiKind.MECH -> handleMech(player, session, EditorGui.buttonId(clicked) ?: return, event.click, inv)
        }
    }

    private fun handleList(player: Player, session: EditorSession, clicked: org.bukkit.inventory.ItemStack, inv: org.bukkit.inventory.Inventory) {
        if (EditorGui.buttonId(clicked) == "close") {
            player.closeInventory()
            return
        }
        val id = EditorGui.entryId(clicked) ?: return
        if (session.remove(id)) {
            Lang.send(player, "editor.marker-deleted", "id" to id)
            EditorManager.refreshTools(player)
            EditorGui.fillList(inv, session)
        }
    }

    private fun handleMech(player: Player, session: EditorSession, button: String, click: ClickType, inv: org.bukkit.inventory.Inventory) {
        val builder = session.mechBuilder
        if (builder == null) {
            Lang.send(player, "editor.mech-ended")
            player.closeInventory()
            return
        }
        when (button) {
            "t_interact" -> setPointTrigger(player, builder, MechTriggerType.INTERACT, Lang.get("editor.trig-interact"))
            "t_break" -> setPointTrigger(player, builder, MechTriggerType.BLOCK_BREAK, Lang.get("editor.trig-break"))
            "t_redstone" -> setPointTrigger(player, builder, MechTriggerType.REDSTONE, Lang.get("editor.trig-redstone"))
            "t_region" -> {
                if (builder.points.size < 2) {
                    Lang.send(player, "editor.need-two-corners")
                    return
                }
                builder.triggerType = MechTriggerType.REGION_ENTER
                builder.regionFrom = builder.points[builder.points.size - 2]
                builder.regionTo = builder.points.last()
                Lang.send(player, "editor.trig-region-set")
            }
            "t_stage" -> {
                builder.triggerType = MechTriggerType.STAGE
                builder.stage = step(if (builder.stage <= 0) 1 else builder.stage, click, 1)
            }
            "t_timer" -> {
                builder.triggerType = MechTriggerType.TIMER
                builder.interval = step(if (builder.interval <= 0L) 5 else builder.interval.toInt(), click, 1).toLong()
            }
            "a_spawn" -> {
                val pos = builder.points.lastOrNull()
                if (pos == null) {
                    Lang.send(player, "editor.need-spawn-pos")
                    return
                }
                builder.actions.add(MechAction(MechActionType.SPAWN, mapOf("mob" to session.defaultMob, "count" to session.defaultCount.toString()), null, pos))
                Lang.send(player, "editor.action-spawn-added", "mob" to session.defaultMob, "count" to session.defaultCount)
            }
            "a_fill" -> {
                if (builder.points.size < 2) {
                    Lang.send(player, "editor.need-two-corners-region")
                    return
                }
                val region = MechRegion(builder.points[builder.points.size - 2], builder.points.last())
                player.closeInventory()
                ChatInputService.await(player, Lang.get("editor.ask-fill-material")) { input ->
                    builder.actions.add(MechAction(MechActionType.FILL, mapOf("material" to input.uppercase()), region, null))
                    Lang.send(player, "editor.action-fill-added", "material" to input.uppercase())
                    EditorGui.openMech(player, session)
                }
                return
            }
            "a_message" -> {
                player.closeInventory()
                ChatInputService.await(player, Lang.get("editor.ask-broadcast-text")) { input ->
                    builder.actions.add(MechAction(MechActionType.MESSAGE, mapOf("text" to input), null, null))
                    Lang.send(player, "editor.action-message-added")
                    EditorGui.openMech(player, session)
                }
                return
            }
            "a_command" -> {
                player.closeInventory()
                ChatInputService.await(player, Lang.get("editor.ask-command")) { input ->
                    builder.actions.add(MechAction(MechActionType.COMMAND, mapOf("command" to input), null, null))
                    Lang.send(player, "editor.action-command-added")
                    EditorGui.openMech(player, session)
                }
                return
            }
            "clearpoints" -> {
                builder.points.clear()
                Lang.send(player, "editor.points-cleared")
            }
            "done" -> {
                doneMech(player, session)
                return
            }
            "discard" -> {
                session.mechBuilder = null
                EditorManager.refreshTools(player)
                player.closeInventory()
                Lang.send(player, "editor.mech-discarded")
                return
            }
            else -> return
        }
        EditorGui.fillMech(inv, session)
    }

    private fun setPointTrigger(player: Player, builder: MechBuilder, type: MechTriggerType, label: String) {
        val pos = builder.points.lastOrNull()
        if (pos == null) {
            Lang.send(player, "editor.need-target-block")
            return
        }
        builder.triggerType = type
        builder.interactPos = pos
        Lang.send(player, "editor.trigger-set-pos", "label" to label, "x" to pos.x, "y" to pos.y, "z" to pos.z)
    }

    private fun doneMech(player: Player, session: EditorSession) {
        val builder = session.mechBuilder ?: return
        val mechanism = builder.build()
        if (mechanism == null) {
            val reason = when {
                builder.triggerType == null -> Lang.get("editor.reason-no-trigger-gui")
                builder.actions.isEmpty() -> Lang.get("editor.reason-no-action-gui")
                else -> Lang.get("editor.reason-incomplete-pos")
            }
            Lang.send(player, "editor.mech-incomplete", "reason" to reason)
            return
        }
        session.mechanisms.removeIf { it.id == mechanism.id }
        session.mechanisms.add(mechanism)
        session.mechBuilder = null
        EditorManager.refreshTools(player)
        player.closeInventory()
        Lang.send(player, "editor.mech-added-gui", "id" to mechanism.id)
    }

    private fun step(current: Int, click: ClickType, min: Int): Int {
        val delta = when (click) {
            ClickType.LEFT -> 1
            ClickType.SHIFT_LEFT -> 10
            ClickType.RIGHT -> -1
            ClickType.SHIFT_RIGHT -> -10
            else -> 0
        }
        return (current + delta).coerceAtLeast(min)
    }
}
