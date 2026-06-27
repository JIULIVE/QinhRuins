package com.qinhuai.ruins.guide

import com.qinhuai.corelib.util.ItemUtils
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.core.GenerationRule
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.data.DiscoveryStore
import com.qinhuai.ruins.loot.LootTableRegistry
import com.qinhuai.ruins.template.BlueprintRegistry
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class CodexHolder : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}

object CodexGui {

    fun open(player: Player) {
        val holder = CodexHolder()
        val inv = Bukkit.createInventory(holder, 54, TextUtil.toComponent(Lang.get("gui.codex-title")))
        holder.inv = inv
        fill(inv, player)
        player.openInventory(inv)
    }

    private fun fill(inv: Inventory, player: Player) {
        val templates = TemplateRegistry.all().toList()
        templates.take(45).forEachIndexed { i, tpl ->
            val found = DiscoveryStore.hasTemplate(player.uniqueId, tpl.id)
            inv.setItem(i, if (found) discovered(tpl) else mystery())
        }
        val bar = ItemUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, 1, " ", emptyList())
        for (i in 45 until 54) inv.setItem(i, bar)
        val total = templates.size
        val got = templates.count { DiscoveryStore.hasTemplate(player.uniqueId, it.id) }
        inv.setItem(49, ItemUtils.createItem(Material.KNOWLEDGE_BOOK, 1, Lang.get("gui.codex-book-name"), listOf(
            Lang.get("gui.codex-progress", "got" to got, "total" to total),
            if (total > 45) Lang.get("gui.codex-overflow") else Lang.get("gui.codex-hint"),
        )))
    }

    private fun discovered(tpl: RuinTemplate): ItemStack {
        val mat = Material.matchMaterial(tpl.icon) ?: Material.FILLED_MAP
        val lore = ArrayList<String>()
        lore.add(Lang.get("gui.card-discovered"))
        lore.add("§8§m                    ")
        lore.add(Lang.get("gui.card-location", "dimension" to dimensionName(tpl.generation), "layer" to layerName(tpl.generation.yMode)))
        val blueprint = BlueprintRegistry.get(tpl.id)
        val total = blueprint?.spawnPoints?.sumOf { it.count } ?: 0
        if (total > 0) {
            lore.add(Lang.get("gui.card-spawn", "count" to total, "cooldown" to cooldownText(tpl.respawnSeconds)))
            lore.add("§r")
            lore.add(Lang.get("gui.card-mobs-header"))
            lore.addAll(mobLines(tpl.id))
        } else {
            lore.add(Lang.get("gui.card-no-spawn"))
        }
        val container = lootSummary(tpl.containerTable)
        val clear = lootSummary(tpl.clearRewardTable)
        val chestTables = (blueprint?.lootChests ?: emptyList()).map { it.lootTable }.distinct()
        if (container != null || clear != null || chestTables.isNotEmpty()) {
            lore.add("§r")
            lore.add(Lang.get("gui.card-loot-header"))
            container?.let { lore.add(Lang.get("gui.card-loot-container", "loot" to it)) }
            chestTables.forEach { t -> lootSummary(t)?.let { lore.add(Lang.get("gui.card-loot-chest", "loot" to it)) } }
            clear?.let { lore.add(Lang.get("gui.card-loot-clear", "loot" to it)) }
        }
        return ItemUtils.createItem(mat, 1, tpl.display, lore)
    }

    private fun lootSummary(tableName: String?): String? {
        val table = tableName?.let { LootTableRegistry.get(it) } ?: return null
        val entries = table.entries + table.groups.flatMap { it.entries }
        if (entries.isEmpty()) return null
        val preview = entries.take(3).joinToString("、") { cleanItem(it.item) }
        return if (entries.size > 3) Lang.get("gui.loot-summary-more", "preview" to preview, "count" to entries.size) else preview
    }

    private fun cleanItem(item: String): String =
        item.substringAfterLast(':').removePrefix("qi-").removePrefix("mi-").removePrefix("ni-").removePrefix("mm-")

    private fun dimensionName(gen: GenerationRule): String {
        if (gen.environments.isNotEmpty()) {
            return gen.environments.joinToString("/") {
                when (it.trim().uppercase()) {
                    "NORMAL", "OVERWORLD" -> Lang.get("gui.dim-overworld")
                    "NETHER", "THE_NETHER" -> Lang.get("gui.dim-nether")
                    "THE_END", "END" -> Lang.get("gui.dim-end")
                    else -> it
                }
            }
        }
        if (gen.worlds.isNotEmpty()) return gen.worlds.joinToString("/")
        return Lang.get("gui.dim-all")
    }

    private fun cooldownText(seconds: Long): String = when {
        seconds <= 0 -> Lang.get("gui.cd-never")
        seconds >= 3600 && seconds % 3600 == 0L -> Lang.get("gui.cd-hours", "h" to seconds / 3600)
        seconds >= 3600 -> Lang.get("gui.cd-hours-min", "h" to seconds / 3600, "m" to (seconds % 3600) / 60)
        seconds >= 60 -> Lang.get("gui.cd-minutes", "m" to seconds / 60)
        else -> Lang.get("gui.cd-seconds", "s" to seconds)
    }

    private fun mobLines(templateId: String): List<String> {
        val blueprint = BlueprintRegistry.get(templateId) ?: return emptyList()
        val byMob = LinkedHashMap<String, Int>()
        for (sp in blueprint.spawnPoints) byMob.merge(sp.mob, sp.count) { a, b -> a + b }
        val total = byMob.values.sum()
        if (total <= 0) return emptyList()
        return byMob.entries.sortedByDescending { it.value }.map { (mob, count) ->
            val share = count * 100.0 / total
            val (tag, color) = rarityOf(share)
            Lang.get("gui.mob-line",
                "color" to color, "tag" to tag, "mob" to cleanMob(mob), "chance" to String.format("%.1f", share))
        }
    }

    private fun rarityOf(share: Double): Pair<String, String> = when {
        share >= 35.0 -> Lang.get("gui.rarity-common") to "§a"
        share >= 20.0 -> Lang.get("gui.rarity-rare") to "§b"
        else -> Lang.get("gui.rarity-elite") to "§6"
    }

    private fun cleanMob(mob: String): String =
        mob.removePrefix("mm-").removePrefix("mythicmobs:").removePrefix("minecraft:")

    private fun mystery(): ItemStack =
        ItemUtils.createItem(Material.BLACK_STAINED_GLASS_PANE, 1, Lang.get("gui.mystery-name"), listOf(
            Lang.get("gui.mystery-line1"),
            Lang.get("gui.mystery-line2"),
        ))

    private fun layerName(yMode: String): String = when (yMode.trim().lowercase()) {
        "surface", "ground" -> Lang.get("gui.layer-surface")
        "underground", "cave", "buried" -> Lang.get("gui.layer-underground")
        "sky", "floating", "air" -> Lang.get("gui.layer-sky")
        "ocean-surface", "sea-surface", "water-surface" -> Lang.get("gui.layer-sea-surface")
        "seabed", "ocean-floor", "sea-floor", "underwater" -> Lang.get("gui.layer-seabed")
        else -> yMode.toIntOrNull()?.let { Lang.get("gui.layer-fixed-y", "y" to it) } ?: Lang.get("gui.layer-surface")
    }
}
