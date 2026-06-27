package com.qinhuai.ruins.affix

import com.qinhuai.corelib.api.item.ItemManagerAPI
import com.qinhuai.corelib.item.ItemMetadataManager
import com.qinhuai.corelib.util.ItemUtils
import com.qinhuai.corelib.util.TextUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object Keystone {

    private const val KEY_TIER = "keystone_tier"
    private const val KEY_AFFIXES = "keystone_affixes"
    private const val AFFIX_MARKER = "— Affixes —"
    private val meta = ItemMetadataManager.get("qinhruins")

    private var material = "ENDER_EYE"
    private var name = "&dRealm Keystone &7T{tier}"
    private var lore = listOf("&7Right-click a ruin core to activate a &dT{tier} &7realm")
    private var modelData: Int? = null

    private val buildTier = ThreadLocal<Int?>()

    fun currentBuildTier(): Int? = buildTier.get()

    fun load(section: ConfigurationSection?) {
        if (section == null) return
        material = section.getString("material") ?: material
        section.getString("ref")?.takeIf { it.isNotBlank() }?.let { material = it }
        name = section.getString("name") ?: name
        lore = if (section.isList("lore")) section.getStringList("lore") else lore
        modelData = if (section.contains("model-data")) section.getInt("model-data") else null
    }

    fun build(tier: Int, player: Player?): ItemStack {
        buildTier.set(tier)
        val resolved = try {
            ItemManagerAPI.getHookItem(material, player)
        } finally {
            buildTier.remove()
        }
        val displayName = name.replace("{tier}", tier.toString())
        val displayLore = lore.map { it.replace("{tier}", tier.toString()) }
        val base = if (resolved != null) {
            if (isVanillaRef(material)) {
                resolved.itemMeta?.let { im ->
                    TextUtil.applyItemDisplay(im, displayName.takeIf { name.isNotBlank() }, displayLore)
                    resolved.itemMeta = im
                }
            }
            resolved
        } else {
            ItemUtils.createItem(materialOf(material), 1, displayName, displayLore, modelData)
        }
        meta.setInt(base, KEY_TIER, tier)
        return base
    }

    private fun isVanillaRef(ref: String): Boolean {
        val main = ref.trim().split("::", limit = 2)[0].trim()
        if (main.isEmpty()) return true
        val alias = when {
            main.contains(':') -> main.substringBefore(':').lowercase()
            main.indexOf('-') > 0 -> main.substringBefore('-').lowercase()
            else -> "vanilla"
        }
        return alias == "vanilla" || alias == "minecraft"
    }

    fun tierOf(item: ItemStack?): Int? {
        if (item == null) return null
        return meta.getInt(item, KEY_TIER)
    }

    fun affixIdsOf(item: ItemStack?): List<String> {
        if (item == null) return emptyList()
        val raw = meta.getString(item, KEY_AFFIXES) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun hasRolledAffixes(item: ItemStack?): Boolean = affixIdsOf(item).isNotEmpty()

    fun applyAffixes(item: ItemStack, affixes: List<AffixDefinition>) {
        meta.setString(item, KEY_AFFIXES, affixes.joinToString(",") { it.id })
        val itemMeta = item.itemMeta ?: return
        val plain = PlainTextComponentSerializer.plainText()
        val existing: List<Component> = itemMeta.lore() ?: emptyList()
        val lines = ArrayList(existing.takeWhile { !plain.serialize(it).contains(AFFIX_MARKER) })
        if (affixes.isNotEmpty()) {
            lines.add(TextUtil.toComponent("&8$AFFIX_MARKER"))
            affixes.forEach { lines.add(TextUtil.toComponent("&7• ${it.name}")) }
        }
        itemMeta.lore(lines)
        item.itemMeta = itemMeta
    }

    private fun materialOf(name: String): Material = Material.matchMaterial(name) ?: Material.ENDER_EYE
}
