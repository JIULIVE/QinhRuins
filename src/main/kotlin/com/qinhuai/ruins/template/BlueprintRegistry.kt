package com.qinhuai.ruins.template

import com.qinhuai.ruins.core.Blueprint
import com.qinhuai.ruins.core.KillRequirement
import com.qinhuai.ruins.core.LootChest
import com.qinhuai.ruins.core.MechAction
import com.qinhuai.ruins.core.MechActionType
import com.qinhuai.ruins.core.MechRegion
import com.qinhuai.ruins.core.MechTrigger
import com.qinhuai.ruins.core.MechTriggerType
import com.qinhuai.ruins.core.Mechanism
import com.qinhuai.ruins.core.ObjectiveStage
import com.qinhuai.ruins.core.Objectives
import com.qinhuai.ruins.core.RelPos
import com.qinhuai.ruins.core.SpawnPoint
import com.qinhuai.ruins.core.VariantOption
import com.qinhuai.ruins.core.VariantSlot
import com.qinhuai.ruins.lang.Lang
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object BlueprintRegistry {

    private val blueprints = HashMap<String, Blueprint>()

    fun load(templatesDir: File): Int {
        blueprints.clear()
        val dirs = templatesDir.listFiles { f -> f.isDirectory } ?: return 0
        for (dir in dirs) {
            if (dir.name.startsWith("_") || dir.name.startsWith(".")) continue
            val file = File(dir, "blueprint.yml")
            if (!file.exists()) continue
            runCatching { parse(file) }.onSuccess { blueprints[dir.name] = it }
        }
        return blueprints.size
    }

    fun get(templateId: String): Blueprint? = blueprints[templateId]

    private fun parse(file: File): Blueprint {
        val yml = YamlConfiguration.loadConfiguration(file)
        val spawnPoints = yml.getMapList("spawn-points").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            val id = m["id"]?.toString() ?: return@mapNotNull null
            val mob = m["mob"]?.toString() ?: return@mapNotNull null
            SpawnPoint(
                id = id,
                pos = RelPos(intOf(m["x"]), intOf(m["y"]), intOf(m["z"])),
                mob = mob,
                count = intOf(m["count"], 1).coerceAtLeast(1),
                level = intOf(m["level"], 1),
                stage = intOf(m["stage"], 1),
            )
        }
        val lootChests = yml.getMapList("loot-chests").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            val id = m["id"]?.toString() ?: return@mapNotNull null
            val table = m["loot-table"]?.toString() ?: return@mapNotNull null
            LootChest(
                id = id,
                pos = RelPos(intOf(m["x"]), intOf(m["y"]), intOf(m["z"])),
                lootTable = table,
                perPlayerOnce = boolOf(m["per-player-once"], true),
                growthScaled = boolOf(m["growth-scaled"], true),
                unlockStage = intOf(m["unlock-stage"], 1),
            )
        }
        val cores = yml.getMapList("cores").map { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            RelPos(intOf(m["x"]), intOf(m["y"]), intOf(m["z"]))
        }
        val spawnCommands = yml.getMapList("spawn-commands").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            val cmd = m["command"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            com.qinhuai.ruins.core.SpawnCommand(RelPos(intOf(m["x"]), intOf(m["y"]), intOf(m["z"])), cmd)
        }
        return Blueprint(spawnPoints, lootChests, parseObjectives(yml), parseMechanisms(yml), cores, parseVariants(yml), spawnCommands, parseBossGate(yml))
    }

    private fun parseBossGate(yml: YamlConfiguration): com.qinhuai.ruins.core.BossGate? {
        val sec = yml.getConfigurationSection("boss-gate") ?: return null
        if (!sec.getBoolean("enabled", true)) return null
        val bossSec = sec.getConfigurationSection("boss") ?: return null
        val mob = bossSec.getString("mob") ?: return null
        val boss = SpawnPoint(
            id = bossSec.getString("id") ?: "boss",
            pos = RelPos(bossSec.getInt("x"), bossSec.getInt("y"), bossSec.getInt("z")),
            mob = mob,
            count = bossSec.getInt("count", 1).coerceAtLeast(1),
            level = bossSec.getInt("level", 1),
            stage = 0,
        )
        val countMobs = sec.getStringList("count-mobs").map { it.lowercase() }.toSet()
        return com.qinhuai.ruins.core.BossGate(
            requiredKills = sec.getInt("required-kills", 10).coerceAtLeast(1),
            countMobs = countMobs,
            announce = sec.getBoolean("announce", true),
            boss = boss,
        )
    }

    private fun parseVariants(yml: YamlConfiguration): List<VariantSlot> =
        yml.getMapList("variants").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            val id = m["id"]?.toString() ?: return@mapNotNull null
            val optionsRaw = m["options"] as? List<*> ?: return@mapNotNull null
            val options = optionsRaw.mapNotNull { o ->
                @Suppress("UNCHECKED_CAST")
                val om = o as? Map<String, Any?> ?: return@mapNotNull null
                val tpl = om["template"]?.toString() ?: return@mapNotNull null
                VariantOption(tpl, intOf(om["weight"], 1).coerceAtLeast(1))
            }
            if (options.isEmpty()) return@mapNotNull null
            VariantSlot(
                id,
                RelPos(intOf(m["x"]), intOf(m["y"]), intOf(m["z"])),
                options,
                boolOf(m["surface"], false),
                intOf(m["y-min"], 0),
                intOf(m["y-max"], 0),
            )
        }

    private fun parseMechanisms(yml: YamlConfiguration): List<Mechanism> =
        yml.getMapList("mechanisms").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            val id = m["id"]?.toString() ?: return@mapNotNull null
            val trigger = parseTrigger(m["trigger"]) ?: return@mapNotNull null
            val actions = parseActions(m["actions"])
            if (actions.isEmpty()) return@mapNotNull null
            Mechanism(
                id = id,
                trigger = trigger,
                actions = actions,
                once = boolOf(m["once"], false),
                cooldownSeconds = intOf(m["cooldown"], 0).toLong(),
                requireStage = intOf(m["require-stage"], 0),
                radius = (m["radius"] as? Number)?.toDouble() ?: 24.0,
            )
        }

    private fun parseTrigger(raw: Any?): MechTrigger? {
        @Suppress("UNCHECKED_CAST")
        val m = raw as? Map<String, Any?> ?: return null
        val type = enumOf<MechTriggerType>(m["type"]) ?: return null
        return MechTrigger(
            type = type,
            pos = relPosOf(m),
            region = regionOf(m),
            intervalSeconds = intOf(m["interval"], 0).toLong(),
            stage = intOf(m["stage"], 0),
        )
    }

    private fun parseActions(raw: Any?): List<MechAction> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            @Suppress("UNCHECKED_CAST")
            val m = entry as? Map<String, Any?> ?: return@mapNotNull null
            val type = enumOf<MechActionType>(m["type"]) ?: return@mapNotNull null
            val reserved = setOf("type", "from", "to", "x", "y", "z", "pos")
            val params = m.filterKeys { it !in reserved }.mapValues { it.value?.toString() ?: "" }
            MechAction(type = type, params = params, region = regionOf(m), pos = relPosOf(m))
        }
    }

    private fun regionOf(m: Map<String, Any?>): MechRegion? {
        @Suppress("UNCHECKED_CAST")
        val from = m["from"] as? Map<String, Any?> ?: return null
        @Suppress("UNCHECKED_CAST")
        val to = m["to"] as? Map<String, Any?> ?: return null
        return MechRegion(RelPos(intOf(from["x"]), intOf(from["y"]), intOf(from["z"])),
            RelPos(intOf(to["x"]), intOf(to["y"]), intOf(to["z"])))
    }

    private fun relPosOf(m: Map<String, Any?>): RelPos? {
        if (m["x"] == null && m["y"] == null && m["z"] == null) return null
        return RelPos(intOf(m["x"]), intOf(m["y"]), intOf(m["z"]))
    }

    private inline fun <reified T : Enum<T>> enumOf(value: Any?): T? {
        val name = value?.toString()?.uppercase()?.replace('-', '_') ?: return null
        return enumValues<T>().firstOrNull { it.name == name }
    }

    private fun parseObjectives(yml: YamlConfiguration): Objectives {
        val stages = yml.getMapList("objectives.stages").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            val stageNum = intOf(m["stage"], -1)
            if (stageNum < 0) return@mapNotNull null
            val killsRaw = m["kills"]
            val kills = if (killsRaw is Map<*, *>) {
                killsRaw.entries.mapNotNull { (k, v) ->
                    val key = k?.toString() ?: return@mapNotNull null
                    val amount = (v as? Number)?.toInt() ?: return@mapNotNull null
                    KillRequirement(key, amount)
                }
            } else emptyList()
            ObjectiveStage(stageNum, m["name"]?.toString() ?: Lang.get("core.stage-fallback", "stage" to stageNum), kills)
        }
        return Objectives(stages)
    }

    private fun intOf(value: Any?, default: Int = 0): Int = (value as? Number)?.toInt() ?: default

    private fun boolOf(value: Any?, default: Boolean): Boolean = (value as? Boolean) ?: default
}
