package com.unsentplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // Default note: white background, font size from config.
        int fontSize = plugin.getConfig().getInt("map.font-size", 8);
        send(player, name, message, Color.WHITE, fontSize);
        return true;
    }

    /**
     * Full send pipeline — validates the name/message, enforces the volume/cooldown limits, runs
     * remote moderation, then creates the note with the given background and font size. Shared by
     * {@code /unsent} (white, config size) and {@link VipCommand} (custom colour + size).
     */
    public void send(Player player, String name, String message, Color background, int fontSize) {
        WordFilter filter = plugin.getWordFilter();

        if (!filter.isValidName(name)) {
            player.sendMessage(Component.text("That name isn't valid. Use letters, numbers, or underscores (max 16 chars).").color(NamedTextColor.RED));
            return;
        }
        if (filter.isBlocked(name)) {
            player.sendMessage(Component.text("That name contains blocked content.").color(NamedTextColor.RED));
            return;
        }

        int maxLen = plugin.getConfig().getInt("max-message-length", 80);
        if (message.length() > maxLen) {
            player.sendMessage(Component.text("Your message is too long (max " + maxLen + " characters).").color(NamedTextColor.RED));
            return;
        }
        if (filter.isBlocked(message)) {
            player.sendMessage(Component.text("Your message contains content that isn't allowed. Please keep it kind.").color(NamedTextColor.RED));
            return;
        }

        // Notes must be unique — no duplicate of the same message to the same name.
        if (plugin.getMessageStore().isDuplicate(name, message)) {
            notifyDuplicate(player, name);
            return;
        }

        // Volume control. unsent.unlimited (OPs by default) bypasses it. When note-credits is on it
        // gates by available credits; otherwise it falls back to the flat per-player message limit.
        boolean unlimited = player.hasPermission("unsent.unlimited");
        boolean creditsEnabled = plugin.getConfig().getBoolean("note-credits.enabled", true);
        if (!unlimited) {
            if (creditsEnabled) {
                if (plugin.getPlayerStore().getCredits(player.getUniqueId()) <= 0) {
                    long ms = plugin.getPlayerStore().millisToNextCredit(player.getUniqueId());
                    player.sendMessage(Component.text("You're out of notes — the next one is ready in "
                            + formatDuration(ms) + ".").color(NamedTextColor.YELLOW));
                    return;
                }
            } else {
                int messageLimit = plugin.getConfig().getInt("max-messages-per-player",
                        plugin.getConfig().getInt("max-maps-per-player", 10));
                if (messageLimit > 0 && plugin.getPlayerStore().getMessageCount(player.getUniqueId()) >= messageLimit) {
                    player.sendMessage(Component.text("You've reached your limit of " + messageLimit + " messages.").color(NamedTextColor.YELLOW));
                    return;
                }
            }
        }

        // Rate limit: cooldown between creations (unlimited players are exempt).
        if (!unlimited) {
            int cooldownSeconds = plugin.getConfig().getInt("creation-cooldown-seconds", 30);
            if (cooldownSeconds > 0) {
                long last = lastCreation.getOrDefault(player.getUniqueId(), 0L);
                long remainingMs = cooldownSeconds * 1000L - (System.currentTimeMillis() - last);
                if (remainingMs > 0) {
                    player.sendMessage(Component.text("Please wait " + formatDuration(remainingMs)
                            + " before making another map.").color(NamedTextColor.YELLOW));
                    return;
                }
            }
        }

        // Remote checks (username validation + AI moderation) are blocking HTTP, so run them off the
        // main thread, then finish back on it.
        UsernameValidator validator = plugin.getUsernameValidator();
        AiModerator ai = plugin.getAiModerator();
        if (validator.isEnabled() || ai.isEnabled()) {
            UUID uuid = player.getUniqueId();
            if (!pendingChecks.add(uuid)) {
                player.sendMessage(Component.text("Hold on — your last message is still being checked.").color(NamedTextColor.YELLOW));
                return;
            }
            player.sendMessage(Component.text("Checking your message…").color(NamedTextColor.GRAY));

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String rejection = runRemoteChecks(validator, ai, name, message);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingChecks.remove(uuid);
                    if (!player.isOnline()) return;
                    if (rejection != null) {
                        player.sendMessage(Component.text(rejection).color(NamedTextColor.RED));
                    } else {
                        createNote(player, name, message, background, fontSize);
                    }
                });
            });
            return;
        }

        createNote(player, name, message, background, fontSize);
    }

    /**
     * Runs the enabled remote checks (username validation, then AI moderation) on the calling
     * thread. Returns a player-facing rejection message, or {@code null} if the message may proceed.
     */
    private String runRemoteChecks(UsernameValidator validator, AiModerator ai, String name, String message) {
        if (validator.isEnabled()) {
            UsernameValidator.Result result = validator.validate(name);
            if (result == UsernameValidator.Result.NOT_FOUND) {
                return "There's no Minecraft player named \"" + name + "\".";
            }
            if (result == UsernameValidator.Result.ERROR && validator.isFailClosed()) {
                return "Couldn't verify that username right now — please try again in a moment.";
            }
        }
        if (ai.isEnabled()) {
            AiModerator.Result result = ai.check(message);
            if (result == AiModerator.Result.UNSAFE) {
                return "Your message looks unsafe for this server. Please keep it kind.";
            }
            if (result == AiModerator.Result.ERROR && ai.isFailClosed()) {
                return "Couldn't verify your message right now — please try again in a moment.";
            }
        }
        return null;
    }

    /** Saves the message, builds the map with the given background/font size, gives it, and accounts for it. */
    private void createNote(Player player, String name, String message, Color background, int fontSize) {
        // Re-check uniqueness here too — this is the authoritative point, after any async gap.
        if (plugin.getMessageStore().isDuplicate(name, message)) {
            notifyDuplicate(player, name);
            return;
        }

        // Stamp once so the stored value and the value rendered on the map match exactly.
        long now = System.currentTimeMillis();

        boolean saved = plugin.getMessageStore().save(name, message, now);
        if (!saved) {
            player.sendMessage(Component.text("There are already too many messages for that name.").color(NamedTextColor.YELLOW));
            return;
        }

        ItemStack map = MapFactory.createMap(plugin, player.getWorld(), name, message, now, background, fontSize);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), map);
            player.sendMessage(Component.text("Your inventory was full — your map dropped at your feet.").color(NamedTextColor.YELLOW));
        } else {
            player.getInventory().addItem(map);
        }

        // Account for the note: spend a credit (and refresh the voucher) or count it toward the
        // flat limit. Unlimited players are exempt.
        boolean creditsEnabled = plugin.getConfig().getBoolean("note-credits.enabled", true);
        boolean unlimited = player.hasPermission("unsent.unlimited");
        if (!unlimited) {
            if (creditsEnabled) {
                plugin.getPlayerStore().spendCredit(player.getUniqueId());
                NoteVoucher.sync(plugin, player);
            } else {
                plugin.getPlayerStore().incrementMessageCount(player.getUniqueId());
            }
        }

        // Start the creation cooldown from this successful map.
        lastCreation.put(player.getUniqueId(), System.currentTimeMillis());

        player.sendMessage(
            Component.text("Your message to ")
                     .color(NamedTextColor.GRAY)
                .append(Component.text(name).color(NamedTextColor.WHITE))
                .append(Component.text(" has been sealed into a map.").color(NamedTextColor.GRAY))
        );

        if (creditsEnabled && !unlimited) {
            int left = plugin.getPlayerStore().getCredits(player.getUniqueId());
            player.sendMessage(Component.text("Notes left: ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(left).color(NamedTextColor.GRAY)));
        }
    }

    /** Formats a duration as days/hours/minutes/seconds, e.g. "6d 23h 59m 12s" or "45s". */
    private static String formatDuration(long ms) {
        long totalSeconds = (ms + 999) / 1000;
        long days    = totalSeconds / 86_400;
        long hours   = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)                            sb.append(days).append("d ");
        if (days > 0 || hours > 0)               sb.append(hours).append("h ");
        if (days > 0 || hours > 0 || minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString();
    }

    private void notifyDuplicate(Player player, String name) {
        player.sendMessage(Component.text("That exact note already exists for ").color(NamedTextColor.YELLOW)
                .append(Component.text(name).color(NamedTextColor.WHITE))
                .append(Component.text(" — make it unique!").color(NamedTextColor.YELLOW)));
    }

    /** Recipient-name suggestions (online + previously-joined players, plus names with messages). */
    static List<String> suggestNames(UnsentPlugin plugin, String typed) {
        String lower = typed.toLowerCase();
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String playerName = offline.getName();
            if (playerName != null && playerName.toLowerCase().startsWith(lower)) {
                names.add(playerName);
            }
        }
        for (String storedName : plugin.getMessageStore().allNames()) {
            if (storedName.toLowerCase().startsWith(lower)) names.add(storedName);
        }
        return names.stream().limit(100).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.contains(":")) return Collections.emptyList();
        if (args.length == 1) {
            return suggestNames(plugin, args[0]);
        }
        return Collections.emptyList();
    }
}
