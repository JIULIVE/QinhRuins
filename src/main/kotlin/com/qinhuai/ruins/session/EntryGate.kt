package com.qinhuai.ruins.session

import com.qinhuai.corelib.economy.EconomyBridge
import com.qinhuai.corelib.scheduler.CooldownManager
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.RuinTemplate
import com.qinhuai.ruins.integration.GrowthProviders
import com.qinhuai.ruins.lang.Lang
import org.bukkit.entity.Player
import java.util.concurrent.TimeUnit

sealed interface GateResult {
    data object Ok : GateResult
    data class Deny(val message: String) : GateResult
}

object EntryGate {

    private val cooldowns = CooldownManager()

    fun cooldownKey(templateId: String, leader: Player): String = "qr_enter:$templateId:${leader.uniqueId}"

    fun applyCooldown(templateId: String, leader: Player, seconds: Long) {
        cooldowns.setCooldown(cooldownKey(templateId, leader), seconds, TimeUnit.SECONDS)
    }

    fun check(template: RuinTemplate, anchor: Anchor, gathered: List<Player>, leader: Player): GateResult {
        if (SessionManager.isAnchorBusy(anchor.id)) {
            return GateResult.Deny(Lang.get("core.anchor-busy"))
        }
        val rule = template.entry

        if (gathered.size < rule.minPlayers) {
            val need = (rule.minPlayers - gathered.size).toString()
            return GateResult.Deny(Lang.get("messages.party-not-enough", "n" to need))
        }
        if (rule.maxPlayers in 1 until gathered.size) {
            return GateResult.Deny(Lang.get("core.party-too-many", "max" to rule.maxPlayers))
        }

        if (rule.minGrowth > 0) {
            val low = gathered.firstOrNull { GrowthProviders.active().getGrowth(it) < rule.minGrowth }
            if (low != null) {
                return GateResult.Deny(Lang.get("messages.growth-too-low") + " §7(${low.name})")
            }
        }

        for (cls in rule.requiredClasses) {
            val present = gathered.any { GrowthProviders.active().hasClass(it, cls) }
            if (!present) {
                return GateResult.Deny(Lang.get("messages.class-required", "class" to cls))
            }
        }

        if (rule.cooldownSeconds > 0) {
            val key = cooldownKey(template.id, leader)
            if (cooldowns.hasCooldown(key)) {
                val remain = cooldowns.getRemaining(key) / 1000
                return GateResult.Deny(Lang.get("core.anchor-cooldown", "remain" to remain))
            }
        }

        if (rule.cost.isNotBlank()) {
            val req = EconomyBridge.parseMoneyRequirement(rule.cost)
            if (req != null && !EconomyBridge.has(leader, req.amount, req.providerId, req.currencyId)) {
                return GateResult.Deny(Lang.get("core.cost-not-enough", "amount" to req.amount.toInt()))
            }
        }

        return GateResult.Ok
    }
}
