package com.qinhuai.ruins.structure

import com.qinhuai.ruins.lang.Lang
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Sign
import org.bukkit.block.data.BlockData
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BlockVector
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream

object SchematicImporter {

    private const val MAX_VOLUME = 8_000_000
    private const val BLOCKS_PER_TICK = 40_000

    fun import(
        plugin: JavaPlugin,
        player: Player,
        schemFile: File,
        templatesDir: File,
        templateId: String,
        onDone: (String) -> Unit,
    ) {
        if (!schemFile.exists()) return onDone(Lang.get("cmd.import-file-not-found", "name" to schemFile.name))

        val root = runCatching { Nbt.read(schemFile) }.getOrElse { return onDone(Lang.get("cmd.import-nbt-failed", "error" to it.message)) }

        @Suppress("UNCHECKED_CAST")
        val schem = (root["Schematic"] as? Map<String, Any>) ?: root
        val width = intOf(schem["Width"]) ?: return onDone(Lang.get("cmd.import-missing-width"))
        val height = intOf(schem["Height"]) ?: return onDone(Lang.get("cmd.import-missing-height"))
        val length = intOf(schem["Length"]) ?: return onDone(Lang.get("cmd.import-missing-length"))
        val volume = width * height * length
        if (volume <= 0) return onDone(Lang.get("cmd.import-bad-size", "w" to width, "h" to height, "l" to length))
        if (volume > MAX_VOLUME) return onDone(Lang.get("cmd.import-too-large", "volume" to volume, "max" to MAX_VOLUME))

        @Suppress("UNCHECKED_CAST")
        val blocksTag = schem["Blocks"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val paletteTag = (blocksTag?.get("Palette") ?: schem["Palette"]) as? Map<String, Any>
            ?: return onDone(Lang.get("cmd.import-missing-palette"))
        val dataBytes = (blocksTag?.get("Data") ?: schem["BlockData"]) as? ByteArray
            ?: return onDone(Lang.get("cmd.import-missing-blockdata"))
        if (paletteTag.isEmpty()) return onDone(Lang.get("cmd.import-empty-palette"))

        val maxId = paletteTag.values.maxOf { (it as? Number)?.toInt() ?: 0 }
        val palette = arrayOfNulls<String>(maxId + 1)
        for ((state, id) in paletteTag) {
            val idx = (id as? Number)?.toInt() ?: continue
            palette[idx] = state
        }

        val indices = decodeVarints(dataBytes, volume) {
            plugin.logger.warning("[QinhRuins] schem 方块数据流提前结束（文件可能不完整），缺失部分填为调色板首项: ${schemFile.name}")
        }
        val blockEntities = runCatching { parseBlockEntities(blocksTag, schem) }.getOrDefault(emptyMap())

        val world = player.world
        val origin = player.location.block.location
        val size = BlockVector(width, height, length)

        val backup = File(templatesDir.parentFile, "import-backup.nbt")
        val backupResult = PasteEngines.active().capture(backup, origin, size, false)
        if (!backupResult.success) return onDone(Lang.get("cmd.import-backup-failed", "error" to backupResult.message))

        val dataCache = HashMap<String, BlockData?>()
        object : BukkitRunnable() {
            private var cursor = 0
            private var placed = 0
            override fun run() {
                var n = 0
                while (cursor < indices.size && n < BLOCKS_PER_TICK) {
                    val state = palette.getOrNull(indices[cursor])
                    if (state != null) {
                        val data = dataCache.getOrPut(state) { runCatching { Bukkit.createBlockData(state) }.getOrNull() }
                        if (data != null) {
                            val x = cursor % width
                            val z = (cursor / width) % length
                            val y = cursor / (width * length)
                            world.getBlockAt(origin.blockX + x, origin.blockY + y, origin.blockZ + z).setBlockData(data, false)
                            placed++
                        }
                    }
                    cursor++
                    n++
                }
                if (cursor < indices.size) return
                cancel()
                var markerNote = ""
                runCatching {
                    if (blockEntities.isNotEmpty()) applyBlockEntities(world, origin, blockEntities)
                    val scan = MarkerScanner.scan(world, origin.blockX, origin.blockY, origin.blockZ, width, height, length)
                    if (!scan.isEmpty()) {
                        MarkerScanner.applyMarkerBlocks(scan)
                        MarkerScanner.writeBlueprint(File(templatesDir, "$templateId/blueprint.yml"), scan)
                        markerNote = Lang.get("cmd.import-marker-note", "spawns" to scan.spawnPoints.size, "chests" to scan.lootChests.size, "cores" to scan.cores.size)
                    }
                }
                val structFile = File(templatesDir, "$templateId/structure.nbt")
                structFile.parentFile?.mkdirs()
                val captureResult = PasteEngines.active().capture(structFile, origin, size, false)
                PasteEngines.active().placeFile(backup, origin)
                backup.delete()
                if (!captureResult.success) {
                    onDone(Lang.get("cmd.import-save-failed", "error" to captureResult.message))
                    return
                }
                writeTemplate(File(templatesDir, "$templateId/template.yml"), templateId)
                onDone(Lang.get("cmd.import-done", "id" to templateId, "w" to width, "h" to height, "l" to length, "placed" to placed, "note" to markerNote))
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    private fun writeTemplate(file: File, templateId: String) {
        val yml = YamlConfiguration()
        yml.set("id", templateId)
        yml.set("display", templateId)
        yml.set("structure.file", "structure.nbt")
        runCatching { yml.save(file) }
    }

    private fun decodeVarints(bytes: ByteArray, count: Int, onTruncated: () -> Unit): IntArray {
        val result = IntArray(count)
        var pos = 0
        var index = 0
        while (index < count && pos < bytes.size) {
            var value = 0
            var shift = 0
            while (true) {
                val b = bytes[pos].toInt() and 0xFF
                pos++
                value = value or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (pos >= bytes.size) break
            }
            result[index++] = value
        }
        if (index < count) onTruncated()
        return result
    }

    private fun intOf(value: Any?): Int? = (value as? Number)?.toInt()

    private fun parseBlockEntities(blocksTag: Map<String, Any>?, schem: Map<String, Any>): Map<String, Map<*, *>> {
        val list = (blocksTag?.get("BlockEntities") ?: schem["BlockEntities"] ?: schem["TileEntities"]) as? List<*>
            ?: return emptyMap()
        val out = LinkedHashMap<String, Map<*, *>>()
        for (entry in list) {
            val be = entry as? Map<*, *> ?: continue
            val pos = posOf(be["Pos"]) ?: continue
            out["${pos[0]},${pos[1]},${pos[2]}"] = be
        }
        return out
    }

    private fun applyBlockEntities(world: org.bukkit.World, origin: Location, entities: Map<String, Map<*, *>>) {
        for ((key, be) in entities) {
            val parts = key.split(',').mapNotNull { it.toIntOrNull() }
            if (parts.size < 3) continue
            val block = world.getBlockAt(origin.blockX + parts[0], origin.blockY + parts[1], origin.blockZ + parts[2])
            val id = ((be["Id"] ?: be["id"]) as? String)?.lowercase() ?: continue
            when {
                id.contains("sign") -> applySign(block, signTextOf(be))
                id.contains("spawner") -> applySpawner(block, be)
            }
        }
    }

    private fun applySign(block: org.bukkit.block.Block, lines: List<String>) {
        if (lines.none { it.isNotEmpty() }) return
        val state = block.state as? Sign ?: return
        runCatching {
            @Suppress("DEPRECATION")
            val side = state.getSide(org.bukkit.block.sign.Side.FRONT)
            for (i in 0 until minOf(4, lines.size)) {
                @Suppress("DEPRECATION")
                side.setLine(i, lines[i])
            }
            state.update(true, false)
        }
    }

    private fun applySpawner(block: org.bukkit.block.Block, be: Map<*, *>) {
        val state = block.state as? org.bukkit.block.CreatureSpawner ?: return
        val typeId = spawnerEntityId(be) ?: return
        runCatching {
            val nsKey = org.bukkit.NamespacedKey.fromString(typeId.lowercase()) ?: return
            val type = org.bukkit.Registry.ENTITY_TYPE.get(nsKey) ?: return
            state.spawnedType = type
            state.update(true, false)
        }
    }

    private fun spawnerEntityId(be: Map<*, *>): String? {
        (be["SpawnData"] as? Map<*, *>)?.let { sd ->
            ((sd["entity"] as? Map<*, *>)?.get("id") as? String)?.let { return it }
        }
        (be["EntityId"] as? String)?.let { return it }
        return null
    }

    private fun posOf(value: Any?): IntArray? = when (value) {
        is IntArray -> if (value.size >= 3) value else null
        is List<*> -> if (value.size >= 3) IntArray(3) { (value[it] as? Number)?.toInt() ?: 0 } else null
        else -> null
    }

    private fun signTextOf(be: Map<*, *>): List<String> {
        (be["front_text"] as? Map<*, *>)?.let { ft ->
            (ft["messages"] as? List<*>)?.let { msgs ->
                return msgs.map { extractPlain(it?.toString() ?: "") }
            }
        }
        val legacy = (1..4).map { extractPlain((be["Text$it"] as? String) ?: "") }
        return legacy
    }

    private fun extractPlain(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty() || s == "\"\"" || s == "{}") return ""
        if (s.startsWith("{")) {
            val m = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(s) ?: return ""
            return m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return s.trim('"')
    }

    private object Nbt {
        fun read(file: File): Map<String, Any> {
            val gzip = file.inputStream().use { it.read() == 0x1f && it.read() == 0x8b }
            val stream = if (gzip) GZIPInputStream(file.inputStream().buffered()) else file.inputStream().buffered()
            DataInputStream(stream).use { input ->
                val type = input.readByte().toInt()
                require(type == 10) { "根标签不是 Compound" }
                input.readUTF()
                return readCompound(input)
            }
        }

        private fun readCompound(input: DataInputStream): Map<String, Any> {
            val map = LinkedHashMap<String, Any>()
            while (true) {
                val type = input.readByte().toInt()
                if (type == 0) break
                val name = input.readUTF()
                map[name] = readPayload(input, type)
            }
            return map
        }

        private fun readPayload(input: DataInputStream, type: Int): Any = when (type) {
            1 -> input.readByte()
            2 -> input.readShort()
            3 -> input.readInt()
            4 -> input.readLong()
            5 -> input.readFloat()
            6 -> input.readDouble()
            7 -> ByteArray(input.readInt()).also { input.readFully(it) }
            8 -> input.readUTF()
            9 -> {
                val elemType = input.readByte().toInt()
                val len = input.readInt()
                val list = ArrayList<Any>(len.coerceAtLeast(0))
                repeat(len) { list.add(readPayload(input, elemType)) }
                list
            }
            10 -> readCompound(input)
            11 -> IntArray(input.readInt()) { input.readInt() }
            12 -> LongArray(input.readInt()) { input.readLong() }
            else -> error("未知 NBT 标签类型 $type")
        }
    }
}
