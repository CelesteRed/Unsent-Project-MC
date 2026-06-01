package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin hub command: {@code /unsentadmin <subcommand>}.
 *
 * <p>Currently handles whitelist management:
 * <ul>
 *   <li>{@code /unsentadmin whitelist add [block|hand]} — add a block (or the held block) </li>
 *   <li>{@code /unsentadmin whitelist remove [block|hand]} — remove one</li>
 *   <li>{@code /unsentadmin whitelist list} — show the current whitelist</li>
 *   <li>{@code /unsentadmin whitelist reload} — re-read whitelist.yml from disk</li>
 * </ul>
 * Gated by {@code unsent.admin} via plugin.yml.
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final UnsentPlugin plugin;

    public AdminCommand(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) {
            sender.sendMessage(Component.text("Unknown command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("whitelist")) {
            sender.sendMessage(Component.text("Usage: /unsentadmin whitelist <add|remove|list|reload> [block|hand]").color(NamedTextColor.RED));
            return true;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "list"   -> doList(sender);
            case "reload" -> {
                plugin.getBlockWhitelist().reload();
                sender.sendMessage(Component.text("Reloaded whitelist.yml ("
                        + plugin.getBlockWhitelist().getAllowed().size() + " blocks).").color(NamedTextColor.GREEN));
            }
            case "add", "remove" -> doAddRemove(sender, sub, args);
            default -> sender.sendMessage(Component.text("Usage: /unsentadmin whitelist <add|remove|list|reload> [block|hand]").color(NamedTextColor.RED));
        }
        return true;
    }

    private void doAddRemove(CommandSender sender, String sub, String[] args) {
        // Default to the held block ("hand") when no block is given.
        String token = args.length >= 3 ? args[2] : "hand";
        Material material = resolveMaterial(sender, token);
        if (material == null) return; // resolveMaterial already messaged the sender

        BlockWhitelist whitelist = plugin.getBlockWhitelist();
        if (sub.equals("add")) {
            if (whitelist.add(material)) {
                sender.sendMessage(Component.text("Added ").color(NamedTextColor.GREEN)
                        .append(Component.text(material.name()).color(NamedTextColor.WHITE))
                        .append(Component.text(" to the whitelist.").color(NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text(material.name() + " is already whitelisted.").color(NamedTextColor.YELLOW));
            }
        } else {
            if (whitelist.remove(material)) {
                sender.sendMessage(Component.text("Removed ").color(NamedTextColor.GREEN)
                        .append(Component.text(material.name()).color(NamedTextColor.WHITE))
                        .append(Component.text(" from the whitelist.").color(NamedTextColor.GREEN)));
            } else {
                sender.sendMessage(Component.text(material.name() + " wasn't in the whitelist.").color(NamedTextColor.YELLOW));
            }
        }
    }

    private void doList(CommandSender sender) {
        List<Material> blocks = plugin.getBlockWhitelist().getAllowed();
        if (blocks.isEmpty()) {
            sender.sendMessage(Component.text("The whitelist is empty — no block accepts a map.").color(NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text("Whitelisted blocks (" + blocks.size() + "):").color(NamedTextColor.GRAY));
        String joined = blocks.stream().map(Material::name).collect(Collectors.joining(", "));
        sender.sendMessage(Component.text(joined).color(NamedTextColor.WHITE));
    }

    /** Resolves a token to a block material: "hand" → the player's held block, else a material name. */
    private Material resolveMaterial(CommandSender sender, String token) {
        if (token.equalsIgnoreCase("hand")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use 'hand' — name a block instead.").color(NamedTextColor.RED));
                return null;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir() || !held.getType().isBlock()) {
                player.sendMessage(Component.text("You're not holding a placeable block.").color(NamedTextColor.RED));
                return null;
            }
            return held.getType();
        }

        Material material = Material.matchMaterial(token);
        if (material == null || !material.isBlock()) {
            sender.sendMessage(Component.text("\"" + token + "\" isn't a known block.").color(NamedTextColor.RED));
            return null;
        }
        return material;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) return Collections.emptyList();

        if (args.length == 1) {
            return filter(List.of("whitelist"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            return filter(List.of("add", "remove", "list", "reload"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("add")) {
                List<String> options = new ArrayList<>();
                options.add("hand");
                for (Material m : Material.values()) {
                    if (m.isBlock() && !m.isLegacy()) options.add(m.name());
                }
                return filter(options, args[2]);
            }
            if (sub.equals("remove")) {
                List<String> options = new ArrayList<>();
                options.add("hand");
                for (Material m : plugin.getBlockWhitelist().getAllowed()) options.add(m.name());
                return filter(options, args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .sorted()
                .limit(100)
                .toList();
    }
}
