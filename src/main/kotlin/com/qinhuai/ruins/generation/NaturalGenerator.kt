package com.qinhuai.ruins.generation

import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.ArrayDeque
import java.util.Random

object NaturalGenerator {

    private data class Candidate(val world: String, val chunkX: Int, val chunkZ: Int, val templateId: String)

    private val queue = ArrayDeque<Candidate>()
    private val random = Random()
    private var task: BukkitTask? = null

    private var enabled = false
    private var maxPerRun = 4
    private var maxQueue = 2000
    private var maxMillisPerRun = 3L

    fun isEnabled(): Boolean = enabled

    fun start(plugin: JavaPlugin, enabled: Boolean, maxPerRun: Int, periodTicks: Long, maxMillisPerRun: Long = 3L) {
        this.enabled = enabled
        this.maxPerRun = maxPerRun.coerceAtLeast(1)
        this.maxMillisPerRun = maxMillisPerRun.coerceAtLeast(1L)
        stop()
        if (!enabled) return
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { process() }, periodTicks, periodTicks)
    }

    fun stop() {
        task?.cancel()
        task = null
        queue.clear()
    }

    private var lastFullWarn = 0L

    fun enqueue(world: String, chunkX: Int, chunkZ: Int, templateId: String) {
        if (!enabled) return
        if (queue.size >= maxQueue) {
            val now = System.currentTimeMillis()
            if (now - lastFullWarn > 60_000) {
                lastFullWarn = now
                com.qinhuai.ruins.QinhRuins.instance.logger.warning("[QinhRuins] 自然生成候选队列已满（$maxQueue），部分候选被丢弃——生成速率跟不上（可调大 generation.max-per-tick / max-millis-per-tick，或调低 director.live.chance）")
            }
            return
        }
        queue.add(Candidate(world, chunkX, chunkZ, templateId))
    }

    private fun process() {
        if (queue.isEmpty()) return
        val deadline = System.currentTimeMillis() + maxMillisPerRun
        var handled = 0
        while (handled < maxPerRun) {
            val candidate = queue.poll() ?: return
            handled++
            handle(candidate)
            if (System.currentTimeMillis() >= deadline) return
        }
    }

    private fun handle(candidate: Candidate) {
        val world = Bukkit.getWorld(candidate.world) ?: return
        if (!world.isChunkLoaded(candidate.chunkX, candidate.chunkZ)) return
        var template = TemplateRegistry.get(candidate.templateId) ?: return
        repeat(3) {
            val placed = runCatching { AnchorPlacer.tryPlace(world, candidate.chunkX, candidate.chunkZ, template, random) }.getOrDefault(false)
            if (placed) return
            template = GenerationDirector.pickForChunk(world) ?: return
        }
    }
}
