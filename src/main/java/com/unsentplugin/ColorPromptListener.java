package com.unsentplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * While a player is being asked for a background colour (see {@link UnsentCommand}), their next
 * chat message is captured as the colour instead of being broadcast. Runs on the async chat event,
 * then hops to the main thread to build the note.
 */
public class ColorPromptListener implements Listener {

    private final UnsentPlugin plugin;
    private final UnsentCommand command;

    public ColorPromptListener(UnsentPlugin plugin, UnsentCommand command) {
        this.plugin = plugin;
        this.command = command;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!command.hasPendingColor(player.getUniqueId())) return;

        event.setCancelled(true); // don't broadcast the colour to everyone
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> command.submitColor(player, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        command.clearPendingColor(event.getPlayer().getUniqueId());
    }
}
