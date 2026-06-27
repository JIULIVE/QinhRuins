package com.qinhuai.ruins.editor

import com.qinhuai.ruins.core.MechAction
import com.qinhuai.ruins.core.MechRegion
import com.qinhuai.ruins.core.MechTrigger
import com.qinhuai.ruins.core.MechTriggerType
import com.qinhuai.ruins.core.Mechanism
import com.qinhuai.ruins.core.RelPos
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object BlueprintWriter {

    fun write(file: File, session: EditorSession) {
        val yml = YamlConfiguration()
        yml.set("spawn-points", session.spawnPoints.map { sp ->
            linkedMapOf<String, Any>(
                "id" to sp.id,
                "x" to sp.pos.x, "y" to sp.pos.y, "z" to sp.pos.z,
                "mob" to sp.mob, "count" to sp.count, "level" to sp.level, "stage" to sp.stage,
            )
        })
        yml.set("loot-chests", session.lootChests.map { c ->
            linkedMapOf<String, Any>(
                "id" to c.id,
                "x" to c.pos.x, "y" to c.pos.y, "z" to c.pos.z,
                "loot-table" to c.lootTable,
                "per-player-once" to c.perPlayerOnce,
                "growth-scaled" to c.growthScaled,
                "unlock-stage" to c.unlockStage,
            )
        })
        if (session.objectives.isNotEmpty()) {
            yml.set("objectives.stages", session.objectives.stages.map { st ->
                linkedMapOf<String, Any>(
                    "stage" to st.stage,
                    "name" to st.name,
                    "kills" to st.kills.associate { it.mobKey to it.amount },
                )
            })
        }
        if (session.mechanisms.isNotEmpty()) {
            yml.set("mechanisms", session.mechanisms.map { mechToMap(it) })
        }
        if (session.cores.isNotEmpty()) {
            yml.set("cores", session.cores.map { linkedMapOf("x" to it.x, "y" to it.y, "z" to it.z) })
        }
        if (session.spawnCommands.isNotEmpty()) {
            yml.set("spawn-commands", session.spawnCommands.map {
                linkedMapOf<String, Any>("x" to it.pos.x, "y" to it.pos.y, "z" to it.pos.z, "command" to it.command)
            })
        }
        if (session.variants.isNotEmpty()) {
            yml.set("variants", session.variants.map { slot ->
                linkedMapOf(
                    "id" to slot.id,
                    "x" to slot.pos.x, "y" to slot.pos.y, "z" to slot.pos.z,
                    "surface" to slot.surface,
                    "y-min" to slot.yMin, "y-max" to slot.yMax,
                    "options" to slot.options.map { linkedMapOf("template" to it.templateId, "weight" to it.weight) },
                )
            })
        }
        file.parentFile?.mkdirs()
        runCatching { yml.save(file) }
    }

    private fun mechToMap(mech: Mechanism): Map<String, Any> {
        val map = linkedMapOf<String, Any>("id" to mech.id)
        if (mech.once) map["once"] = true
        if (mech.cooldownSeconds > 0) map["cooldown"] = mech.cooldownSeconds.toInt()
        if (mech.requireStage > 0) map["require-stage"] = mech.requireStage
        map["radius"] = mech.radius
        map["trigger"] = triggerToMap(mech.trigger)
        map["actions"] = mech.actions.map { actionToMap(it) }
        return map
    }

    private fun triggerToMap(trigger: MechTrigger): Map<String, Any> {
        val map = linkedMapOf<String, Any>("type" to trigger.type.name.lowercase().replace('_', '-'))
        trigger.pos?.let { putPos(map, it) }
        trigger.region?.let { putRegion(map, it) }
        if (trigger.type == MechTriggerType.STAGE) map["stage"] = trigger.stage
        if (trigger.type == MechTriggerType.TIMER) map["interval"] = trigger.intervalSeconds.toInt()
        return map
    }

    private fun actionToMap(action: MechAction): Map<String, Any> {
        val map = linkedMapOf<String, Any>("type" to action.type.name.lowercase())
        action.params.forEach { (k, v) -> map[k] = v }
        action.pos?.let { putPos(map, it) }
        action.region?.let { putRegion(map, it) }
        return map
    }

    private fun putPos(map: MutableMap<String, Any>, pos: RelPos) {
        map["x"] = pos.x
        map["y"] = pos.y
        map["z"] = pos.z
    }

    private fun putRegion(map: MutableMap<String, Any>, region: MechRegion) {
        map["from"] = linkedMapOf("x" to region.from.x, "y" to region.from.y, "z" to region.from.z)
        map["to"] = linkedMapOf("x" to region.to.x, "y" to region.to.y, "z" to region.to.z)
    }
}
