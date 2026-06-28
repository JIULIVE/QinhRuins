package com.qinhuai.ruins.command

import com.qinhuai.corelib.command.cloud.QinhCloudComponents
import com.qinhuai.corelib.util.LocationUtils
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.QinhRuins
import com.qinhuai.ruins.data.AnchorStore
import com.qinhuai.ruins.data.SnapshotStore
import com.qinhuai.ruins.generation.AnchorManager
import com.qinhuai.ruins.generation.NaturalGenerator
import com.qinhuai.ruins.lang.Lang
import com.qinhuai.ruins.affix.AffixRegistry
import com.qinhuai.ruins.affix.Keystone
import com.qinhuai.ruins.affix.RerollService
import com.qinhuai.ruins.affix.TierConfig
import com.qinhuai.ruins.combat.AnchorActivation
import com.qinhuai.ruins.combat.ObjectiveTracker
import com.qinhuai.ruins.editor.EditorCommands
import com.qinhuai.ruins.editor.EditorMechCommands
import com.qinhuai.ruins.guide.GuideItem
import com.qinhuai.ruins.guide.GuideService
import com.qinhuai.ruins.loot.ChestRegistry
import com.qinhuai.ruins.loot.LootTableRegistry
import com.qinhuai.ruins.loot.RealmLoot
import com.qinhuai.ruins.mechanism.MechanismService
import com.qinhuai.ruins.realm.RealmManager
import com.qinhuai.ruins.generation.StagingService
import com.qinhuai.ruins.generation.StructureGenerator
import com.qinhuai.ruins.template.BlueprintRegistry
import com.qinhuai.ruins.template.TilePaletteRegistry
import com.qinhuai.ruins.session.BuiltinParties
import com.qinhuai.ruins.session.SessionManager
import com.qinhuai.ruins.structure.SchematicImporter
import com.qinhuai.ruins.structure.SelectionService
import com.qinhuai.ruins.structure.StructureSaver
import com.qinhuai.ruins.template.TemplateRegistry
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.incendo.cloud.parser.standard.StringParser
import java.io.File
import java.util.function.Supplier

object QinhRuinsCommands {

    fun register(manager: LegacyPaperCommandManager<CommandSender>) {
        val root = manager.commandBuilder("qr", "qruins")

        manager.command(root.handler { ctx -> sendHelp(ctx.sender()) })
        manager.command(root.literal("help").handler { ctx -> sendHelp(ctx.sender()) })
        manager.command(root.literal("version").permission("qinhruins.use").handler { ctx -> sendVersion(ctx.sender()) })
        manager.command(root.literal("reload").permission("qinhruins.admin").handler { ctx -> reload(ctx) })

        manager.command(
            root.literal("download").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("pack", Supplier { com.qinhuai.ruins.content.ContentService.catalogIds() }))
                .handler { ctx -> com.qinhuai.ruins.content.ContentService.download(ctx.sender(), ctx.get("pack")) }
        )
        manager.command(root.literal("catalog").permission("qinhruins.admin").handler { ctx -> com.qinhuai.ruins.content.ContentService.showCatalog(ctx.sender()) })
        manager.command(root.literal("packs").permission("qinhruins.admin").handler { ctx -> com.qinhuai.ruins.content.ContentService.showInstalled(ctx.sender()) })

        manager.command(root.literal("pos1").permission("qinhruins.admin").handler { ctx -> setPos(ctx, 1) })
        manager.command(root.literal("pos2").permission("qinhruins.admin").handler { ctx -> setPos(ctx, 2) })

