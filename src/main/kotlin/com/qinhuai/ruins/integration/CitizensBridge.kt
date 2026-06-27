package com.qinhuai.ruins.integration

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.EntityType

object CitizensBridge {

    private val idsByAnchor = HashMap<String, MutableList<Int>>()
    private val namesByAnchor = HashMap<String, MutableSet<String>>()

    fun isAvailable(): Boolean =
        Bukkit.getPluginManager().getPlugin("Citizens")?.isEnabled == true

    fun spawnFor(anchorId: String, location: Location, name: String, skin: String?): Int? {
        if (!isAvailable()) return null
        val names = namesByAnchor.getOrPut(anchorId) { HashSet() }
        if (!names.add(name)) return null
        val id = spawn(location, name, skin)
        if (id == null) {
            names.remove(name)
            return null
        }
        idsByAnchor.getOrPut(anchorId) { ArrayList() }.add(id)
        return id
    }

    fun despawnAnchor(anchorId: String) {
        namesByAnchor.remove(anchorId)
        idsByAnchor.remove(anchorId)?.forEach { despawn(it) }
    }

    private fun spawn(location: Location, name: String, skin: String?): Int? = runCatching {
        val apiClass = Class.forName("net.citizensnpcs.api.CitizensAPI")
        val registry = apiClass.getMethod("getNPCRegistry").invoke(null)
        val npc = registry.javaClass
            .getMethod("createNPC", EntityType::class.java, String::class.java)
            .invoke(registry, EntityType.PLAYER, name)
        if (!skin.isNullOrBlank()) {
            runCatching {
                val skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait")
                val trait = npc.javaClass.getMethod("getOrAddTrait", Class::class.java).invoke(npc, skinTraitClass)
                skinTraitClass.getMethod("setSkinName", String::class.java).invoke(trait, skin)
            }
        }
        npc.javaClass.getMethod("spawn", Location::class.java).invoke(npc, location)
        npc.javaClass.getMethod("getId").invoke(npc) as Int
    }.getOrNull()

    private fun despawn(id: Int) {
        runCatching {
            val apiClass = Class.forName("net.citizensnpcs.api.CitizensAPI")
            val registry = apiClass.getMethod("getNPCRegistry").invoke(null)
            val npc = registry.javaClass.getMethod("getById", Int::class.javaPrimitiveType)
                .invoke(registry, id) ?: return
            npc.javaClass.getMethod("destroy").invoke(npc)
        }
    }
}
