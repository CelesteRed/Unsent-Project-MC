package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class ReadCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final UnsentPlugin plugin;

    public ReadCommand(UnsentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Block namespaced calls
        if (label.contains(":")) {
            sender.sendMessage(Component.text("Unknown command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /unsentread <name>").color(NamedTextColor.RED));
            return true;
        }

        String name = args[0];
        List<MessageStore.UnsentMessage> messages = plugin.getMessageStore().load(name);

        if (messages.isEmpty()) {
            sender.sendMessage(Component.text("No unsent messages found for ").color(NamedTextColor.GRAY)
                    .append(Component.text(name).color(NamedTextColor.WHITE))
                    .append(Component.text(".").color(NamedTextColor.GRAY)));
            return true;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(
            Component.text("── Unsent messages to ")
                     .color(NamedTextColor.DARK_GRAY)
                .append(Component.text(name).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ──").color(NamedTextColor.DARK_GRAY))
        );

        for (int i = 0; i < messages.size(); i++) {
            MessageStore.UnsentMessage msg = messages.get(i);
            String date = msg.timestamp > 0
                    ? DATE_FMT.format(Instant.ofEpochMilli(msg.timestamp))
                    : "unknown date";

            sender.sendMessage(
                Component.text((i + 1) + ". ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text("\"" + msg.text + "\"").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, true))
                    .append(Component.text("  — " + date).color(NamedTextColor.DARK_GRAY))
            );
        }

        sender.sendMessage(
            Component.text("(" + messages.size() + " message" + (messages.size() == 1 ? "" : "s") + ")")
                     .color(NamedTextColor.DARK_GRAY)
        );
        sender.sendMessage(Component.empty());
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
