package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UnsentCommand implements CommandExecutor, TabCompleter {

    private final UnsentPlugin plugin;

    public UnsentCommand(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Block namespaced calls like /unsentplugin:unsent
        if (label.contains(":")) {
            sender.sendMessage(Component.text("Unknown command.").color(NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can send unsent messages.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /unsent <name> <message>").color(NamedTextColor.RED));
            return true;
        }

        String name = args[0];
        WordFilter filter = plugin.getWordFilter();

        if (!filter.isValidName(name)) {
            player.sendMessage(Component.text("That name isn't valid. Use letters, numbers, or underscores (max 16 chars).").color(NamedTextColor.RED));
            return true;
        }

        if (filter.isBlocked(name)) {
            player.sendMessage(Component.text("That name contains blocked content.").color(NamedTextColor.RED));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        int maxLen = plugin.getConfig().getInt("max-message-length", 80);
        if (message.length() > maxLen) {
            player.sendMessage(Component.text("Your message is too long (max " + maxLen + " characters).").color(NamedTextColor.RED));
            return true;
        }

        if (filter.isBlocked(message)) {
            player.sendMessage(Component.text("Your message contains content that isn't allowed. Please keep it kind.").color(NamedTextColor.RED));
            return true;
        }

        boolean saved = plugin.getMessageStore().save(name, message);
        if (!saved) {
            player.sendMessage(Component.text("There are already too many messages for that name.").color(NamedTextColor.YELLOW));
            return true;
        }

        ItemStack map = MapFactory.createMap(player.getWorld(), name, message);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), map);
            player.sendMessage(Component.text("Your inventory was full — your map dropped at your feet.").color(NamedTextColor.YELLOW));
        } else {
            player.getInventory().addItem(map);
        }

        player.sendMessage(
            Component.text("Your message to ")
                     .color(NamedTextColor.GRAY)
                .append(Component.text(name).color(NamedTextColor.WHITE))
                .append(Component.text(" has been sealed into a map.").color(NamedTextColor.GRAY))
        );

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase();
            return plugin.getMessageStore().allNames().stream()
                    .filter(n -> n.startsWith(typed))
                    .toList();
        }
        return Collections.emptyList();
    }
}
