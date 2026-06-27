package com.qinhuai.ruins.integration

import com.qinhuai.ruins.session.BuiltinParties
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

data class RuinParty(val leader: UUID, val members: List<Player>)

interface PartyProvider {
    val id: String
    fun isAvailable(): Boolean
    fun getParty(player: Player): RuinParty
}

object BuiltinPartyProvider : PartyProvider {
    override val id = "builtin"
    override fun isAvailable(): Boolean = true

    override fun getParty(player: Player): RuinParty {
        val memberIds = BuiltinParties.partyMembers(player.uniqueId)
        val leader = BuiltinParties.leaderOf(player.uniqueId) ?: player.uniqueId
        val online = memberIds.mapNotNull { Bukkit.getPlayer(it) }
        return RuinParty(leader, online.ifEmpty { listOf(player) })
    }
}

object PartyProviders {
    private var active: PartyProvider = BuiltinPartyProvider

    fun active(): PartyProvider = active

    fun set(provider: PartyProvider) {
        if (provider.isAvailable()) active = provider
    }
}
