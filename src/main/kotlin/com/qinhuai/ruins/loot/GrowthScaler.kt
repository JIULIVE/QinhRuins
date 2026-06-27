package com.qinhuai.ruins.loot

object GrowthScaler {

    fun scaleAmount(base: Int, growth: Double): Int {
        val scaled = (base * (1.0 + growth / 200.0)).toInt()
        return scaled.coerceAtLeast(base)
    }
}
