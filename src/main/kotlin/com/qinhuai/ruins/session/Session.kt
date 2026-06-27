package com.qinhuai.ruins.session

import org.bukkit.boss.BossBar
import java.util.UUID

data class Session(
    val id: String,
    val anchorId: String,
    val templateId: String,
    val worldName: String,
    val members: MutableSet<UUID>,
    var leader: UUID,
    val startedAtMillis: Long,
    val timeLimitSeconds: Long,
    val cleanupOnEmptySeconds: Long,
    val bossBar: BossBar?,
    var emptySinceMillis: Long? = null,
)
