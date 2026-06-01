package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code /unsentvip <name> <size> <color> <message>} — the colour/size variant for players with the
 * {@code unsent.color} permission. Font size must be 10–30 and the colour is a hex code; everything
 * else (validation, limits, moderation, creation) is shared with {@link UnsentCommand#send}.
 */
public class VipCommand implements CommandExecutor, TabCompleter {

    private static final int MIN_SIZE = 10;
    private static final int MAX_SIZE = 24;

    private final UnsentPlugin plugin;
    private final UnsentCommand unsentCommand;

    public VipCommand(UnsentPlugin plugin, UnsentCommand unsentCommand) {
        this.plugin = plugin;
        this.unsentCommand = unsentCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) {
            sender.sendMessage(Component.text("Unknown command.").color(NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can send unsent messages.");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(Component.text("Usage: /unsentvip <name> <size 10-24> <#hexcolor> <message>").color(NamedTextColor.RED));
            return true;
        }

        String name = args[0];

        int size;
        try {
            size = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Size must be a number between " + MIN_SIZE + " and " + MAX_SIZE + ".").color(NamedTextColor.RED));
            return true;
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            player.sendMessage(Component.text("Size must be between " + MIN_SIZE + " and " + MAX_SIZE + ".").color(NamedTextColor.RED));
            return true;
        }

        Color color = resolveColor(args[2]);
        if (color == null) {
            player.sendMessage(Component.text("\"" + args[2] + "\" isn't a valid colour. Use a hex like #FFAA00 or a name like red.").color(NamedTextColor.RED));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        unsentCommand.send(player, name, message, color, size);
        return true;
    }

    /** Resolves "default"/"white", a named colour from config (e.g. "red"), or a #RRGGBB hex. */
    private Color resolveColor(String input) {
        String s = input.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("default") || s.equalsIgnoreCase("white")) return Color.WHITE;

        // Named colour from the config `colors` section (case-insensitive).
        String configured = plugin.getConfig().getString("colors." + s.toLowerCase());
        if (configured != null) {
            Color named = parseHex(configured);
            if (named != null) return named;
            plugin.getLogger().warning("colors." + s.toLowerCase() + " = \"" + configured + "\" isn't a valid hex.");
        }
        return parseHex(s);
    }

    /** Parses "#RRGGBB"/"RRGGBB". Returns null if not a 6-digit hex. */
    static Color parseHex(String input) {
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (hex.matches("[0-9A-Fa-f]{6}")) {
            return new Color(Integer.parseInt(hex, 16));
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) return Collections.emptyList();
        if (args.length == 1) {
            return UnsentCommand.suggestNames(plugin, args[0]);
        }
        if (args.length == 2) {
            return List.of("10", "12", "14", "16", "20", "24").stream()
                    .filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 3) {
            List<String> options = new ArrayList<>();
            ConfigurationSection colors = plugin.getConfig().getConfigurationSection("colors");
            if (colors != null) options.addAll(colors.getKeys(false));
            options.add("#FFAA00");
            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .sorted().toList();
        }
        return Collections.emptyList();
    }
}
