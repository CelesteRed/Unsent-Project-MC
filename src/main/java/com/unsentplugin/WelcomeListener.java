package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

/**
 * Greets players on join with the MiniMessage lines configured under {@code welcome} in config.yml.
 * The command hint is clickable (suggests {@code /unsent } in the chat box).
 */
public class WelcomeListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final UnsentPlugin plugin;

    public WelcomeListener(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("welcome.enabled", true)) return;

        List<String> lines = plugin.getConfig().getStringList("welcome.message");
        if (lines.isEmpty()) return;

        Player player = event.getPlayer();
        player.sendMessage(Component.empty());
        for (String line : lines) {
            try {
                player.sendMessage(MM.deserialize(line));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid MiniMessage in welcome.message — skipping a line: " + e.getMessage());
            }
        }
    }
}
