package com.qinhuai.ruins.guide

import com.qinhuai.ruins.lang.Lang
import org.bukkit.Location
import kotlin.math.atan2
import kotlin.math.roundToInt

object Direction {

    private val KEYS = arrayOf(
        "gui.dir-n", "gui.dir-ne", "gui.dir-e", "gui.dir-se",
        "gui.dir-s", "gui.dir-sw", "gui.dir-w", "gui.dir-nw",
    )

    fun compass(from: Location, to: Location): String {
        val dx = to.x - from.x
        val dz = to.z - from.z
        var bearing = Math.toDegrees(atan2(dx, -dz))
        if (bearing < 0) bearing += 360.0
        val index = (bearing / 45.0).roundToInt() % 8
        return Lang.get(KEYS[index])
    }
}