        manager.command(
            root.literal("save").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("id", Supplier { TemplateRegistry.ids() }))
                .handler { ctx -> save(ctx) }
        )
        manager.command(
            root.literal("spawn").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("template", Supplier { TemplateRegistry.ids() }))
                .handler { ctx -> spawn(ctx) }
        )
        manager.command(root.literal("gentest").permission("qinhruins.admin").handler { ctx -> genTest(ctx) })
        manager.command(
            root.literal("why").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("template", Supplier { TemplateRegistry.ids() }))
                .handler { ctx -> whyHere(ctx) }
        )
        manager.command(
            root.literal("scatter").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("radius"))
                .required(QinhCloudComponents.requiredString("count"))
                .handler { ctx -> scatter(ctx) }
        )
        val stage = root.literal("stage").permission("qinhruins.admin")
        manager.command(
            stage.literal("start")
                .required(QinhCloudComponents.requiredString("template", Supplier { TemplateRegistry.ids() }))
                .handler { ctx -> stageStart(ctx) }
        )
        manager.command(stage.literal("confirm").handler { ctx -> stageAct(ctx) { StagingService.confirm(it) } })
        manager.command(stage.literal("cancel").handler { ctx -> stageAct(ctx) { StagingService.cancel(it) } })
        manager.command(stage.literal("rotate").handler { ctx -> stageAct(ctx) { StagingService.rotate(it) } })
        manager.command(stage.literal("here").handler { ctx -> stageAct(ctx) { StagingService.toHere(it) } })
        manager.command(
            stage.literal("move")
                .required(QinhCloudComponents.requiredString("dx"))
                .required(QinhCloudComponents.requiredString("dy"))
                .required(QinhCloudComponents.requiredString("dz"))
                .handler { ctx -> stageMove(ctx) }
        )
        manager.command(
            root.literal("import").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("file", Supplier { schematicFiles() }))
                .required(QinhCloudComponents.requiredString("id"))
                .handler { ctx -> importSchem(ctx) }
        )
        manager.command(
            root.literal("tp").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("anchor", Supplier { AnchorManager.ids() }))
                .handler { ctx -> tp(ctx) }
        )
        manager.command(
            root.literal("remove").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("anchor", Supplier { AnchorManager.ids() }))
                .handler { ctx -> remove(ctx) }
        )
        manager.command(
            root.literal("markers").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("anchor", Supplier { AnchorManager.ids() }))
                .handler { ctx -> markers(ctx) }
        )

        manager.command(root.literal("guide").literal("cancel").permission("qinhruins.use").handler { ctx -> guideCancel(ctx) })
        manager.command(
            root.literal("guide").literal("clear").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("player", Supplier { Bukkit.getOnlinePlayers().map { it.name } }))
                .handler { ctx -> guideClear(ctx) }
        )

        manager.command(
            root.literal("keystone").literal("give").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("tier", Supplier { (1..TierConfig.maxTier).map { it.toString() } }))
                .optional(QinhCloudComponents.optionalString("player", Supplier { Bukkit.getOnlinePlayers().map { it.name } }))
                .handler { ctx -> giveKeystone(ctx) }
        )

        manager.command(
            root.literal("genstruct").permission("qinhruins.admin")
                .required(QinhCloudComponents.requiredString("palette", Supplier { TilePaletteRegistry.ids() }))
                .optional(QinhCloudComponents.optionalString("mode", Supplier { listOf("dry") }))
                .handler { ctx -> genStruct(ctx) }
        )
        manager.command(root.literal("activate").permission("qinhruins.use").handler { ctx -> activate(ctx) })
        manager.command(root.literal("codex").permission("qinhruins.use").handler { ctx -> codex(ctx) })
        manager.command(root.literal("reroll").permission("qinhruins.use").handler { ctx -> reroll(ctx) })
        manager.command(root.literal("reward").permission("qinhruins.use").handler { ctx -> reward(ctx) })

        manager.command(root.literal("party").literal("create").permission("qinhruins.use").handler { ctx -> partyCreate(ctx) })
        manager.command(
            root.literal("party").literal("invite").permission("qinhruins.use")
                .required(QinhCloudComponents.requiredString("player", Supplier { Bukkit.getOnlinePlayers().map { it.name } }))
                .handler { ctx -> partyInvite(ctx) }
        )
        manager.command(root.literal("party").literal("accept").permission("qinhruins.use").handler { ctx -> partyAccept(ctx) })
        manager.command(root.literal("party").literal("leave").permission("qinhruins.use").handler { ctx -> partyLeave(ctx) })
        manager.command(root.literal("party").literal("info").permission("qinhruins.use").handler { ctx -> partyInfo(ctx) })

        val editor = root.literal("editor").permission("qinhruins.admin")
        manager.command(
            editor.literal("start")
                .required(QinhCloudComponents.requiredString("template", Supplier { TemplateRegistry.ids() }))
                .handler { ctx -> editorAct(ctx) { p -> EditorCommands.start(p, str(ctx, "template")) } }
        )
        manager.command(
            editor.literal("mode")
                .required(QinhCloudComponents.requiredString("mode", Supplier { listOf("spawn", "chest", "core") }))
                .handler { ctx -> editorAct(ctx) { p -> EditorCommands.mode(p, str(ctx, "mode")) } }
        )
        manager.command(
            editor.literal("set")
                .required(QinhCloudComponents.requiredString("field", Supplier { listOf("mob", "count", "stage", "loot", "unlock") }))
                .required(QinhCloudComponents.requiredString("value"))
                .handler { ctx -> editorAct(ctx) { p -> EditorCommands.set(p, str(ctx, "field"), str(ctx, "value")) } }
        )
        manager.command(
            editor.literal("mech")
                .required("args", StringParser.greedyStringParser())
                .handler { ctx -> editorAct(ctx) { p -> EditorMechCommands.handle(p, ctx.get("args")) } }
        )
        manager.command(editor.literal("list").handler { ctx -> editorAct(ctx) { p -> EditorCommands.list(p) } })
        manager.command(
            editor.literal("remove").required(QinhCloudComponents.requiredString("id"))
                .handler { ctx -> editorAct(ctx) { p -> EditorCommands.remove(p, str(ctx, "id")) } }
        )
        manager.command(editor.literal("save").handler { ctx -> editorAct(ctx) { p -> EditorCommands.save(p) } })
        manager.command(editor.literal("cancel").handler { ctx -> editorAct(ctx) { p -> EditorCommands.cancel(p) } })

        manager.command(root.literal("list").permission("qinhruins.use").handler { ctx -> list(ctx.sender()) })
        manager.command(
            root.literal("near").permission("qinhruins.use")
                .optional(QinhCloudComponents.optionalString("radius"))
                .handler { ctx -> near(ctx) }
        )
        manager.command(
            root.literal("info").permission("qinhruins.admin")
                .optional(QinhCloudComponents.optionalString("anchor", Supplier { AnchorManager.ids() }))
                .handler { ctx -> info(ctx) }
        )
        manager.command(
            root.literal("profile").permission("qinhruins.admin")
                .optional(QinhCloudComponents.optionalString("action", Supplier { listOf("reset") }))
                .handler { ctx -> profile(ctx) }
        )
    }

    private fun sendHelp(sender: CommandSender) {
        listOf(
            Lang.get("cmd.help-header", "version" to QinhRuins.VERSION),
            Lang.get("cmd.help-help"),
            Lang.get("cmd.help-list"),
            Lang.get("cmd.help-near"),
            Lang.get("cmd.help-activate"),
            Lang.get("cmd.help-reroll"),
            Lang.get("cmd.help-reward"),
            Lang.get("cmd.help-party"),
            Lang.get("cmd.help-admin-sep"),
            Lang.get("cmd.help-pos"),
            Lang.get("cmd.help-save"),
            Lang.get("cmd.help-import"),
            Lang.get("cmd.help-spawn"),
            Lang.get("cmd.help-why"),
            Lang.get("cmd.help-stage-start"),
            Lang.get("cmd.help-stage"),
            Lang.get("cmd.help-genstruct"),
            Lang.get("cmd.help-editor"),
            Lang.get("cmd.help-markers"),
            Lang.get("cmd.help-markers-sign"),
            Lang.get("cmd.help-markers-types"),
            Lang.get("cmd.help-markers-placeholder"),
            Lang.get("cmd.help-guide-cancel"),
            Lang.get("cmd.help-guide-clear"),
            Lang.get("cmd.help-guide-source"),
            Lang.get("cmd.help-tp"),
            Lang.get("cmd.help-remove"),
            Lang.get("cmd.help-info"),
            Lang.get("cmd.help-profile"),
            Lang.get("cmd.help-reload"),
            Lang.get("cmd.help-catalog"),
            Lang.get("cmd.help-download"),
            Lang.get("cmd.help-packs"),
        ).forEach { TextUtil.sendColored(sender, it) }
    }

    private fun sendVersion(sender: CommandSender) {
        Lang.send(sender, "cmd.version", "version" to QinhRuins.VERSION)
    }

    private fun reload(ctx: CommandContext<CommandSender>) {
        val count = reloadAll()
        Lang.send(ctx.sender(), "cmd.reload-done", "count" to count, "anchors" to AnchorManager.all().size)
    }

    fun reloadAll(): Int {
        val plugin = QinhRuins.instance
        plugin.reloadConfig()
        com.qinhuai.ruins.lang.Lang.load()
        com.qinhuai.ruins.template.GenerationProfiles.load(File(plugin.dataFolder, "generators"), plugin.logger)
        val count = TemplateRegistry.load(File(plugin.dataFolder, "templates"), plugin.logger)
        com.qinhuai.ruins.generation.AnchorPlacer.clearCache()
        com.qinhuai.ruins.structure.PasteEngines.active().clearCache()
        BlueprintRegistry.load(File(plugin.dataFolder, "templates"))
        TilePaletteRegistry.load(File(plugin.dataFolder, "palettes"), plugin.logger)
        LootTableRegistry.load(File(plugin.dataFolder, "loottables"))
        RealmLoot.load(plugin.config.getConfigurationSection("realm.reward"))
        AffixRegistry.load(File(plugin.dataFolder, "affixes.yml"))
        TierConfig.load(plugin.config.getConfigurationSection("realm.tiers"))
        Keystone.load(plugin.config.getConfigurationSection("realm.keystone"))
        RerollService.load(plugin.config.getConfigurationSection("realm.reroll"))
        AnchorManager.loadAll(AnchorStore.loadAll())
        ChestRegistry.rebuild(AnchorManager.all())
        com.qinhuai.ruins.realm.CoreRegistry.rebuild(AnchorManager.all())
        MechanismService.rebuild(AnchorManager.all())
        AnchorActivation.start(plugin, plugin.config)
        NaturalGenerator.start(
            plugin,
            plugin.config.getBoolean("generation.enabled", true),
            plugin.config.getInt("generation.max-per-tick", 4),
            plugin.config.getLong("generation.period-ticks", 5L),
        )
        com.qinhuai.ruins.generation.GenerationDirector.start(plugin, plugin.config.getConfigurationSection("generation.director"))
        GuideService.start(plugin, plugin.config)
        SessionManager.start(plugin, plugin.config)
        RealmManager.start(plugin, plugin.config)
        MechanismService.start(plugin, plugin.config)
        return count
    }

    private fun setPos(ctx: CommandContext<CommandSender>, which: Int) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val target = player.getTargetBlockExact(100)
        if (target == null) {
            Lang.send(player, "cmd.pos-look-block", "which" to which)
            return
        }
        val loc = target.location
        if (which == 1) SelectionService.setPos1(player.uniqueId, loc) else SelectionService.setPos2(player.uniqueId, loc)
        Lang.send(player, "cmd.pos-set", "which" to which, "x" to loc.blockX, "y" to loc.blockY, "z" to loc.blockZ)
    }

    private fun save(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val id = runCatching { ctx.get<String>("id") }.getOrNull().orEmpty()
        if (id.isBlank()) return Lang.send(player, "cmd.save-need-id")
        val message = StructureSaver.save(player, id)
        val plugin = QinhRuins.instance
        TemplateRegistry.load(File(plugin.dataFolder, "templates"), plugin.logger)
        BlueprintRegistry.load(File(plugin.dataFolder, "templates"))
        ChestRegistry.rebuild(AnchorManager.all())
        com.qinhuai.ruins.realm.CoreRegistry.rebuild(AnchorManager.all())
        MechanismService.rebuild(AnchorManager.all())
        TextUtil.sendColored(player, message)
    }

    private fun spawn(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val templateId = runCatching { ctx.get<String>("template") }.getOrNull().orEmpty()
        val template = TemplateRegistry.get(templateId)
            ?: return Lang.send(player, "cmd.template-not-found", "id" to templateId)
        val outcome = AnchorManager.spawn(template, player.location.block.location)
        if (outcome.anchor == null) {
            Lang.send(player, "cmd.spawn-failed", "reason" to outcome.result.message)
            return
        }
        ChestRegistry.rebuild(AnchorManager.all())
        MechanismService.rebuild(AnchorManager.all())
        com.qinhuai.ruins.realm.CoreRegistry.rebuild(AnchorManager.all())
        Lang.send(player, "cmd.spawn-done", "name" to template.display, "id" to outcome.anchor.id)
    }

    private fun genTest(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        Lang.send(player, "cmd.gentest-running")
        val summary = com.qinhuai.ruins.generation.GenerationDirector.debugSpawnNear(player, 25)
        ChestRegistry.rebuild(AnchorManager.all())
        MechanismService.rebuild(AnchorManager.all())
        com.qinhuai.ruins.realm.CoreRegistry.rebuild(AnchorManager.all())
        TextUtil.sendColored(player, summary)
    }

    private fun whyHere(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val templateId = str(ctx, "template")
        val template = TemplateRegistry.get(templateId)
            ?: return Lang.send(player, "cmd.template-not-found", "id" to templateId)
        val loc = player.location
        val world = loc.world ?: return Lang.send(player, "cmd.why-no-world")
        val lines = com.qinhuai.ruins.generation.AnchorPlacer.diagnose(world, loc.blockX, loc.blockZ, template)
        Lang.send(player, "cmd.why-header", "name" to template.display, "world" to world.name, "x" to loc.blockX, "z" to loc.blockZ)
        lines.forEach { l ->
            val mark = if (l.ok) "§a✔" else "§c✘"
            TextUtil.sendColored(player, "$mark §f${l.label} §8- §7${l.detail}")
        }
        val fails = lines.count { !it.ok }
        if (fails == 0) {
            Lang.send(player, "cmd.why-all-pass")
        } else {
            Lang.send(player, "cmd.why-fails", "fails" to fails)
        }
    }

    private fun codex(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        com.qinhuai.ruins.guide.CodexGui.open(player)
    }

    private fun scatter(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val radius = runCatching { ctx.get<String>("radius") }.getOrNull()?.toIntOrNull()?.coerceIn(100, 20000)
            ?: return Lang.send(player, "cmd.scatter-bad-radius")
        val count = runCatching { ctx.get<String>("count") }.getOrNull()?.toIntOrNull()?.coerceIn(1, 200)
            ?: return Lang.send(player, "cmd.scatter-bad-count")
        Lang.send(player, "cmd.scatter-start", "radius" to radius, "count" to count)
        com.qinhuai.ruins.generation.GenerationDirector.scatter(QinhRuins.instance, player, radius, count)
    }

    private fun importSchem(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val fileName = str(ctx, "file")
        val id = str(ctx, "id")
        if (id.isBlank()) return Lang.send(player, "cmd.import-need-id")
        val plugin = QinhRuins.instance
        val schemDir = File(plugin.dataFolder, "schematics")
        val schemFile = File(schemDir, if (fileName.endsWith(".schem", true)) fileName else "$fileName.schem")
        val templatesDir = File(plugin.dataFolder, "templates")
        Lang.send(player, "cmd.import-start", "id" to id)
        SchematicImporter.import(plugin, player, schemFile, templatesDir, id) { message ->
            TextUtil.sendColored(player, message)
            TemplateRegistry.load(templatesDir, plugin.logger)
            BlueprintRegistry.load(templatesDir)
        }
    }

    private fun schematicFiles(): List<String> {
        val dir = File(QinhRuins.instance.dataFolder, "schematics")
        return dir.listFiles { f -> f.extension.equals("schem", true) }?.map { it.name } ?: emptyList()
    }

    private fun stageStart(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val templateId = str(ctx, "template")
        val template = TemplateRegistry.get(templateId)
            ?: return Lang.send(player, "cmd.template-not-found", "id" to templateId)
        StagingService.stage(player, template)
    }

    private fun stageMove(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val dx = str(ctx, "dx").toIntOrNull() ?: 0
        val dy = str(ctx, "dy").toIntOrNull() ?: 0
        val dz = str(ctx, "dz").toIntOrNull() ?: 0
        StagingService.move(player, dx, dy, dz)
    }

    private fun stageAct(ctx: CommandContext<CommandSender>, block: (Player) -> Unit) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        block(player)
    }

    private fun tp(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val id = runCatching { ctx.get<String>("anchor") }.getOrNull().orEmpty()
        val anchor = AnchorManager.get(id) ?: return Lang.send(player, "cmd.anchor-not-found", "id" to id)
        player.teleport(anchor.location)
        Lang.send(player, "cmd.tp-done", "id" to id)
    }

    private fun remove(ctx: CommandContext<CommandSender>) {
        val id = runCatching { ctx.get<String>("anchor") }.getOrNull().orEmpty()
        val result = com.qinhuai.ruins.generation.AnchorRecycler.recycle(id)
        if (!result.found) {
            Lang.send(ctx.sender(), "cmd.anchor-not-found", "id" to id)
            return
        }
        val tail = if (result.restored) Lang.get("cmd.remove-restored") else Lang.get("cmd.remove-no-snapshot")
        Lang.send(ctx.sender(), "cmd.remove-done", "id" to id, "tail" to tail)
    }

    private fun markers(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val id = str(ctx, "anchor")
        val anchor = AnchorManager.get(id) ?: return Lang.send(player, "cmd.anchor-not-found", "id" to id)
        val template = TemplateRegistry.get(anchor.templateId)
            ?: return Lang.send(player, "cmd.markers-template-missing", "id" to anchor.templateId)
        if (anchor.width <= 0 || anchor.depth <= 0) {
            return Lang.send(player, "cmd.markers-no-size", "id" to anchor.templateId)
        }
        val loc = anchor.location
        val world = loc.world ?: return Lang.send(player, "cmd.markers-no-world")
        val scan = com.qinhuai.ruins.structure.MarkerScanner.scan(
            world, loc.blockX, loc.blockY, loc.blockZ, anchor.width, anchor.height.coerceAtLeast(1), anchor.depth,
        )
        if (scan.isEmpty()) {
            return Lang.send(player, "cmd.markers-none")
        }
        com.qinhuai.ruins.structure.MarkerScanner.applyMarkerBlocks(scan)
        val plugin = QinhRuins.instance
        val templatesDir = File(plugin.dataFolder, "templates")
        com.qinhuai.ruins.structure.MarkerScanner.writeBlueprint(File(templatesDir, "${anchor.templateId}/blueprint.yml"), scan)
        val structFile = File(templatesDir, "${anchor.templateId}/${template.structureFile}")
        val size = org.bukkit.util.BlockVector(anchor.width, anchor.height.coerceAtLeast(1), anchor.depth)
        com.qinhuai.ruins.structure.PasteEngines.active().capture(structFile, loc, size, false)
        BlueprintRegistry.load(templatesDir)
        ChestRegistry.rebuild(AnchorManager.all())
        com.qinhuai.ruins.realm.CoreRegistry.rebuild(AnchorManager.all())
        MechanismService.rebuild(AnchorManager.all())
        Lang.send(
            player,
            "cmd.markers-done",
            "id" to anchor.templateId,
            "spawns" to scan.spawnPoints.size,
            "chests" to scan.lootChests.size,
            "cores" to scan.cores.size,
            "commands" to scan.commands.size,
        )
    }

    private fun giveKeystone(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender()
        val tier = str(ctx, "tier").toIntOrNull()?.coerceIn(1, TierConfig.maxTier)
            ?: return Lang.send(sender, "cmd.keystone-bad-tier", "max" to TierConfig.maxTier)
        val targetName = runCatching { ctx.get<String>("player") }.getOrNull()?.takeIf { it.isNotBlank() }
        val target = if (targetName != null) Bukkit.getPlayerExact(targetName) else sender as? Player
        if (target == null) {
            Lang.send(sender, "cmd.keystone-no-target")
            return
        }
        target.inventory.addItem(Keystone.build(tier, target))
        Lang.send(sender, "cmd.keystone-given", "name" to target.name, "tier" to tier)
    }

    private fun activate(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        RealmManager.tryActivate(player)
    }

    private fun genStruct(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val paletteId = str(ctx, "palette")
        val palette = TilePaletteRegistry.get(paletteId)
            ?: return Lang.send(player, "cmd.genstruct-no-palette", "id" to paletteId)
        val mode = str(ctx, "mode")
        val dry = mode.equals("dry", true)
        val seed = mode.toLongOrNull() ?: System.nanoTime()

        if (dry) {
            val plan = StructureGenerator.plan(palette, System.nanoTime())
            Lang.send(player, "cmd.genstruct-dry-header", "id" to paletteId, "size" to plan.size)
            plan.forEach {
                Lang.send(player, "cmd.genstruct-dry-tile", "tile" to it.def.templateId, "x" to it.offset.x, "y" to it.offset.y, "z" to it.offset.z, "sx" to it.def.size.x, "sy" to it.def.size.y, "sz" to it.def.size.z)
            }
            return
        }

        val result = StructureGenerator.generate(palette, player.location.block.location, seed)
        Lang.send(player, "cmd.genstruct-done", "id" to paletteId, "pasted" to result.pasted, "planned" to result.planned, "seed" to seed)
    }

    private fun reroll(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        RerollService.reroll(player)
    }

    private fun reward(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        com.qinhuai.ruins.loot.SlotMachine.openNearest(player)
    }

    private fun partyCreate(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        TextUtil.sendColored(player, BuiltinParties.create(player.uniqueId))
    }

    private fun partyInvite(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val targetName = runCatching { ctx.get<String>("player") }.getOrNull().orEmpty()
        val target = Bukkit.getPlayerExact(targetName)
            ?: return Lang.send(player, "cmd.party-no-player", "name" to targetName)
        TextUtil.sendColored(player, BuiltinParties.invite(player.uniqueId, target.uniqueId))
        Lang.send(target, "cmd.party-invited", "name" to player.name)
    }

    private fun partyAccept(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        TextUtil.sendColored(player, BuiltinParties.accept(player.uniqueId))
    }

    private fun partyLeave(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        TextUtil.sendColored(player, BuiltinParties.leave(player.uniqueId))
    }

    private fun partyInfo(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val names = BuiltinParties.partyMembers(player.uniqueId)
            .mapNotNull { Bukkit.getOfflinePlayer(it).name }
        Lang.send(player, "cmd.party-members", "count" to names.size, "names" to names.joinToString(", "))
    }

    private fun guideCancel(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        if (!com.qinhuai.ruins.guide.GuideService.isGuiding(player.uniqueId)) {
            Lang.send(player, "cmd.guide-none")
            return
        }
        com.qinhuai.ruins.guide.GuideService.stopGuide(player.uniqueId, returnItem = true)
    }

    private fun guideClear(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender()
        val name = runCatching { ctx.get<String>("player") }.getOrNull().orEmpty()
        val uuid = Bukkit.getPlayerExact(name)?.uniqueId ?: Bukkit.getOfflinePlayer(name).uniqueId
        if (com.qinhuai.ruins.guide.GuideService.adminClear(uuid)) {
            Lang.send(sender, "cmd.guide-cleared", "name" to name)
        } else {
            Lang.send(sender, "cmd.guide-clear-none", "name" to name)
        }
    }

    private fun list(sender: CommandSender) {
        Lang.send(sender, "cmd.list-templates-header", "count" to TemplateRegistry.all().size)
        TemplateRegistry.all().forEach { t ->
            val gen = if (t.generation.enabled) Lang.get("cmd.list-gen-auto", "mode" to t.generation.yMode) else Lang.get("cmd.list-gen-manual")
            TextUtil.sendColored(sender, "§e${t.id} §7- ${t.display} §8[$gen§8]")
        }
        Lang.send(sender, "cmd.list-anchors-header", "count" to AnchorManager.all().size)
        AnchorManager.all().forEach { a ->
            val loc = a.location
            TextUtil.sendColored(sender, "§e${a.id} §7@ ${loc.world?.name} ${loc.blockX},${loc.blockY},${loc.blockZ} §8[${a.state.name}]")
        }
    }

    private fun near(ctx: CommandContext<CommandSender>) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        val radius = runCatching { ctx.get<String>("radius") }.getOrNull()?.toDoubleOrNull() ?: 200.0
        val found = AnchorManager.nearby(player.location, radius)
        if (found.isEmpty()) {
            Lang.send(player, "cmd.near-none", "radius" to radius.toInt())
            return
        }
        Lang.send(player, "cmd.near-header", "count" to found.size)
        found.forEach { a ->
            val dist = LocationUtils.distance(a.location, player.location).toInt()
            val name = TemplateRegistry.get(a.templateId)?.display ?: a.templateId
            TextUtil.sendColored(player, "§e$name §7${dist}m §8(${a.id})")
        }
    }

    private fun info(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender()
        val id = str(ctx, "anchor")
        if (id.isBlank()) {
            Lang.send(sender, "cmd.info-status-header")
            Lang.send(
                sender,
                "cmd.info-status-line",
                "templates" to TemplateRegistry.all().size,
                "anchors" to AnchorManager.all().size,
                "sessions" to SessionManager.activeCount(),
                "tracked" to ObjectiveTracker.activeCount(),
            )
            return
        }
        val anchor = AnchorManager.get(id) ?: return Lang.send(sender, "cmd.anchor-not-found", "id" to id)
        val loc = anchor.location
        val template = TemplateRegistry.get(anchor.templateId)
        val blueprint = BlueprintRegistry.get(anchor.templateId)
        Lang.send(sender, "cmd.info-anchor-header", "id" to anchor.id)
        Lang.send(sender, "cmd.info-template", "name" to (template?.display ?: anchor.templateId))
        Lang.send(sender, "cmd.info-location", "world" to loc.world?.name, "x" to loc.blockX, "y" to loc.blockY, "z" to loc.blockZ, "state" to anchor.state.name)
        Lang.send(sender, "cmd.info-session", "status" to (if (SessionManager.isAnchorBusy(id)) Lang.get("cmd.info-session-busy") else Lang.get("cmd.info-session-free")))
        if (blueprint != null) {
            Lang.send(sender, "cmd.info-blueprint", "spawns" to blueprint.spawnPoints.size, "chests" to blueprint.lootChests.size, "stages" to blueprint.objectives.maxStage)
        }
        ObjectiveTracker.progressFor(id)?.let { progress ->
            val stageName = progress.objectives.stage(progress.currentStage)?.name ?: ""
            val done = if (progress.completed) Lang.get("cmd.info-progress-done") else ""
            Lang.send(sender, "cmd.info-progress", "cur" to progress.currentStage, "max" to progress.objectives.maxStage, "stage" to stageName, "done" to done)
        }
    }

    private fun profile(ctx: CommandContext<CommandSender>) {
        val sender = ctx.sender()
        if (str(ctx, "action").equals("reset", true)) {
            com.qinhuai.ruins.util.Profiler.reset()
            Lang.send(sender, "cmd.profile-reset")
            return
        }
        if (com.qinhuai.ruins.util.Profiler.isEmpty()) {
            Lang.send(sender, "cmd.profile-empty")
            return
        }
        Lang.send(sender, "cmd.profile-header")
        for (line in com.qinhuai.ruins.util.Profiler.summary()) {
            Lang.send(
                sender,
                "cmd.profile-line",
                "name" to line.name,
                "count" to line.count,
                "avg" to String.format("%.2f", line.avgMs),
                "max" to String.format("%.2f", line.maxMs),
                "total" to String.format("%.1f", line.totalMs),
            )
        }
        Lang.send(sender, "cmd.profile-hint")
    }

    private fun editorAct(ctx: CommandContext<CommandSender>, block: (Player) -> Unit) {
        val player = ctx.sender() as? Player ?: return notPlayer(ctx.sender())
        block(player)
    }

    private fun str(ctx: CommandContext<CommandSender>, name: String): String =
        runCatching { ctx.get<String>(name) }.getOrNull().orEmpty()

    private fun notPlayer(sender: CommandSender) {
        Lang.send(sender, "cmd.not-player")
    }
}
