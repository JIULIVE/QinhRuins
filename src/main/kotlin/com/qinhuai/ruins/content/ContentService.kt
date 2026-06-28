package com.qinhuai.ruins.content

import com.qinhuai.ruins.QinhRuins
import com.qinhuai.ruins.command.QinhRuinsCommands
import com.qinhuai.ruins.lang.Lang
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.zip.ZipInputStream

object ContentService {

    data class PackEntry(
        val id: String,
        val name: String,
        val version: Int,
        val author: String,
        val description: String,
        val url: String,
        val size: String,
    )

    private val ALLOWED_TOP = setOf(
        "templates", "generators", "palettes", "loottables", "blueprints",
        "sections", "lang", "affixes.yml", "pack.yml", "readme.md", "readme.txt",
    )
    private const val MAX_ENTRIES = 20_000

    @Volatile
    private var cachedCatalog: List<PackEntry> = emptyList()

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build()
    }

    fun catalogIds(): List<String> = cachedCatalog.map { it.id }

    private fun config() = QinhRuins.instance.config
    private fun catalogUrl(): String =
        config().getString("content.catalog-url")?.takeIf { it.isNotBlank() } ?: DEFAULT_CATALOG_URL
    private fun maxDownloadBytes(): Long = config().getLong("content.max-download-mb", 50L).coerceAtLeast(1L) * 1024L * 1024L
    private fun allowHttp(): Boolean = config().getBoolean("content.allow-http", false)

    private const val DEFAULT_CATALOG_URL =
        "https://raw.githubusercontent.com/JIULIVE/QinhRuins/main/catalog.yml"

    fun showCatalog(sender: CommandSender) {
        Lang.send(sender, "cmd.content-fetching")
        runAsync {
            val packs = try {
                fetchCatalog()
            } catch (e: Exception) {
                runSync { Lang.send(sender, "cmd.content-catalog-failed", "error" to (e.message ?: "?")) }
                return@runAsync
            }
            runSync {
                if (packs.isEmpty()) {
                    Lang.send(sender, "cmd.content-catalog-empty")
                } else {
                    Lang.send(sender, "cmd.content-catalog-header", "count" to packs.size)
                    val installed = installedVersions()
                    for (p in packs) {
                        val state = when (val v = installed[p.id]) {
                            null -> ""
                            else -> if (v >= p.version) Lang.get("cmd.content-tag-installed") else Lang.get("cmd.content-tag-update")
                        }
                        Lang.send(
                            sender, "cmd.content-catalog-entry",
                            "id" to p.id, "name" to p.name, "version" to p.version,
                            "size" to p.size, "author" to p.author, "desc" to p.description, "state" to state,
                        )
                        if (p.description.isNotBlank()) {
                            Lang.send(sender, "cmd.content-catalog-entry-desc", "desc" to p.description)
                        }
                    }
                }
            }
        }
    }

    fun showInstalled(sender: CommandSender) {
        val yml = packsConfig()
        val section = yml.getConfigurationSection("installed")
        val ids = section?.getKeys(false)?.toList() ?: emptyList()
        if (ids.isEmpty()) {
            Lang.send(sender, "cmd.content-packs-empty")
            return
        }
        Lang.send(sender, "cmd.content-packs-header", "count" to ids.size)
        for (id in ids) {
            Lang.send(
                sender, "cmd.content-packs-entry",
                "id" to id,
                "name" to (section!!.getString("$id.name") ?: id),
                "version" to section.getInt("$id.version"),
            )
        }
    }

    fun download(sender: CommandSender, arg: String) {
        val trimmed = arg.trim()
        Lang.send(sender, "cmd.content-downloading", "src" to trimmed)
        runAsync {
            try {
                val entry: PackEntry? = if (isUrl(trimmed)) null else resolveFromCatalog(trimmed)
                val url = entry?.url ?: trimmed
                if (!isUrl(url)) {
                    runSync { Lang.send(sender, "cmd.content-not-in-catalog", "id" to trimmed) }
                    return@runAsync
                }
                if (url.startsWith("http://", true) && !allowHttp()) {
                    runSync { Lang.send(sender, "cmd.content-http-blocked") }
                    return@runAsync
                }
                val result = installFromUrl(url, entry)
                runSync {
                    val count = QinhRuinsCommands.reloadAll()
                    Lang.send(
                        sender, "cmd.content-installed",
                        "name" to result.name, "version" to result.version,
                        "files" to result.fileCount, "count" to count,
                    )
                }
            } catch (e: Exception) {
                runSync { Lang.send(sender, "cmd.content-download-failed", "error" to (e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    private data class InstallResult(val id: String, val name: String, val version: Int, val fileCount: Int)

    private fun installFromUrl(url: String, entry: PackEntry?): InstallResult {
        val tmp = Files.createTempFile("qr-pack-", ".zip").toFile()
        try {
            downloadTo(url, tmp)
            val extracted = ArrayList<String>()
            var packMeta: ByteArray? = null
            val dataFolder = QinhRuins.instance.dataFolder
            val rootCanon = dataFolder.canonicalPath
            var entries = 0

            ZipInputStream(tmp.inputStream().buffered()).use { zis ->
                var ze = zis.nextEntry
                while (ze != null) {
                    if (++entries > MAX_ENTRIES) throw IllegalStateException("zip entries exceed $MAX_ENTRIES")
                    val rawName = ze.name.replace('\\', '/')
                    if (!ze.isDirectory && rawName.isNotBlank()) {
                        val firstSeg = (if (rawName.contains('/')) rawName.substringBefore('/') else rawName).lowercase()
                        if (firstSeg in ALLOWED_TOP) {
                            if (firstSeg == "pack.yml" && !rawName.contains('/')) {
                                packMeta = zis.readBytes()
                            } else {
                                val dest = File(dataFolder, rawName)
                                if (!dest.canonicalPath.startsWith(rootCanon + File.separator)) {
                                    throw SecurityException("unsafe path: $rawName")
                                }
                                dest.parentFile?.mkdirs()
                                Files.copy(zis, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                extracted.add(rawName)
                            }
                        }
                    }
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            }
            if (extracted.isEmpty()) throw IllegalStateException("no installable content (expected templates/ generators/ palettes/ ...)")

            val meta = packMeta?.let { runCatching { YamlConfiguration.loadConfiguration(InputStreamReader(ByteArrayInputStream(it), StandardCharsets.UTF_8)) }.getOrNull() }
            val id = meta?.getString("id")?.takeIf { it.isNotBlank() }
                ?: entry?.id
                ?: url.substringAfterLast('/').substringBeforeLast('.').ifBlank { "pack" }
            val name = meta?.getString("name")?.takeIf { it.isNotBlank() } ?: entry?.name ?: id
            val version = meta?.getInt("version", entry?.version ?: 1) ?: (entry?.version ?: 1)

            recordInstall(id, name, version, extracted)
            return InstallResult(id, name, version, extracted.size)
        } finally {
            tmp.delete()
        }
    }

    private fun downloadTo(url: String, dest: File) {
        val req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "QinhRuins/${QinhRuins.VERSION}")
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream())
        val cap = maxDownloadBytes()
        var total = 0L
        resp.body().use { input ->
            if (resp.statusCode() !in 200..299) throw IllegalStateException("HTTP ${resp.statusCode()}")
            dest.outputStream().buffered().use { out ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > cap) throw IllegalStateException("download exceeds ${cap / (1024 * 1024)}MB cap")
                    out.write(buf, 0, n)
                }
            }
        }
    }

    private fun resolveFromCatalog(id: String): PackEntry? {
        if (cachedCatalog.isEmpty()) runCatching { fetchCatalog() }
        return cachedCatalog.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    private fun fetchCatalog(): List<PackEntry> {
        val req = HttpRequest.newBuilder(URI.create(catalogUrl()))
            .header("User-Agent", "QinhRuins/${QinhRuins.VERSION}")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (resp.statusCode() !in 200..299) throw IllegalStateException("HTTP ${resp.statusCode()}")
        val yml = YamlConfiguration.loadConfiguration(java.io.StringReader(resp.body()))
        val section = yml.getConfigurationSection("packs") ?: return emptyList<PackEntry>().also { cachedCatalog = it }
        val list = ArrayList<PackEntry>()
        for (id in section.getKeys(false)) {
            val url = section.getString("$id.url")?.takeIf { it.isNotBlank() } ?: continue
            list.add(
                PackEntry(
                    id = id,
                    name = section.getString("$id.name") ?: id,
                    version = section.getInt("$id.version", 1),
                    author = section.getString("$id.author") ?: "?",
                    description = section.getString("$id.description") ?: "",
                    url = url,
                    size = section.getString("$id.size") ?: "?",
                ),
            )
        }
        cachedCatalog = list
        return list
    }

    private fun packsConfig(): YamlConfiguration {
        val file = File(QinhRuins.instance.dataFolder, "packs.yml")
        return YamlConfiguration.loadConfiguration(file)
    }

    private fun installedVersions(): Map<String, Int> {
        val section = packsConfig().getConfigurationSection("installed") ?: return emptyMap()
        return section.getKeys(false).associateWith { section.getInt("$it.version", 0) }
    }

    private fun recordInstall(id: String, name: String, version: Int, files: List<String>) {
        val file = File(QinhRuins.instance.dataFolder, "packs.yml")
        val yml = YamlConfiguration.loadConfiguration(file)
        yml.set("installed.$id.name", name)
        yml.set("installed.$id.version", version)
        yml.set("installed.$id.installed-at", System.currentTimeMillis())
        yml.set("installed.$id.files", files)
        runCatching { yml.save(file) }
    }

    private fun isUrl(s: String): Boolean = s.startsWith("http://", true) || s.startsWith("https://", true)

    private fun runAsync(block: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(QinhRuins.instance, Runnable { block() })
    }

    private fun runSync(block: () -> Unit) {
        Bukkit.getScheduler().runTask(QinhRuins.instance, Runnable { block() })
    }
}
