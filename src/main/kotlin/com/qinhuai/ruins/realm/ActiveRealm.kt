package com.qinhuai.ruins.realm

import com.qinhuai.ruins.affix.AffixDefinition
import org.bukkit.Location
import org.bukkit.boss.BossBar
import java.util.UUID

data class ActiveRealm(
    val anchorId: String,
    val templateId: String,
    val tier: Int,
    val affixes: List<AffixDefinition>,
    val startedAtMillis: Long,
    val mobs: MutableSet<UUID>,
    val totalMobs: Int,
    val bossBar: BossBar?,
    val timeLimitSeconds: Long,
    var lastSeenPlayersAtMillis: Long,
    val zoneCenter: Location,
    val zoneRadius: Double,
    val participants: MutableSet<UUID> = HashSet(),
)
