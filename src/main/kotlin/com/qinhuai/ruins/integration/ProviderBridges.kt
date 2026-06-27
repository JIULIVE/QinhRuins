package com.qinhuai.ruins.integration

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object ProviderBridges {

    fun register(): String? {
        var growth: String? = null
        when {
            pluginEnabled("QinhClass") && QinhClassGrowthProvider.isAvailable() -> {
                GrowthProviders.set(QinhClassGrowthProvider); growth = "QinhClass"
            }
            pluginEnabled("MMOCore") && MMOCoreGrowthProvider.isAvailable() -> {
                GrowthProviders.set(MMOCoreGrowthProvider); growth = "MMOCore"
            }
        }
        if (pluginEnabled("MMOCore") && MMOCorePartyProvider.isAvailable()) {
            PartyProviders.set(MMOCorePartyProvider)
        }
        return growth
    }

    private fun pluginEnabled(name: String): Boolean =
        Bukkit.getPluginManager().getPlugin(name)?.isEnabled == true
}

object QinhClassGrowthProvider : GrowthProvider {
    override val id = "qinhclass"

    private val api: Any? by lazy {
        runCatching { Class.forName("com.qinhuai.clazz.api.QinhClassAPI").getField("INSTANCE").get(null) }.getOrNull()
    }

    override fun isAvailable(): Boolean = api != null

    override fun getGrowth(player: Player): Double = runCatching {
        val a = api ?: return 0.0
        (a.javaClass.getMethod("getLevel", Player::class.java).invoke(a, player) as? Int)?.toDouble() ?: 0.0
    }.getOrDefault(0.0)

    override fun hasClass(player: Player, classId: String): Boolean = runCatching {
        val a = api ?: return false
        a.javaClass.getMethod("isClass", Player::class.java, String::class.java).invoke(a, player, classId) as? Boolean ?: false
    }.getOrDefault(false)
}

object MMOCoreGrowthProvider : GrowthProvider {
    override val id = "mmocore"

    private val playerDataClass: Class<*>? by lazy {
        runCatching { Class.forName("net.Indyuce.mmocore.api.player.PlayerData") }.getOrNull()
    }

    override fun isAvailable(): Boolean = playerDataClass != null

    private fun data(player: Player): Any? = runCatching {
        playerDataClass!!.getMethod("get", UUID::class.java).invoke(null, player.uniqueId)
    }.getOrNull()

    override fun getGrowth(player: Player): Double = runCatching {
        val d = data(player) ?: return 0.0
        (d.javaClass.getMethod("getLevel").invoke(d) as? Int)?.toDouble() ?: 0.0
    }.getOrDefault(0.0)

    override fun hasClass(player: Player, classId: String): Boolean = runCatching {
        val d = data(player) ?: return false
        val prof = d.javaClass.getMethod("getProfess").invoke(d) ?: return false
        val pid = prof.javaClass.getMethod("getId").invoke(prof) as? String ?: return false
        pid.equals(classId, ignoreCase = true)
    }.getOrDefault(false)
}

object MMOCorePartyProvider : PartyProvider {
    override val id = "mmocore"

    private val playerDataClass: Class<*>? by lazy {
        runCatching { Class.forName("net.Indyuce.mmocore.api.player.PlayerData") }.getOrNull()
    }

    override fun isAvailable(): Boolean = playerDataClass != null

    override fun getParty(player: Player): RuinParty {
        val solo = RuinParty(player.uniqueId, listOf(player))
        return runCatching {
            val data = playerDataClass!!.getMethod("get", UUID::class.java).invoke(null, player.uniqueId) ?: return solo
            val party = data.javaClass.getMethod("getParty").invoke(data) ?: return solo
            val raw = party.javaClass.getMethod("getOnlineMembers").invoke(party) as? Collection<*> ?: return solo
            val members = raw.filterIsInstance<Player>()
            if (members.isEmpty()) solo else RuinParty(members.first().uniqueId, members)
        }.getOrDefault(solo)
    }
}
