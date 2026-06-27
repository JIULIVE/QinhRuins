package com.qinhuai.ruins.editor

import com.qinhuai.corelib.item.ItemMetadataManager
import com.qinhuai.corelib.util.ItemUtils
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.lang.Lang
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

enum class EditorGuiKind { LIST, MECH }

class EditorHolder(val kind: EditorGuiKind) : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}

object EditorGui {

    const val ENTRY_KEY = "editor_entry"
    const val BTN_KEY = "editor_btn"
    private val meta = ItemMetadataManager.get("qinhruins")

    fun openList(player: Player, session: EditorSession) {
        val holder = EditorHolder(EditorGuiKind.LIST)
        val inv = Bukkit.createInventory(holder, 54, TextUtil.toComponent(Lang.get("editor.gui-list-title")))
        holder.inv = inv
        fillList(inv, session)
        player.openInventory(inv)
    }

    fun openMech(player: Player, session: EditorSession) {
        val holder = EditorHolder(EditorGuiKind.MECH)
        val inv = Bukkit.createInventory(holder, 27, TextUtil.toComponent(Lang.get("editor.gui-mech-title")))
        holder.inv = inv
        fillMech(inv, session)
        player.openInventory(inv)
    }

    fun fillList(inv: Inventory, session: EditorSession) {
        inv.clear()
        var slot = 0
        for (sp in session.spawnPoints) {
            if (slot >= 45) break
            inv.setItem(slot++, entry(sp.id, Material.ZOMBIE_HEAD, Lang.get("editor.entry-spawn-name", "id" to sp.id), listOf(
                Lang.get("editor.entry-spawn-lore1", "mob" to sp.mob, "count" to sp.count, "stage" to sp.stage),
                Lang.get("editor.entry-pos", "x" to sp.pos.x, "y" to sp.pos.y, "z" to sp.pos.z),
                Lang.get("editor.entry-delete"),
            )))
        }
        for (c in session.lootChests) {
            if (slot >= 45) break
            inv.setItem(slot++, entry(c.id, Material.CHEST, Lang.get("editor.entry-chest-name", "id" to c.id), listOf(
                Lang.get("editor.entry-chest-lore1", "table" to c.lootTable, "stage" to c.unlockStage),
                Lang.get("editor.entry-pos", "x" to c.pos.x, "y" to c.pos.y, "z" to c.pos.z),
                Lang.get("editor.entry-delete"),
            )))
        }
        session.cores.forEachIndexed { i, p ->
            if (slot >= 45) return@forEachIndexed
            inv.setItem(slot++, entry("core${i + 1}", Material.LODESTONE, Lang.get("editor.entry-core-name", "n" to (i + 1)), listOf(
                Lang.get("editor.entry-pos", "x" to p.x, "y" to p.y, "z" to p.z),
                Lang.get("editor.entry-delete"),
            )))
        }
        for (m in session.mechanisms) {
            if (slot >= 45) break
            inv.setItem(slot++, entry(m.id, Material.REDSTONE, Lang.get("editor.entry-mech-name", "id" to m.id), listOf(
                Lang.get("editor.entry-mech-lore1", "trigger" to m.trigger.type.name.lowercase(), "actions" to m.actions.joinToString(",") { it.type.name.lowercase() }),
                Lang.get("editor.entry-delete"),
            )))
        }
        inv.setItem(53, button("close", Material.BARRIER, Lang.get("editor.btn-back"), emptyList()))
    }

    fun fillMech(inv: Inventory, session: EditorSession) {
        inv.clear()
        val builder = session.mechBuilder
        val info = if (builder == null) Lang.get("editor.mech-not-started")
            else Lang.get("editor.mech-info", "id" to builder.id, "points" to builder.points.size, "actions" to builder.actions.size)
        inv.setItem(4, button("mechinfo", Material.PAPER, Lang.get("editor.mech-current", "info" to info), listOf(
            Lang.get("editor.mech-current-lore1"),
            Lang.get("editor.mech-current-lore2"),
        )))
        inv.setItem(10, button("t_interact", Material.LEVER, Lang.get("editor.mech-t-interact"), listOf(Lang.get("editor.mech-use-last-point"))))
        inv.setItem(11, button("t_break", Material.IRON_PICKAXE, Lang.get("editor.mech-t-break"), listOf(Lang.get("editor.mech-use-last-point"))))
        inv.setItem(12, button("t_redstone", Material.REDSTONE_TORCH, Lang.get("editor.mech-t-redstone"), listOf(Lang.get("editor.mech-use-last-point"))))
        inv.setItem(13, button("t_region", Material.STRUCTURE_VOID, Lang.get("editor.mech-t-region"), listOf(Lang.get("editor.mech-use-two-points"))))
        val stageShown = (builder?.stage ?: 0).coerceAtLeast(1)
        val timerShown = (builder?.interval ?: 0L).coerceAtLeast(1L)
        inv.setItem(14, button("t_stage", Material.EXPERIENCE_BOTTLE, Lang.get("editor.mech-t-stage", "stage" to stageShown), listOf(Lang.get("editor.mech-plus-minus"))))
        inv.setItem(15, button("t_timer", Material.CLOCK, Lang.get("editor.mech-t-timer", "sec" to timerShown), listOf(Lang.get("editor.mech-plus-minus"))))
        inv.setItem(18, button("a_spawn", Material.ZOMBIE_HEAD, Lang.get("editor.mech-a-spawn"), listOf(Lang.get("editor.mech-a-spawn-lore1", "mob" to session.defaultMob, "count" to session.defaultCount), Lang.get("editor.mech-a-spawn-lore2"))))
        inv.setItem(19, button("a_fill", Material.GRASS_BLOCK, Lang.get("editor.mech-a-fill"), listOf(Lang.get("editor.mech-a-fill-lore1"), Lang.get("editor.mech-a-fill-lore2"))))
        inv.setItem(20, button("a_message", Material.OAK_SIGN, Lang.get("editor.mech-a-message"), listOf(Lang.get("editor.mech-a-message-lore1"))))
        inv.setItem(21, button("a_command", Material.COMMAND_BLOCK, Lang.get("editor.mech-a-command"), listOf(Lang.get("editor.mech-a-command-lore1"))))
        inv.setItem(22, button("clearpoints", Material.WATER_BUCKET, Lang.get("editor.mech-clearpoints"), emptyList()))
        inv.setItem(24, button("done", Material.LIME_DYE, Lang.get("editor.mech-done"), emptyList()))
        inv.setItem(25, button("discard", Material.RED_DYE, Lang.get("editor.mech-discard"), emptyList()))
        fillBackground(inv)
    }

    fun buttonId(item: ItemStack?): String? = item?.let { meta.getString(it, BTN_KEY) }

    fun entryId(item: ItemStack?): String? = item?.let { meta.getString(it, ENTRY_KEY) }

    private fun button(id: String, material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemUtils.createItem(material, 1, name, lore)
        meta.setString(item, BTN_KEY, id)
        return item
    }

    private fun entry(id: String, material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemUtils.createItem(material, 1, name, lore)
        meta.setString(item, ENTRY_KEY, id)
        return item
    }

    private fun fillBackground(inv: Inventory) {
        val filler = ItemUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, 1, " ", emptyList())
        for (i in 0 until inv.size) {
            if (inv.getItem(i) == null) inv.setItem(i, filler)
        }
    }
}
