package com.qinhuai.ruins.editor

import com.qinhuai.corelib.scheduler.TaskScheduler
import com.qinhuai.corelib.util.TextUtil
import com.qinhuai.ruins.lang.Lang
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

object ChatInputService : Listener {

    private val pending = HashMap<UUID, (String) -> Unit>()
    private val plain = PlainTextComponentSerializer.plainText()

    fun await(player: Player, prompt: String, callback: (String) -> Unit) {
        pending[player.uniqueId] = callback
        TextUtil.sendColored(player, prompt)
        Lang.send(player, "editor.chat-input-hint")
    }

    fun cancel(player: Player) {
        pending.remove(player.uniqueId)
    }

    fun isWaiting(player: Player): Boolean = pending.containsKey(player.uniqueId)

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val callback = pending.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val message = plain.serialize(event.message()).trim()
        TaskScheduler.runSync {
            if (message.equals(Lang.get("editor.chat-cancel-keyword"), true) || message.equals("cancel", true)) {
                Lang.send(event.player, "editor.chat-input-cancelled")
            } else {
                callback(message)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pending.remove(event.player.uniqueId)
    }
}
