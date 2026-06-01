package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UnsentCommand implements CommandExecutor, TabCompleter {

    private final UnsentPlugin plugin;

    /** Players with an AI moderation check currently in flight — blocks spamming the API. */
    private final Set<UUID> pendingChecks = ConcurrentHashMap.newKeySet();

    /** Epoch-millis of each player's last successful map creation, for the cooldown. */
    private final Map<UUID, Long> lastCreation = new ConcurrentHashMap<>();

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

        // Enforce the per-player message limit. Players with unsent.unlimited (OPs by default) bypass it.
        boolean unlimited = player.hasPermission("unsent.unlimited");
        // Read the new key, falling back to the old `max-maps-per-player` name, then a default of 10.
        int messageLimit = plugin.getConfig().getInt("max-messages-per-player",
                plugin.getConfig().getInt("max-maps-per-player", 10));
        if (!unlimited && messageLimit > 0 && plugin.getPlayerStore().getMessageCount(player.getUniqueId()) >= messageLimit) {
            player.sendMessage(Component.text("You've reached your limit of " + messageLimit + " messages.").color(NamedTextColor.YELLOW));
            return true;
        }

        // Rate limit: enforce a cooldown between creations (unlimited players are exempt).
        if (!unlimited) {
            int cooldownSeconds = plugin.getConfig().getInt("creation-cooldown-seconds", 30);
            if (cooldownSeconds > 0) {
                long last = lastCreation.getOrDefault(player.getUniqueId(), 0L);
                long remainingMs = cooldownSeconds * 1000L - (System.currentTimeMillis() - last);
                if (remainingMs > 0) {
                    long remainingSec = (remainingMs + 999) / 1000; // round up
                    player.sendMessage(Component.text("Please wait " + remainingSec
                            + "s before making another map.").color(NamedTextColor.YELLOW));
                    return true;
                }
            }
        }

        // Second moderation layer: ask OpenAI whether the message is safe (catches filter bypasses).
        // The network call is blocking, so run it off the main thread and finish back on it.
        AiModerator ai = plugin.getAiModerator();
        if (ai.isEnabled()) {
            UUID uuid = player.getUniqueId();
            if (!pendingChecks.add(uuid)) {
                player.sendMessage(Component.text("Hold on — your last message is still being checked.").color(NamedTextColor.YELLOW));
                return true;
            }
            player.sendMessage(Component.text("Checking your message…").color(NamedTextColor.GRAY));

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                AiModerator.Result result = ai.check(message);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingChecks.remove(uuid);
                    if (!player.isOnline()) return;
                    switch (result) {
                        case SAFE -> finishUnsent(player, name, message);
                        case UNSAFE -> player.sendMessage(Component.text(
                                "Your message looks unsafe for this server. Please keep it kind.").color(NamedTextColor.RED));
                        case ERROR -> {
                            if (ai.isFailClosed()) {
                                player.sendMessage(Component.text(
                                        "Couldn't verify your message right now — please try again in a moment.").color(NamedTextColor.YELLOW));
                            } else {
                                finishUnsent(player, name, message);
                            }
                        }
                    }
                });
            });
            return true;
        }

        // AI moderation off — commit immediately.
        finishUnsent(player, name, message);
        return true;
    }

    /** Saves the message, builds the map, gives it to the player, and counts it against their limit. */
    private void finishUnsent(Player player, String name, String message) {
        // Stamp once so the stored value and the value rendered on the map match exactly.
        long now = System.currentTimeMillis();

        boolean saved = plugin.getMessageStore().save(name, message, now);
        if (!saved) {
            player.sendMessage(Component.text("There are already too many messages for that name.").color(NamedTextColor.YELLOW));
            return;
        }

        ItemStack map = MapFactory.createMap(plugin, player.getWorld(), name, message, now);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), map);
            player.sendMessage(Component.text("Your inventory was full — your map dropped at your feet.").color(NamedTextColor.YELLOW));
        } else {
            player.getInventory().addItem(map);
        }

        // Count this message against the player's limit (skipped for unlimited players).
        if (!player.hasPermission("unsent.unlimited")) {
            plugin.getPlayerStore().incrementMessageCount(player.getUniqueId());
        }

        // Start the creation cooldown from this successful map.
        lastCreation.put(player.getUniqueId(), System.currentTimeMillis());

        player.sendMessage(
            Component.text("Your message to ")
                     .color(NamedTextColor.GRAY)
                .append(Component.text(name).color(NamedTextColor.WHITE))
                .append(Component.text(" has been sealed into a map.").color(NamedTextColor.GRAY))
        );
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
