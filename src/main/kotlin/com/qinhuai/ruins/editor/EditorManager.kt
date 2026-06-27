package com.qinhuai.ruins.editor

import com.qinhuai.ruins.core.Blueprint
import com.qinhuai.ruins.core.Objectives
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

object EditorManager {

    private val sessions = HashMap<UUID, EditorSession>()
    private val savedInventories = HashMap<UUID, Array<ItemStack?>>()

    fun begin(player: Player, templateId: String, anchorId: String, blueprint: Blueprint?): EditorSession {
        val session = EditorSession(
            templateId = templateId,
            anchorId = anchorId,
            spawnPoints = blueprint?.spawnPoints?.toMutableList() ?: mutableListOf(),
            lootChests = blueprint?.lootChests?.toMutableList() ?: mutableListOf(),
            objectives = blueprint?.objectives ?: Objectives(emptyList()),
            mechanisms = blueprint?.mechanisms?.toMutableList() ?: mutableListOf(),
            cores = blueprint?.cores?.toMutableList() ?: mutableListOf(),
            variants = blueprint?.variants ?: emptyList(),
            spawnCommands = blueprint?.spawnCommands ?: emptyList(),
        )
        sessions[player.uniqueId] = session
        savedInventories[player.uniqueId] = player.inventory.contents.clone()
        EditorTools.equip(player, session)
        return session
    }

    fun get(player: Player): EditorSession? = sessions[player.uniqueId]

    fun end(player: Player): EditorSession? {
        ChatInputService.cancel(player)
        restoreInventory(player)
        return sessions.remove(player.uniqueId)
    }

    fun isTool(item: ItemStack?): Boolean = EditorTools.isEditorItem(item)

    fun isEditing(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    fun refreshTools(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        EditorTools.render(player, session)
    }

    private fun restoreInventory(player: Player) {
        val saved = savedInventories.remove(player.uniqueId)
        if (saved == null) {
            val contents = player.inventory.contents
            for (i in contents.indices) if (EditorTools.isEditorItem(contents[i])) player.inventory.setItem(i, null)
            return
        }
        player.inventory.contents = saved
        player.updateInventory()
    }
}
