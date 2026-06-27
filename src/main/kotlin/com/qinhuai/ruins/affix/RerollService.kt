package com.qinhuai.ruins.affix

import com.qinhuai.corelib.economy.EconomyBridge
import com.qinhuai.corelib.expression.ExpressionEngine
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.lang.Lang
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

object RerollService {

    private var enabled = true
    private var costExpr = "tier * 50"
    private var provider: String? = null
    private var currencyId: String? = null

    fun load(section: ConfigurationSection?) {
        if (section == null) return
        enabled = section.getBoolean("enabled", true)
        costExpr = section.getString("cost-base") ?: costExpr
        provider = section.getString("currency-provider")?.takeIf { it.isNotBlank() }
        currencyId = section.getString("currency-id")?.takeIf { it.isNotBlank() }
    }

    fun reroll(player: Player) {
        if (!enabled) {
            TextUtil.sendColored(player, Lang.get("realm.reroll-disabled"))
            return
        }
        val slot = findKeystoneSlot(player)
        if (slot == null) {
            TextUtil.sendColored(player, Lang.get("realm.need-keystone"))
            return
        }
        val (item, hand) = slot
        val tier = Keystone.tierOf(item) ?: return
        val cost = runCatching {
            ExpressionEngine.evaluate(costExpr, mapOf("tier" to tier.toDouble()))
        }.getOrDefault(0.0)

        if (cost > 0.0) {
            if (!EconomyBridge.isAvailable()) {
                TextUtil.sendColored(player, Lang.get("realm.reroll-no-economy"))
                return
            }
            if (!EconomyBridge.has(player, cost, provider, currencyId)) {
                val label = currencyId ?: provider ?: Lang.get("realm.default-currency")
                val balance = EconomyBridge.getBalance(player, provider, currencyId).toLong()
                TextUtil.sendColored(player, Lang.get("realm.reroll-insufficient", "cost" to cost.toLong(), "label" to label, "balance" to balance))
                TextUtil.sendColored(player, Lang.get("realm.reroll-currency-hint"))
                return
            }
            val paid = EconomyBridge.withdraw(player, cost, provider, currencyId)
            if (!paid.success) {
                TextUtil.sendColored(player, Lang.get("realm.reroll-pay-failed", "error" to (paid.message ?: Lang.get("realm.unknown-error"))))
                return
            }
        }

        val affixes = AffixRoller.roll(tier)
        Keystone.applyAffixes(item, affixes)
        setHand(player, hand, item)

        TextUtil.sendColored(player, Lang.get("realm.reroll-done", "tier" to tier))
        if (affixes.isEmpty()) {
            TextUtil.sendColored(player, Lang.get("realm.reroll-no-affixes"))
        } else {
            affixes.forEach { TextUtil.sendColored(player, "  §8» ${it.name}") }
        }
    }

    private fun findKeystoneSlot(player: Player): Pair<ItemStack, EquipmentSlot>? {
        val main = player.inventory.itemInMainHand
        if (Keystone.tierOf(main) != null) return main to EquipmentSlot.HAND
        val off = player.inventory.itemInOffHand
        if (Keystone.tierOf(off) != null) return off to EquipmentSlot.OFF_HAND
        return null
    }

    private fun setHand(player: Player, hand: EquipmentSlot, item: ItemStack) {
        if (hand == EquipmentSlot.HAND) player.inventory.setItemInMainHand(item)
        else player.inventory.setItemInOffHand(item)
    }
}
