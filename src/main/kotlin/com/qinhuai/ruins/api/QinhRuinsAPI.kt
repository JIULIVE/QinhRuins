package com.qinhuai.ruins.api

import com.qinhuai.ruins.core.Anchor
import com.qinhuai.ruins.core.AnchorState
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.generation.AnchorRecycler
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Location

object QinhRuinsAPI {

    data class RuinInfo(
        val anchorId: String,
        val templateId: String,
        val location: Location,
        val cleared: Boolean,
    )

    private fun Anchor.toInfo() = RuinInfo(
        id, templateId, location.clone(),
        state == AnchorState.CLEARED || state == AnchorState.RECYCLED,
    )

    fun spawn(templateId: String, location: Location): String? {
        val template = TemplateRegistry.get(templateId) ?: return null
        return AnchorManager.spawn(template, location.block.location).anchor?.id
    }

    fun count(): Int = AnchorManager.all().size

    fun ruinAt(location: Location): RuinInfo? = AnchorManager.containing(location)?.toInfo()

    fun nearest(location: Location, templateId: String? = null, radius: Double = 12000.0): RuinInfo? =
        if (templateId != null) {
            AnchorManager.nearestOfTemplate(location, templateId)?.toInfo()
        } else {
            AnchorManager.nearby(location, radius).firstOrNull()?.toInfo()
        }

    fun list(templateId: String? = null): List<RuinInfo> =
        AnchorManager.all().filter { templateId == null || it.templateId == templateId }.map { it.toInfo() }

    fun remove(anchorId: String): Boolean = AnchorRecycler.recycle(anchorId).found

    fun templateIds(): List<String> = TemplateRegistry.ids()
}
