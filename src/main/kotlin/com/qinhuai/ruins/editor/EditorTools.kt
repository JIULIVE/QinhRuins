package com.qinhuai.ruins.editor

import com.qinhuai.corelib.item.ItemMetadataManager
import com.qinhuai.corelib.util.ItemUtils
import com.qinhuai.ruins.lang.Lang
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

enum class EditorAction {
    GOTO_SPAWN, GOTO_CHEST, GOTO_CORE, GOTO_MECH, OVERVIEW, BACK,
    MARKER, MOB, COUNT, STAGE, LOOT, UNLOCK, MECH_PANEL,
    SAVE, CANCEL,
}

object EditorTools {

    const val TOOL_KEY = "editor_tool"
    const val ACTION_KEY = "editor_action"
    private val meta = ItemMetadataManager.get("qinhruins")

    fun equip(player: Player, session: EditorSession) {
        render(player, session)
        player.inventory.heldItemSlot = 0
        player.updateInventory()
    }

    fun render(player: Player, session: EditorSession) {
        player.inventory.clear()
        for ((slot, action) in layoutFor(session)) {
            player.inventory.setItem(slot, build(action, session))
        }
        player.updateInventory()
    }

    fun actionOf(item: ItemStack?): EditorAction? {
        if (item == null) return null
        val raw = meta.getString(item, ACTION_KEY) ?: return null
        return runCatching { EditorAction.valueOf(raw) }.getOrNull()
    }

    fun isEditorItem(item: ItemStack?): Boolean = item != null && meta.has(item, TOOL_KEY)

    fun screenMode(screen: EditorScreen): EditorMode = when (screen) {
        EditorScreen.CHEST -> EditorMode.CHEST
        EditorScreen.CORE -> EditorMode.CORE
        EditorScreen.MECH -> EditorMode.MECH
        else -> EditorMode.SPAWN
    }

    private fun layoutFor(session: EditorSession): Map<Int, EditorAction> = when (session.screen) {
        EditorScreen.HOME -> linkedMapOf(
            0 to EditorAction.GOTO_SPAWN,
            1 to EditorAction.GOTO_CHEST,
            2 to EditorAction.GOTO_CORE,
            3 to EditorAction.GOTO_MECH,
            4 to EditorAction.OVERVIEW,
            7 to EditorAction.SAVE,
            8 to EditorAction.CANCEL,
        )
        EditorScreen.SPAWN -> linkedMapOf(
            0 to EditorAction.MARKER,
            1 to EditorAction.MOB,
            2 to EditorAction.COUNT,
            3 to EditorAction.STAGE,
            8 to EditorAction.BACK,
        )
        EditorScreen.CHEST -> linkedMapOf(
            0 to EditorAction.MARKER,
            1 to EditorAction.LOOT,
            2 to EditorAction.UNLOCK,
            8 to EditorAction.BACK,
        )
        EditorScreen.CORE -> linkedMapOf(
            0 to EditorAction.MARKER,
            8 to EditorAction.BACK,
        )
        EditorScreen.MECH -> linkedMapOf(
            0 to EditorAction.MARKER,
            1 to EditorAction.MECH_PANEL,
            8 to EditorAction.BACK,
        )
    }

    private fun markerLabel(screen: EditorScreen): String = when (screen) {
        EditorScreen.CHEST -> Lang.get("editor.label-chest")
        EditorScreen.CORE -> Lang.get("editor.label-core")
        EditorScreen.MECH -> Lang.get("editor.label-mech-point")
        else -> Lang.get("editor.label-spawn")
    }

