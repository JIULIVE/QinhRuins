package com.qinhuai.ruins.core

object Durations {

    fun seconds(value: String?): Long {
        if (value.isNullOrBlank()) return 0
        val t = value.trim().lowercase()
        val unit = t.last()
        if (unit.isDigit()) return t.toLongOrNull() ?: 0
        val num = t.dropLast(1).toLongOrNull() ?: return 0
        return when (unit) {
            's' -> num
            'm' -> num * 60
            'h' -> num * 3600
            'd' -> num * 86400
            else -> 0
        }
    }

    fun format(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0:00"
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d".format(m, s)
    }
}
