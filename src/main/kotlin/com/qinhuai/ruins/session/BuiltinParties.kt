package com.qinhuai.ruins.session

import com.qinhuai.ruins.lang.Lang
import java.util.UUID

object BuiltinParties {

    private val leaderOf = HashMap<UUID, UUID>()
    private val membersOf = HashMap<UUID, LinkedHashSet<UUID>>()
    private val invites = HashMap<UUID, UUID>()

    fun partyMembers(player: UUID): Set<UUID> {
        val leader = leaderOf[player] ?: return setOf(player)
        return membersOf[leader] ?: setOf(player)
    }

    fun leaderOf(player: UUID): UUID? = leaderOf[player]

    fun create(player: UUID): String {
        if (leaderOf.containsKey(player)) return Lang.get("core.party-already-in")
        leaderOf[player] = player
        membersOf[player] = linkedSetOf(player)
        return Lang.get("core.party-created")
    }

    fun invite(inviter: UUID, target: UUID): String {
        val leader = leaderOf[inviter]
        if (leader != inviter) return Lang.get("core.party-only-leader-invite")
        if (leaderOf.containsKey(target)) return Lang.get("core.party-target-in-other")
        invites[target] = inviter
        return Lang.get("core.party-invite-sent")
    }

    fun accept(player: UUID): String {
        val leader = invites.remove(player) ?: return Lang.get("core.party-no-invite")
        if (!membersOf.containsKey(leader)) return Lang.get("core.party-disbanded")
        if (leaderOf.containsKey(player)) return Lang.get("core.party-already-in-other")
        membersOf[leader]?.add(player)
        leaderOf[player] = leader
        return Lang.get("core.party-joined")
    }

    fun leave(player: UUID): String {
        val leader = leaderOf[player] ?: return Lang.get("core.party-not-in")
        if (player == leader) {
            membersOf.remove(leader)?.forEach { leaderOf.remove(it) }
            return Lang.get("core.party-you-disbanded")
        }
        leaderOf.remove(player)
        membersOf[leader]?.remove(player)
        return Lang.get("core.party-you-left")
    }

    fun clear() {
        leaderOf.clear()
        membersOf.clear()
        invites.clear()
    }
}
