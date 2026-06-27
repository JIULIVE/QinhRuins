package com.qinhuai.ruins.editor

import com.qinhuai.ruins.core.MechAction
import com.qinhuai.ruins.core.MechRegion
import com.qinhuai.ruins.core.MechTrigger
import com.qinhuai.ruins.core.MechTriggerType
import com.qinhuai.ruins.core.Mechanism
import com.qinhuai.ruins.core.RelPos

class MechBuilder(val id: String) {

    val points = mutableListOf<RelPos>()
    var triggerType: MechTriggerType? = null
    var interactPos: RelPos? = null
    var regionFrom: RelPos? = null
    var regionTo: RelPos? = null
    var stage = 0
    var interval = 0L
    val actions = mutableListOf<MechAction>()
    var once = false
    var cooldown = 0L
    var requireStage = 0
    var radius = 24.0

    fun build(): Mechanism? {
        val type = triggerType ?: return null
        if (actions.isEmpty()) return null
        val trigger = when (type) {
            MechTriggerType.INTERACT, MechTriggerType.BLOCK_BREAK, MechTriggerType.REDSTONE ->
                MechTrigger(type, pos = interactPos ?: return null)
            MechTriggerType.REGION_ENTER -> MechTrigger(
                type,
                region = MechRegion(regionFrom ?: return null, regionTo ?: return null),
            )
            MechTriggerType.STAGE -> MechTrigger(type, stage = stage)
            MechTriggerType.TIMER -> MechTrigger(type, intervalSeconds = interval)
        }
        return Mechanism(id, trigger, actions.toList(), once, cooldown, requireStage, radius)
    }
}
