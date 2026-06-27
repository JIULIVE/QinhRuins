package com.qinhuai.ruins.util

object Profiler {

    class Stat {
        var count: Long = 0
        var totalNanos: Long = 0
        var maxNanos: Long = 0
    }

    data class Line(val name: String, val count: Long, val avgMs: Double, val maxMs: Double, val totalMs: Double)

    private val stats = LinkedHashMap<String, Stat>()

    @Synchronized
    fun record(name: String, nanos: Long) {
        val stat = stats.getOrPut(name) { Stat() }
        stat.count++
        stat.totalNanos += nanos
        if (nanos > stat.maxNanos) stat.maxNanos = nanos
    }

    inline fun <T> time(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(name, System.nanoTime() - start)
        }
    }

    @Synchronized
    fun summary(): List<Line> = stats.entries
        .map { (name, s) ->
            val total = s.totalNanos / 1_000_000.0
            val avg = if (s.count > 0) total / s.count else 0.0
            Line(name, s.count, avg, s.maxNanos / 1_000_000.0, total)
        }
        .sortedByDescending { it.totalMs }

    @Synchronized
    fun reset() {
        stats.clear()
    }

    @Synchronized
    fun isEmpty(): Boolean = stats.isEmpty()
}
