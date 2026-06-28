package com.qinhuai.ruins.template

import com.qinhuai.ruins.core.Connector
import com.qinhuai.ruins.core.RelPos
import com.qinhuai.ruins.core.Side
import com.qinhuai.ruins.core.TileDef
import com.qinhuai.ruins.core.TilePalette
import com.qinhuai.ruins.structure.PasteEngines
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

object TilePaletteRegistry {

    private val palettes = HashMap<String, TilePalette>()

    fun load(dir: File, logger: Logger): Int {
        palettes.clear()
        if (!dir.exists()) {
            dir.mkdirs()
            return 0
        }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return 0
        for (file in files) {
            runCatching { parse(file, logger) }
                .onSuccess { if (it != null) palettes[it.id] = it }
                .onFailure { logger.warning("[QinhRuins] 调色板解析失败 ${file.name}: ${it.message}") }
        }
        return palettes.size
    }

    fun get(id: String): TilePalette? = palettes[id]

    fun ids(): List<String> = palettes.keys.sorted()

    private fun parse(file: File, logger: Logger): TilePalette? {
        val yml = YamlConfiguration.loadConfiguration(file)
        val id = file.nameWithoutExtension
        val tiles = yml.getMapList("tiles").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as Map<String, Any?>
            val templateId = m["template"]?.toString() ?: return@mapNotNull null
            val template = TemplateRegistry.get(templateId)
            if (template == null) {
                logger.warning("[QinhRuins] 调色板 $id 引用了不存在的模板 $templateId，跳过")
                return@mapNotNull null
            }
            val size = PasteEngines.active().structureSize(template)
            if (size == null) {
                logger.warning("[QinhRuins] 模板 $templateId 无法读取结构尺寸，跳过")
                return@mapNotNull null
            }
            TileDef(
                templateId = templateId,
                size = RelPos(size.blockX, size.blockY, size.blockZ),
                connectors = parseConnectors(m["connectors"]),
                weight = (m["weight"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
                maxCount = (m["max-count"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 99,
                role = m["role"]?.toString() ?: "room",
                repetitionPenalty = (m["repetition-penalty"] as? Number)?.toInt() ?: 0,
                noRepeat = (m["no-repeat"] as? Boolean) ?: false,
            )
        }
        if (tiles.isEmpty()) {
            logger.warning("[QinhRuins] 调色板 $id 没有有效瓦片")
            return null
        }
        return TilePalette(
            id = id,
            maxRooms = yml.getInt("max-rooms", 12).coerceAtLeast(1),
            startTile = yml.getString("start")?.takeIf { it.isNotBlank() },
            tiles = tiles,
        )
    }

    private fun parseConnectors(raw: Any?): List<Connector> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            @Suppress("UNCHECKED_CAST")
            val m = entry as? Map<String, Any?> ?: return@mapNotNull null
            val side = runCatching { Side.valueOf(m["side"].toString().uppercase()) }.getOrNull() ?: return@mapNotNull null
            Connector(side, RelPos(intOf(m["x"]), intOf(m["y"]), intOf(m["z"])))
        }
    }

    private fun intOf(value: Any?): Int = (value as? Number)?.toInt() ?: 0
}
