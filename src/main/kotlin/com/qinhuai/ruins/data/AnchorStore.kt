package com.qinhuai.ruins.data

import com.qinhuai.corelib.util.LocationUtils
import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.AnchorState
import com.qinhuai.ruins.structure.Rotations
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object AnchorStore {

    private lateinit var file: File

    fun init(file: File) {
        this.file = file
    }

    fun loadAll(): List<Anchor> {
        if (!::file.isInitialized || !file.exists()) return emptyList()
        val yml = YamlConfiguration.loadConfiguration(file)
        val section = yml.getConfigurationSection("anchors") ?: return emptyList()
        val result = ArrayList<Anchor>()
        for (id in section.getKeys(false)) {
            val base = "anchors.$id"
            val location = LocationUtils.deserialize(yml.getString("$base.location") ?: continue) ?: continue
            val template = yml.getString("$base.template") ?: continue
            val state = runCatching { AnchorState.valueOf(yml.getString("$base.state") ?: "DORMANT") }
                .getOrDefault(AnchorState.DORMANT)
            val rotation = Rotations.parse(yml.getString("$base.rotation"))
            result.add(
                Anchor(
                    id, template, location, state, rotation,
                    yml.getInt("$base.width", 0),
                    yml.getInt("$base.height", 0),
                    yml.getInt("$base.depth", 0),
                    yml.getLong("$base.cleared-at", 0L),
                ),
            )
        }
        return result
    }

    fun saveAll(anchors: Collection<Anchor>) {
        if (!::file.isInitialized) return
        val yml = YamlConfiguration()
        for (anchor in anchors) {
            val base = "anchors.${anchor.id}"
            yml.set("$base.template", anchor.templateId)
            yml.set("$base.location", LocationUtils.serialize(anchor.location))
            yml.set("$base.state", anchor.state.name)
            yml.set("$base.rotation", anchor.rotation.name)
            if (anchor.width > 0) yml.set("$base.width", anchor.width)
            if (anchor.height > 0) yml.set("$base.height", anchor.height)
            if (anchor.depth > 0) yml.set("$base.depth", anchor.depth)
            if (anchor.clearedAt > 0) yml.set("$base.cleared-at", anchor.clearedAt)
        }
        runCatching { yml.save(file) }
    }
}
