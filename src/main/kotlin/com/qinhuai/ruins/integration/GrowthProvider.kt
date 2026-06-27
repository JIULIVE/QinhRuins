package com.qinhuai.ruins.integration

import org.bukkit.entity.Player

interface GrowthProvider {
    val id: String
    fun isAvailable(): Boolean
    fun getGrowth(player: Player): Double
    fun hasClass(player: Player, classId: String): Boolean
}

object VanillaGrowthProvider : GrowthProvider {
    override val id = "vanilla"
    override fun isAvailable(): Boolean = true
    override fun getGrowth(player: Player): Double = player.level.toDouble()
    override fun hasClass(player: Player, classId: String): Boolean = false
}

object GrowthProviders {
    private var active: GrowthProvider = VanillaGrowthProvider

    fun active(): GrowthProvider = active

    fun set(provider: GrowthProvider) {
        if (provider.isAvailable()) active = provider
    }
}
