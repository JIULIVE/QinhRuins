package com.qinhuai.ruins.generation

import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import java.util.Random

object ChunkHook : Listener {

    private val random = Random()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!NaturalGenerator.isEnabled()) return
        if (!event.isNewChunk) return

        val chunk = event.chunk
        if (GenerationDirector.isEnabled()) {
            GenerationDirector.onNewChunk(chunk.world, chunk.x, chunk.z)
            return
        }

        val worldName = chunk.world.name

        for (template in TemplateRegistry.all()) {
            val gen = template.generation
            if (!gen.enabled) continue
            if (!com.qinhuai.ruins.template.GenerationProfiles.eligibleWorld(gen, chunk.world)) continue
            if (gen.probabilityDenominator <= 0) continue
            if (random.nextInt(gen.probabilityDenominator) < gen.probabilityNumerator) {
                NaturalGenerator.enqueue(worldName, chunk.x, chunk.z, template.id)
            }
        }
    }
}
