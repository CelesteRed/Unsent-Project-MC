package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * {@code /unsentreload} — re-reads config.yml and whitelist.yml from disk and applies them live,
 * so edits (especially to the whitelist) take effect without restarting the server. Gated by
 * {@code unsent.admin}.
 */
public class ReloadCommand implements CommandExecutor {

    private final UnsentPlugin plugin;

    public ReloadCommand(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) {
            sender.sendMessage(Component.text("Unknown command.").color(NamedTextColor.RED));
            return true;
        }

        plugin.reloadConfig();
        plugin.getBlockWhitelist().reload();
        int blocks = plugin.getBlockWhitelist().getAllowed().size();

        sender.sendMessage(
            Component.text("UnsentPlugin reloaded — whitelist now allows ").color(NamedTextColor.GREEN)
                .append(Component.text(blocks).color(NamedTextColor.WHITE))
                .append(Component.text(" block(s).").color(NamedTextColor.GREEN))
        );
        return true;
    }
}