    private fun build(action: EditorAction, session: EditorSession): ItemStack {
        val item = when (action) {
            EditorAction.GOTO_SPAWN -> ItemUtils.createItem(Material.ZOMBIE_HEAD, 1, Lang.get("editor.tool-spawn-name"), listOf(Lang.get("editor.tool-spawn-lore1"), Lang.get("editor.tool-enter")))
            EditorAction.GOTO_CHEST -> ItemUtils.createItem(Material.CHEST, 1, Lang.get("editor.tool-chest-name"), listOf(Lang.get("editor.tool-chest-lore1"), Lang.get("editor.tool-enter")))
            EditorAction.GOTO_CORE -> ItemUtils.createItem(Material.LODESTONE, 1, Lang.get("editor.tool-core-name"), listOf(Lang.get("editor.tool-core-lore1"), Lang.get("editor.tool-enter")))
            EditorAction.GOTO_MECH -> ItemUtils.createItem(Material.REDSTONE, 1, Lang.get("editor.tool-mech-name"), listOf(Lang.get("editor.tool-mech-lore1"), Lang.get("editor.tool-enter")))
            EditorAction.OVERVIEW -> ItemUtils.createItem(Material.BOOK, 1, Lang.get("editor.tool-overview-name"), listOf(
                Lang.get("editor.tool-overview-lore1",
                    "spawns" to session.spawnPoints.size, "chests" to session.lootChests.size, "cores" to session.cores.size, "mechs" to session.mechanisms.size),
                Lang.get("editor.tool-overview-lore2"),
            ))
            EditorAction.BACK -> ItemUtils.createItem(Material.ARROW, 1, Lang.get("editor.tool-back-name"), listOf(Lang.get("editor.tool-back-lore1")))
            EditorAction.MARKER -> ItemUtils.createItem(Material.BLAZE_ROD, 1, Lang.get("editor.tool-marker-name", "label" to markerLabel(session.screen)), listOf(
                Lang.get("editor.tool-marker-lore1", "label" to markerLabel(session.screen)),
            ))
            EditorAction.MOB -> ItemUtils.createItem(Material.SPAWNER, 1, Lang.get("editor.tool-mob-name", "mob" to session.defaultMob), listOf(
                Lang.get("editor.tool-mob-lore1"),
            ))
            EditorAction.COUNT -> ItemUtils.createItem(Material.ROTTEN_FLESH, session.defaultCount.coerceIn(1, 64), Lang.get("editor.tool-count-name", "count" to session.defaultCount), listOf(
                Lang.get("editor.tool-count-lore1"),
            ))
            EditorAction.STAGE -> ItemUtils.createItem(Material.GLOWSTONE_DUST, session.defaultStage.coerceIn(1, 64), Lang.get("editor.tool-stage-name", "stage" to session.defaultStage), listOf(
                Lang.get("editor.tool-stage-lore1"),
                Lang.get("editor.tool-stage-lore2"),
            ))
            EditorAction.LOOT -> ItemUtils.createItem(Material.CHEST, 1, Lang.get("editor.tool-loot-name", "table" to session.defaultLootTable), listOf(
                Lang.get("editor.tool-loot-lore1"),
                Lang.get("editor.tool-loot-lore2"),
            ))
            EditorAction.UNLOCK -> ItemUtils.createItem(Material.IRON_BARS, 1, Lang.get("editor.tool-unlock-name", "stage" to session.defaultUnlockStage), listOf(
                Lang.get("editor.tool-unlock-lore1"),
                Lang.get("editor.tool-unlock-lore2"),
            ))
            EditorAction.MECH_PANEL -> ItemUtils.createItem(Material.COMPARATOR, 1, Lang.get("editor.tool-mechpanel-name"), listOf(
                Lang.get("editor.tool-mechpanel-lore1"),
                Lang.get("editor.tool-mechpanel-lore2"),
            ))
            EditorAction.SAVE -> ItemUtils.createItem(Material.NETHER_STAR, 1, Lang.get("editor.tool-save-name"), listOf(Lang.get("editor.tool-save-lore1")))
            EditorAction.CANCEL -> ItemUtils.createItem(Material.BARRIER, 1, Lang.get("editor.tool-cancel-name"), listOf(Lang.get("editor.tool-cancel-lore1")))
        }
        meta.setString(item, TOOL_KEY, "1")
        meta.setString(item, ACTION_KEY, action.name)
        return item
    }
}
