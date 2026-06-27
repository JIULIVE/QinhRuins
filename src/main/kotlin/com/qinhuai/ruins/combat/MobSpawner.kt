package com.qinhuai.ruins.combat

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType

object MobSpawner {

    fun spawn(ref: String, at: Location, level: Int): Entity? {
        return if (ref.startsWith("mm-", ignoreCase = true)) {
            spawnMythic(ref.substring(3), at, level)
        } else {
            spawnVanilla(ref, at)
        }
    }

    private fun spawnVanilla(name: String, at: Location): Entity? {
        val type = runCatching { EntityType.valueOf(name.uppercase()) }.getOrNull() ?: return null
        if (!type.isSpawnable) return null
        val world = at.world ?: return null
        return runCatching { world.spawnEntity(at, type) }.getOrNull()
    }

    private fun spawnMythic(name: String, at: Location, level: Int): Entity? {
        return try {
            val mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            val instance = mythicBukkit.getMethod("inst").invoke(null)
            val apiHelper = instance.javaClass.getMethod("getAPIHelper").invoke(instance)
            val spawn = apiHelper.javaClass.getMethod(
                "spawnMythicMob",
                String::class.java,
                Location::class.java,
                Int::class.javaPrimitiveType,
            )
            spawn.invoke(apiHelper, name, at, level) as? Entity
        } catch (e: Throwable) {
            null
        }
    }
}
